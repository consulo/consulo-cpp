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
package consulo.cpp.clang;

import consulo.annotation.access.RequiredReadAction;
import consulo.cpp.lang.CPPSourceFileType;
import consulo.cpp.lang.CSourceFileType;
import consulo.cpp.lang.version.CLanguageVersion;
import consulo.cpp.lang.version.CLanguageVersions;
import consulo.language.file.LanguageFileType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.stub.IndexingDataKeys;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.version.LanguageVersion;
import consulo.test.junit.impl.language.SimpleParsingTest;
import consulo.virtualFileSystem.fileType.FileType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Parses clang's tests of code clang accepts without a diagnostic (see {@code clang/SOURCE.txt}): each must parse with
 * no error. The files the parser cannot handle yet are listed in {@code clang/known-failures.txt}; the list only
 * shrinks - a listed file which parses now fails the test too, until it is taken off. With
 * {@code CLANG_TEST_UPDATE_KNOWN} set, the list is rewritten from what fails instead.
 */
public class ClangAcceptedCodeTest extends SimpleParsingTest<Object> {
    private static final Path ROOT = Path.of("src/test/resources/clang");
    private static final Path KNOWN_FAILURES = ROOT.resolve("known-failures.txt");
    private static final String UPDATE_KNOWN = "CLANG_TEST_UPDATE_KNOWN";
    private static final Pattern RUN_STANDARD = Pattern.compile("RUN:.*?-std=([a-z0-9+:]+)");

    public ClangAcceptedCodeTest() {
        super("clang", "cpp");
    }

    @Test
    @RequiredReadAction
    public void testAcceptedCodeParses(Context context) throws Exception {
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(ROOT)) {
            sources = walk.filter(Files::isRegularFile)
                .filter(path -> fileTypeOf(path) != null)
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        }

        Map<String, String> failing = new TreeMap<>();
        for (Path source : sources) {
            String relative = ROOT.relativize(source).toString().replace('\\', '/');
            String text = Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
            String firstError = firstError(context, source, text);
            if (firstError != null) {
                failing.put(relative, firstError);
            }
        }

        String update = System.getenv(UPDATE_KNOWN);
        if (update != null && !update.isEmpty()) {
            Files.writeString(KNOWN_FAILURES, String.join("\n", failing.keySet()) + "\n", StandardCharsets.UTF_8);
            return;
        }

        Set<String> known = readKnownFailures();
        List<String> problems = new ArrayList<>();
        failing.forEach((file, error) -> {
            if (!known.contains(file)) {
                problems.add("fails now: " + file + ":" + error);
            }
        });
        for (String file : known) {
            if (!failing.containsKey(file)) {
                problems.add("parses now, take it off known-failures.txt: " + file);
            }
        }
        if (!problems.isEmpty()) {
            fail(problems.size() + " of " + sources.size() + " files changed (" + failing.size() + " failing, "
                + known.size() + " known):\n" + String.join("\n", problems));
        }
    }

    /**
     * The first error of the file, as {@code line:column message at 'token'}, or {@code null} when it parses.
     */
    @RequiredReadAction
    private @Nullable String firstError(Context context, Path source, String text) {
        LanguageFileType fileType = fileTypeOf(source);
        PsiFile file = createFile(context, standardOf(text), source.getFileName().toString(), fileType, text);
        for (PsiFile root : file.getViewProvider().getAllFiles()) {
            ensureParsed(root);
            for (PsiErrorElement error : PsiTreeUtil.collectElementsOfType(root, PsiErrorElement.class)) {
                return position(text, error.getTextRange().getStartOffset()) + " " + error.getErrorDescriptionValue().get()
                    + " at '" + nearText(error) + "'";
            }
        }
        return null;
    }

    /**
     * The standard a test is compiled under: the newest {@code -std=} of its {@code RUN:} lines - the one with the most
     * of the grammar - or {@code null} for the default of its file type.
     */
    private static @Nullable CLanguageVersion standardOf(String text) {
        CLanguageVersion newest = null;
        Matcher matcher = RUN_STANDARD.matcher(text);
        while (matcher.find()) {
            CLanguageVersion version = CLanguageVersions.findById(versionIdOf(matcher.group(1)));
            if (version != null && (newest == null || version.isCpp() == newest.isCpp() && version.getWeight() > newest.getWeight()
                || version.isCpp() && !newest.isCpp())) {
                newest = version;
            }
        }
        return newest;
    }

    private static @Nullable String versionIdOf(String standard) {
        return switch (standard) {
            case "c++98", "c++03", "gnu++98", "gnu++03" -> "CPP98";
            case "c++11", "c++0x", "gnu++11", "gnu++0x" -> "CPP11";
            case "c++14", "c++1y", "gnu++14", "gnu++1y" -> "CPP14";
            case "c++17", "c++1z", "gnu++17", "gnu++1z" -> "CPP17";
            case "c++20", "c++2a", "gnu++20", "gnu++2a" -> "CPP20";
            case "c++23", "c++2b", "c++26", "c++2c", "gnu++23", "gnu++2b", "gnu++26", "gnu++2c" -> "CPP23";
            case "c89", "c90", "gnu89", "gnu90", "iso9899:1990" -> "C89";
            case "c99", "gnu99", "iso9899:1999" -> "C99";
            case "c11", "gnu11", "iso9899:2011" -> "C11";
            case "c17", "c18", "gnu17", "gnu18", "iso9899:2017" -> "C17";
            case "c23", "c2x", "c2y", "gnu23", "gnu2x", "gnu2y" -> "C23";
            default -> null;
        };
    }

    private static Set<String> readKnownFailures() throws Exception {
        Set<String> known = new TreeSet<>();
        if (!Files.exists(KNOWN_FAILURES)) {
            return known;
        }
        for (String line : Files.readAllLines(KNOWN_FAILURES, StandardCharsets.UTF_8)) {
            String file = line.strip();
            if (!file.isEmpty() && !file.startsWith("#")) {
                known.add(file);
            }
        }
        return known;
    }

    private static String position(String text, int offset) {
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < Math.min(offset, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return line + ":" + (offset - lineStart + 1);
    }

    private static String nearText(PsiErrorElement error) {
        String own = error.getText().strip();
        if (!own.isEmpty()) {
            return shorten(own);
        }
        PsiElement next = PsiTreeUtil.nextLeaf(error);
        while (next != null && (next instanceof PsiWhiteSpace || next.getTextLength() == 0)) {
            next = PsiTreeUtil.nextLeaf(next);
        }
        return next == null ? "<eof>" : shorten(next.getText());
    }

    private static String shorten(String text) {
        String line = text.strip().replace('\n', ' ');
        return line.length() > 30 ? line.substring(0, 30) + "..." : line;
    }

    private static @Nullable LanguageFileType fileTypeOf(Path path) {
        String name = path.getFileName().toString();
        if (name.endsWith(".cpp")) {
            return CPPSourceFileType.INSTANCE;
        }
        if (name.endsWith(".c")) {
            return CSourceFileType.INSTANCE;
        }
        return null;
    }

    @Override
    protected LanguageFileType getFileType(Context context, @Nullable Object testContext) {
        return CPPSourceFileType.INSTANCE;
    }

    /**
     * A light file is never indexed, so it is parsed under no module-aware options: they are attached up front, as the
     * index does for a file it parses, rather than looked up in an index a light application does not have.
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

    @RequiredReadAction
    @Override
    protected LanguageVersion resolveLanguageVersion(Context context, @Nullable Object testContext, FileType fileType) {
        if (testContext instanceof CLanguageVersion standard) {
            return standard;
        }
        return CLanguageVersions.select(fileType, null, null);
    }
}
