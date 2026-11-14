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

package consulo.cpp.parsing;

import consulo.cpp.CParsingBaseTest;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiElement;
import consulo.document.util.TextRange;
import consulo.cpp.lang.CLanguage;
import consulo.annotation.access.RequiredReadAction;
import consulo.cpp.lang.CSourceFileType;
import consulo.language.file.LanguageFileType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for preprocessor macro expansion in C source files.
 *
 * <p>Each test verifies that macros defined with {@code #define} are correctly expanded
 * when referenced in C code. Fixture files are stored under
 * {@code src/test/resources/parsing/expand/} with per-language expected trees:
 * <ul>
 * <li>{@code TestName.c}             – C source input</li>
 * <li>{@code TestName.C.txt}         – expected C primary root (after expansion)</li>
 * <li>{@code TestName.CPreprocessor.txt} – expected preprocessor root</li>
 * </ul>
 */
public class CExpandTest extends CParsingBaseTest {
    public CExpandTest() {
        super("parsing/expand", "c");
    }

    @Nonnull
    @Override
    protected LanguageFileType getFileType(@Nonnull Context context, @Nullable Object testContext) {
        return CSourceFileType.INSTANCE;
    }

    @Override
    protected boolean checkAllPsiRoots() {
        return true;
    }

    /**
     * Qt's Q_DECLARE_STRONGLY_ORDERED_LITERAL_TYPE: an overloaded macro whose helpers take empty, commented arguments,
     * one with its parameters continued over lines
     */
    @Test
    public void testQtOrderingMacro(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Qt's QT_OVERLOADED_MACRO: the count of the arguments pasted onto the name of the macro called with them
     */
    @Test
    public void testOverloadedMacro(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code #define MY_INT int} used in a global variable declaration
     */
    @Test
    public void testTypedefExpand(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code #define VOID void} used in a function declaration
     */
    @Test
    public void testFunctionReturnTypeExpand(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code #define FOO 1\n#undef FOO\n#define FOO 2} – undef then redefine
     */
    @Test
    public void testUndefAndRedefine(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code #include "defs.h"} where the file cannot be resolved (light-file context).
     * Verifies the directive is cleanly stripped from the C view without crashing.
     */
    @Test
    public void testIncludeNotFound(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code #include <stdio.h>} where no include paths are configured (light-file context).
     * Verifies the directive is cleanly stripped from the C view without crashing.
     */
    @Test
    public void testSystemIncludeNotFound(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * A function-like macro called with arguments
     */
    @Test
    public void testFunctionLikeMacro(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * A macro expanding to nothing in front of a declaration
     */
    @Test
    public void testEmptyMacro(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code #define UINT unsigned int} - a body of several tokens
     */
    @Test
    public void testMultiTokenMacro(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * glibc's {@code FD_ZERO}: a function-like macro continued over several lines - tabs before each backslash -
     * expanding to {@code do { ... } while (0)} through another function-like macro
     */
    @Test
    public void testMultiLineMacro(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Directives written with blanks after the {@code #} - {@code # define} - as system headers nest them
     */
    @Test
    public void testSpacedDirectives(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * A macro expanding to another macro
     */
    @Test
    public void testNestedMacro(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code #} and {@code ##} in function-like macros
     */
    @Test
    public void testStringizeAndPaste(Context context) throws Exception {
        doTest(context, null);
    }
    /**
     * A directive after whitespace - the blank line after a comment, the indent before a nested {@code #define}: every
     * leaf of the C tree sits where its text is, so the offsets of declarations after it are those of the file
     */
    @Test
    @RequiredReadAction
    public void testDirectiveOffsets(Context context) throws Exception {
        PsiFile file = loadPsiFile(context, null);
        PsiFile cRoot = file.getViewProvider().getPsi(CLanguage.INSTANCE);
        String source = file.getViewProvider().getContents().toString();
        for (PsiElement leaf = PsiTreeUtil.firstChild(cRoot); leaf != null; leaf = PsiTreeUtil.nextLeaf(leaf)) {
            TextRange range = leaf.getTextRange();
            assertEquals(range.substring(source), leaf.getText(), "leaf " + leaf.getNode().getElementType() + " at " + range);
        }
    }
}
