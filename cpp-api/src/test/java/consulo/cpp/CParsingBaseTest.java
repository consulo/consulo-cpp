package consulo.cpp;

import consulo.annotation.access.RequiredReadAction;
import consulo.cpp.lang.version.CLanguageVersions;
import consulo.language.Language;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.PsiFile;
import consulo.language.psi.stub.IndexingDataKeys;
import consulo.language.version.LanguageVersion;
import consulo.test.junit.impl.language.SimpleParsingTest;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Base class for C/C++ parsing tests.
 * Concrete subclasses provide the file type and data path.
 * <p>
 * With {@code CPP_TEST_OVERWRITE} set, the expected trees are rewritten from what the parser produces instead of
 * checked - for when the tree shape changes on purpose. Review the diff of the rewritten files.
 */
public abstract class CParsingBaseTest extends SimpleParsingTest<Object> {
    private static final String OVERWRITE = "CPP_TEST_OVERWRITE";

    private final String myDataPath;

    public CParsingBaseTest(String dataPath, String extension) {
        super(dataPath, extension);
        myDataPath = dataPath;
    }

    /**
     * A light file is never indexed, so it is parsed under no module-aware options: they are attached up front, as
     * the index does for a file it parses, rather than looked up in an index a light application does not have.
     * Not marked as a copy - marking walks the tree, which would parse it before the options are attached.
     */
    @RequiredReadAction
    @Override
    protected PsiFile createFile(Context context, @Nullable Object testContext, String fileName, FileType fileType, String text) {
        LanguageVersion languageVersion = resolveLanguageVersion(context, testContext, fileType);
        PsiFile file = context.psiFileFactory().createFileFromText(fileName, languageVersion, text, true, false, false);
        for (PsiFile root : file.getViewProvider().getAllFiles()) {
            root.putUserData(IndexingDataKeys.INDEX_OPTIONS, Map.of());
        }
        return file;
    }

    /**
     * Only check the primary C/C++ PSI root, not the preprocessor root.
     */
    @Override
    protected boolean checkAllPsiRoots() {
        return false;
    }

    @RequiredReadAction
    @Override
    protected LanguageVersion resolveLanguageVersion(Context context, @Nullable Object testContext, FileType fileType) {
        return CLanguageVersions.select(fileType, null, null);
    }

    @Override
    protected void checkResult(Context context, @Nullable Object testContext, PsiFile file) throws Exception {
        String overwrite = System.getenv(OVERWRITE);
        if (overwrite == null || overwrite.isEmpty()) {
            super.checkResult(context, testContext, file);
            return;
        }

        String name = getFileName(context, testContext);
        FileViewProvider viewProvider = file.getViewProvider();
        Set<Language> languages = viewProvider.getLanguages();
        if (!checkAllPsiRoots() || languages.size() == 1) {
            writeExpected(name + ".txt", toParseTreeText(file, skipSpaces(), includeRanges()));
            return;
        }
        for (Language language : languages) {
            writeExpected(name + "." + language.getID() + ".txt", toParseTreeText(viewProvider.getPsi(language), skipSpaces(), includeRanges()));
        }
    }

    private void writeExpected(String fileName, String tree) throws Exception {
        Path expected = Path.of("src/test/resources", myDataPath, fileName);
        Files.writeString(expected, tree.trim(), StandardCharsets.UTF_8);
    }
}
