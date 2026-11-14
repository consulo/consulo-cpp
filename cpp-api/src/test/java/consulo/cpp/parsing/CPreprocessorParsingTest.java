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
import consulo.cpp.lang.CSourceFileType;
import consulo.language.file.LanguageFileType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Parsing tests for the C preprocessor language.
 *
 * <p>Each test method corresponds to a pair of resource files under
 * {@code src/test/resources/parsing/preprocessor/}:
 * <ul>
 * <li>{@code MethodName.c}   – C source input with preprocessor directives</li>
 * <li>{@code MethodName.txt} – expected PSI tree dump (all roots)</li>
 * </ul>
 */
public class CPreprocessorParsingTest extends CParsingBaseTest {
    public CPreprocessorParsingTest() {
        super("parsing/preprocessor", "c");
    }

    @Nonnull
    @Override
    protected LanguageFileType getFileType(@Nonnull Context context, @Nullable Object testContext) {
        return CSourceFileType.INSTANCE;
    }

    /**
     * {@code checkAllPsiRoots} is overridden so that the preprocessor PSI root
     * is also verified alongside the primary C root.
     */
    @Override
    protected boolean checkAllPsiRoots() {
        return true;
    }

    /**
     * An {@code #if} continued over lines with backslashes, as gcc's {@code stddef.h} opens
     */
    @Test
    public void testContinuedCondition(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code #define FOO 42} – simple macro definition
     */
    @Test
    public void testDefine(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code #undef FOO} – macro undefinition
     */
    @Test
    public void testUndef(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code #pragma once} – pragma directive
     */
    @Test
    public void testPragma(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code #ifdef FOO ... #endif} – ifdef / endif block
     */
    @Test
    public void testIfdef(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code #define FOO} then {@code #ifdef FOO ... #else ... #endif} – the defined branch is the one that
     * reaches the C parse; the other branch is dropped.
     */
    @Test
    public void testIfdefActiveBranch(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * The same block with {@code FOO} undefined – the {@code #else} branch is the one that reaches the C parse.
     */
    @Test
    public void testIfdefElseBranch(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * A directive inside the branch that is kept: the ranges dropped around it must still be produced in
     * ascending order, which a plain declaration in the branch does not exercise.
     */
    @Test
    public void testIfdefNestedDirective(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code #if 1 ... #elif 0 ... #else ... #endif} – if / elif / else block
     */
    @Test
    public void testIfElifElse(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * The first branch whose condition holds wins, so a false {@code #if} followed by a true {@code #elif}
     * compiles the {@code #elif} body and not the {@code #else}.
     */
    @Test
    public void testElifSelected(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code #include <stdio.h>} – system include directive, parsed as
     * {@code CPsiSharpIndepIncludeImpl} in the preprocessor tree and
     * treated as an {@code OUTER} range in the primary C tree.
     */
    @Test
    public void testSystemInclude(Context context) throws Exception {
        doTest(context, null);
    }
}
