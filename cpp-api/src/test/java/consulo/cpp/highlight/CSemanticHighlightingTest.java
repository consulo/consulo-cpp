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
import consulo.cpp.lang.editor.highlight.CCallChecker;
import consulo.cpp.lang.editor.highlight.CSemanticHighlighting;
import consulo.cpp.lang.psi.resolve.CDeclarations;
import consulo.cpp.lang.psi.resolve.CReferenceBase;
import consulo.cpp.preprocessor.psi.impl.CMacroArgumentReference;
import consulo.document.util.TextRange;
import consulo.language.file.LanguageFileType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * The semantic highlighting of a file, and what each name resolves to. For each name of {@code Name.cpp} the expected
 * {@code Name.txt} has a line {@code line:column name KEY}, and for a use {@code -> line:column} of the declaration it
 * resolves to, or {@code -> ?} when it resolves to none in the file; a call no function takes has a line
 * {@code ERROR line:column message}.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public class CSemanticHighlightingTest extends CParsingBaseTest {
    private static final String DATA_PATH = "highlighting";
    private static final String OVERWRITE = "CPP_TEST_OVERWRITE";

    public CSemanticHighlightingTest() {
        super(DATA_PATH, "cpp");
    }

    @Override
    protected LanguageFileType getFileType(Context context, @Nullable Object testContext) {
        return CPPSourceFileType.INSTANCE;
    }

    /**
     * Locals, parameters, globals and enum constants; shadowing by an inner block, a {@code for} variable, the variable
     * of its own initializer.
     */
    @Test
    public void testVariables(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Members through {@code .}, {@code ->} and {@code this->}, from inline and out-of-class member functions, of a base
     * class; qualified names through namespaces reopened, a typedef and a nested class.
     */
    @Test
    public void testMembers(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Template parameters, {@code using namespace}, a using-declaration and an alias, lambda and {@code catch}
     * parameters; members through the types of {@code auto} variables, calls, indexing and dereferencing.
     */
    @Test
    public void testLanguage(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * The overload a call means by the types of its arguments - exact, promoted, converted, a pointer to a derived class,
     * a null pointer, defaults and {@code ...}, a function preferred to a template - argument-dependent lookup, a
     * template class's arguments substituted, {@code struct stat} apart from {@code stat()}; calls no function takes.
     */
    @Test
    public void testOverloads(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * The names the arguments of a macro call pass - {@code calc::add} of {@code EXPECT_EQ(calc::add(local, 2), 3)} - and a
     * declaration a macro makes from an argument, used afterwards.
     */
    @Test
    public void testMacros(Context context) throws Exception {
        doTest(context, null);
    }

    @RequiredReadAction
    @Override
    protected void checkResult(Context context, @Nullable Object testContext, PsiFile file) throws Exception {
        PsiFile cRoot = file.getViewProvider().getPsi(CLanguage.INSTANCE);
        String text = file.getText();

        Map<Integer, String> lines = new TreeMap<>();
        collect(cRoot, text, lines);
        String actual = String.join("\n", lines.values());

        String name = getFileName(context, testContext);
        String overwrite = System.getenv(OVERWRITE);
        if (overwrite != null && !overwrite.isEmpty()) {
            Files.writeString(Path.of("src/test/resources", DATA_PATH, name + ".txt"), actual, StandardCharsets.UTF_8);
            return;
        }
        Assertions.assertEquals(loadText(context, testContext, "txt").trim(), actual.trim());
    }

    @RequiredReadAction
    private static void collect(PsiElement element, String text, Map<Integer, String> lines) {
        // keyed by offset twice: a name at even keys, an error after it at the odd one
        CCallChecker.check(element, (range, message) ->
            lines.merge(range.getStartOffset() * 2 + 1, "ERROR " + position(text, range) + " " + message.get(), (a, b) -> a + "\n" + b));
        CSemanticHighlighting.highlight(element, (range, key) ->
            lines.merge(range.getStartOffset() * 2, position(text, range) + " " + range.substring(text) + " " + key.getExternalName(), CSemanticHighlightingTest::join));

        for (PsiReference reference : element.getReferences()) {
            if (!(reference instanceof CReferenceBase<?>) && !(reference instanceof CMacroArgumentReference)) {
                continue;
            }
            TextRange range = reference.getRangeInElement().shiftRight(element.getTextRange().getStartOffset());
            if (range.isEmpty()) {
                continue;
            }
            PsiElement target = reference.resolve();
            PsiElement targetName = target == null ? null : CDeclarations.nameIdentifier(target);
            String resolved = targetName == null ? "-> ?"
                : "-> " + position(text, new TextRange(target.getTextOffset(), target.getTextOffset()));
            lines.merge(range.getStartOffset() * 2, position(text, range) + " " + range.substring(text) + " " + resolved, CSemanticHighlightingTest::join);
        }

        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            collect(child, text, lines);
        }
    }

    /**
     * The color and the target of one name, on one line: {@code 3:5 x LOCAL_VARIABLE -> 2:9}.
     */
    private static String join(String first, String second) {
        String[] firstParts = first.split(" ", 3);
        String[] secondParts = second.split(" ", 3);
        String color = firstParts[2].startsWith("->") ? secondParts[2] : firstParts[2];
        String target = firstParts[2].startsWith("->") ? firstParts[2] : secondParts[2];
        return firstParts[0] + " " + firstParts[1] + " " + color + " " + target;
    }

    private static String position(String text, TextRange range) {
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < range.getStartOffset(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return line + ":" + (range.getStartOffset() - lineStart + 1);
    }
}
