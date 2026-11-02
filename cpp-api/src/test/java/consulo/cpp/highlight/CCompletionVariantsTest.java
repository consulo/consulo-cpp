/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.cpp.highlight;

import consulo.annotation.access.RequiredReadAction;
import consulo.cpp.CParsingBaseTest;
import consulo.cpp.lang.CLanguage;
import consulo.cpp.lang.CPPSourceFileType;
import consulo.cpp.lang.psi.CPsiTokens;
import consulo.cpp.lang.psi.resolve.CCompletionVariants;
import consulo.cpp.lang.psi.resolve.CDeclarations;
import consulo.language.file.LanguageFileType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What completion offers where {@code Name.cpp} has the identifier {@code ZZ}: {@code Name.txt} has a line
 * {@code line:column name, name, ...} for each, the names sorted.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public class CCompletionVariantsTest extends CParsingBaseTest {
    private static final String DATA_PATH = "completion";
    private static final String MARKER = "ZZ";

    public CCompletionVariantsTest() {
        super(DATA_PATH, "cpp");
    }

    @Override
    protected LanguageFileType getFileType(Context context, @Nullable Object testContext) {
        return CPPSourceFileType.INSTANCE;
    }

    /**
     * Locals with the innermost of a name, parameters, globals; members after {@code .}, {@code ->} and {@code ::}; types
     * and namespaces in a type.
     */
    @Test
    public void testScopes(Context context) throws Exception {
        doTest(context, null);
    }

    @RequiredReadAction
    @Override
    protected void checkResult(Context context, @Nullable Object testContext, PsiFile file) throws Exception {
        PsiFile cRoot = file.getViewProvider().getPsi(CLanguage.INSTANCE);
        String text = file.getText();
        List<String> lines = new ArrayList<>();
        for (PsiElement leaf = PsiTreeUtil.firstChild(cRoot); leaf != null; leaf = PsiTreeUtil.nextLeaf(leaf)) {
            if (leaf.getNode().getElementType() != CPsiTokens.IDENTIFIER || !MARKER.equals(leaf.getText())) {
                continue;
            }
            List<String> names = new ArrayList<>();
            for (PsiElement variant : CCompletionVariants.collect(leaf)) {
                String name = CDeclarations.nameOf(variant);
                if (name != null && !name.equals(MARKER)) {
                    names.add(name);
                }
            }
            names.sort(null);
            lines.add(position(text, leaf.getTextRange().getStartOffset()) + " " + String.join(", ", names));
        }
        String actual = String.join("\n", lines);

        String overwrite = System.getenv("CPP_TEST_OVERWRITE");
        if (overwrite != null && !overwrite.isEmpty()) {
            Files.writeString(Path.of("src/test/resources", DATA_PATH, getFileName(context, testContext) + ".txt"), actual, StandardCharsets.UTF_8);
            return;
        }
        Assertions.assertEquals(loadText(context, testContext, "txt").trim(), actual.trim());
    }

    private static String position(String text, int offset) {
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return line + ":" + (offset - lineStart + 1);
    }
}
