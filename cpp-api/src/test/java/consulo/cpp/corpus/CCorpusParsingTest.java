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
package consulo.cpp.corpus;

import consulo.annotation.access.RequiredReadAction;
import consulo.cpp.CParsingBaseTest;
import consulo.cpp.lang.CHeaderFileType;
import consulo.cpp.lang.CPPHeaderFileType;
import consulo.cpp.lang.CPPSourceFileType;
import consulo.cpp.lang.CSourceFileType;
import consulo.language.file.LanguageFileType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Parses every C/C++ source of a real project and reports what the parser cannot handle. It does nothing unless
 * {@code CPP_CORPUS_DIR} names the source tree; the report goes to {@code CPP_CORPUS_REPORT}, by default
 * {@code target/cpp-corpus-report.txt}, and with {@code CPP_CORPUS_DUMP} set the tree of every file is written there.
 * Nothing is asserted - the report is the result.
 */
public class CCorpusParsingTest extends CParsingBaseTest {
    private static final String CORPUS_DIR = "CPP_CORPUS_DIR";
    private static final String CORPUS_REPORT = "CPP_CORPUS_REPORT";
    private static final String CORPUS_DUMP = "CPP_CORPUS_DUMP";

    private static final Map<String, LanguageFileType> FILE_TYPES = Map.ofEntries(
        Map.entry("c", CSourceFileType.INSTANCE),
        Map.entry("h", CHeaderFileType.INSTANCE),
        Map.entry("cpp", CPPSourceFileType.INSTANCE),
        Map.entry("cc", CPPSourceFileType.INSTANCE),
        Map.entry("cxx", CPPSourceFileType.INSTANCE),
        Map.entry("c++", CPPSourceFileType.INSTANCE),
        Map.entry("hpp", CPPHeaderFileType.INSTANCE),
        Map.entry("hh", CPPHeaderFileType.INSTANCE),
        Map.entry("hxx", CPPHeaderFileType.INSTANCE),
        Map.entry("h++", CPPHeaderFileType.INSTANCE),
        Map.entry("inl", CPPHeaderFileType.INSTANCE),
        Map.entry("ipp", CPPHeaderFileType.INSTANCE)
    );

    private record Problem(String file, int line, int column, String root, String message, String near, String sourceLine) {
    }

    public CCorpusParsingTest() {
        super("", "");
    }

    @Override
    protected LanguageFileType getFileType(Context context, @Nullable Object testContext) {
        return CPPSourceFileType.INSTANCE;
    }

    @Test
    @RequiredReadAction
    public void testCorpus(Context context) throws Exception {
        String corpusDir = System.getenv(CORPUS_DIR);
        Assumptions.assumeTrue(corpusDir != null && !corpusDir.isEmpty(), CORPUS_DIR + " is not set");

        // several trees may be given, separated as in a path; with more than one, a file is named after its tree too
        List<Path> roots = new ArrayList<>();
        for (String each : corpusDir.split(File.pathSeparator)) {
            if (!each.isBlank()) {
                roots.add(Path.of(each));
            }
        }
        Map<Path, String> sources = new LinkedHashMap<>();
        for (Path tree : roots) {
            String prefix = roots.size() == 1 ? "" : tree.getFileName() + "/";
            try (Stream<Path> walk = Files.walk(tree)) {
                walk.filter(Files::isRegularFile)
                    .filter(path -> fileTypeOf(path) != null)
                    .filter(path -> !tree.relativize(path).toString().startsWith("build"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> sources.put(path, prefix + tree.relativize(path)));
            }
        }

        String reportPath = System.getenv(CORPUS_REPORT);
        Path reportFile = Path.of(reportPath == null || reportPath.isEmpty() ? "target/cpp-corpus-report.txt" : reportPath);
        Files.createDirectories(reportFile.toAbsolutePath().getParent());
        // names the file being parsed, so a parse that never finishes shows where it hangs
        Path progressFile = Path.of(reportFile + ".progress");

        List<Problem> problems = new ArrayList<>();
        Map<String, String> crashes = new LinkedHashMap<>();
        List<String> textMismatches = new ArrayList<>();
        int lines = 0;

        for (Map.Entry<Path, String> entry : sources.entrySet()) {
            Path source = entry.getKey();
            String relative = entry.getValue();
            String text = Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
            lines += text.split("\n", -1).length;
            Files.writeString(progressFile, relative, StandardCharsets.UTF_8);
            try {
                PsiFile file = createFile(context, null, source.getFileName().toString(), fileTypeOf(source), text);
                ensureParsed(file);

                String dumpDir = System.getenv(CORPUS_DUMP);
                if (dumpDir != null && !dumpDir.isEmpty()) {
                    Path dump = Path.of(dumpDir, relative + ".tree.txt");
                    Files.createDirectories(dump.toAbsolutePath().getParent());
                    Files.writeString(dump, toParseTreeText(file, true, false), StandardCharsets.UTF_8);
                }

                for (PsiFile psiRoot : file.getViewProvider().getAllFiles()) {
                    ensureParsed(psiRoot);
                    if (!text.contentEquals(psiRoot.getText())) {
                        textMismatches.add(relative + " [" + psiRoot.getLanguage().getID() + "]");
                    }
                    for (PsiErrorElement error : PsiTreeUtil.collectElementsOfType(psiRoot, PsiErrorElement.class)) {
                        problems.add(problemOf(relative, text, psiRoot, error));
                    }
                }
            }
            catch (Throwable e) {
                crashes.put(relative, e.toString());
            }
        }

        Files.deleteIfExists(progressFile);
        Files.writeString(reportFile, report(corpusDir, sources.size(), lines, problems, crashes, textMismatches), StandardCharsets.UTF_8);
    }

    private static @Nullable LanguageFileType fileTypeOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? null : FILE_TYPES.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static Problem problemOf(String file, String text, PsiFile root, PsiErrorElement error) {
        int offset = Math.min(error.getTextRange().getStartOffset(), text.length());
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        int lineEnd = text.indexOf('\n', offset);
        String sourceLine = text.substring(lineStart, lineEnd < 0 ? text.length() : lineEnd).strip();
        return new Problem(file, line, offset - lineStart + 1, root.getLanguage().getID(), error.getErrorDescriptionValue().get(),
            nearText(error), sourceLine);
    }

    /**
     * The token an error stands at: its own text, or the next significant leaf when the error is empty.
     */
    private static String nearText(PsiErrorElement error) {
        String own = error.getText();
        if (!own.isBlank()) {
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

    private static String report(String root,
                                 int fileCount,
                                 int lineCount,
                                 List<Problem> problems,
                                 Map<String, String> crashes,
                                 List<String> textMismatches) {
        StringBuilder out = new StringBuilder();
        out.append("Corpus: ").append(root).append('\n');
        out.append("Files: ").append(fileCount).append(", lines: ").append(lineCount).append('\n');
        Map<String, Integer> byMessage = new TreeMap<>();
        Map<String, Integer> byMessageAndToken = new TreeMap<>();
        Map<String, Integer> byFile = new TreeMap<>();
        for (Problem problem : problems) {
            byMessage.merge(problem.root() + " | " + problem.message(), 1, Integer::sum);
            byMessageAndToken.merge(problem.root() + " | " + problem.message() + " | at '" + problem.near() + "'", 1, Integer::sum);
            byFile.merge(problem.file(), 1, Integer::sum);
        }

        out.append("Errors: ").append(problems.size())
            .append(", crashes: ").append(crashes.size())
            .append(", text mismatches: ").append(textMismatches.size()).append('\n');
        // a corpus of code known to compile - clang's tests marked expected-no-diagnostics - should be all clean
        out.append("Clean files: ").append(fileCount - byFile.size()).append(" of ").append(fileCount).append("\n\n");

        appendCounts(out, "By message", byMessage);
        appendCounts(out, "By message and token", byMessageAndToken);
        appendCounts(out, "By file", byFile);

        out.append("== Crashes\n");
        crashes.forEach((file, crash) -> out.append(file).append(": ").append(crash).append('\n'));
        out.append("\n== Text mismatches\n");
        textMismatches.forEach(file -> out.append(file).append('\n'));

        out.append("\n== Errors\n");
        for (Problem problem : problems) {
            out.append(problem.file()).append(':').append(problem.line()).append(':').append(problem.column())
                .append(" [").append(problem.root()).append("] ").append(problem.message())
                .append(" at '").append(problem.near()).append("'\n    ").append(problem.sourceLine()).append('\n');
        }
        return out.toString();
    }

    private static void appendCounts(StringBuilder out, String title, Map<String, Integer> counts) {
        out.append("== ").append(title).append('\n');
        counts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> out.append(String.format("%5d  %s%n", entry.getValue(), entry.getKey())));
        out.append('\n');
    }
}
