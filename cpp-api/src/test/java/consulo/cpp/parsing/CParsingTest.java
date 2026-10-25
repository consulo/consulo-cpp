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
 * Parsing tests for the C language (.c files).
 *
 * <p>Each test method corresponds to a pair of resource files under
 * {@code src/test/resources/parsing/c/}:
 * <ul>
 * <li>{@code MethodName.c}   – C source input</li>
 * <li>{@code MethodName.txt} – expected PSI tree dump</li>
 * </ul>
 */
public class CParsingTest extends CParsingBaseTest {
    public CParsingTest() {
        super("parsing/c", "c");
    }

    @Nonnull
    @Override
    protected LanguageFileType getFileType(@Nonnull Context context, @Nullable Object testContext) {
        return CSourceFileType.INSTANCE;
    }

    /**
     * {@code int x;} – simple global variable declaration
     */
    @Test
    public void testGlobalVariable(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code enum Color { RED, GREEN };} – enum declaration
     */
    @Test
    public void testEnum(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code void foo();} – forward function declaration
     */
    @Test
    public void testFunctionDeclaration(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code int add(int a, int b) { return; }} – function with a body
     */
    @Test
    public void testFunctionWithBody(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code if (x > 0) { return; } else { return; }} – if/else statement
     */
    @Test
    public void testIfStatement(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code while (x > 0) { x--; }} – while loop
     */
    @Test
    public void testWhileStatement(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code for (int i = 0; i < 10; i++) { x++; }} – for loop
     */
    @Test
    public void testForStatement(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code while (1) { break; continue; }} – break and continue
     */
    @Test
    public void testBreakContinue(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Local variable declarations inside a function body:
     * {@code int result = add(3, 4);} and {@code char buf[256];}
     */
    @Test
    public void testLocalVariable(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Expression statements inside a function body:
     * {@code x++;} and {@code add(3, 4);}
     */
    @Test
    public void testExpressionStatement(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * A stray {@code @} token (not an expression starter) inside a function body
     * must not cause an infinite loop — the parser must report an error, skip the
     * bad token, and continue parsing the next statement ({@code x++;}) normally.
     */
    @Test
    public void testUnexpectedToken(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code do { x++; } while (x < 10);} – do/while loop
     */
    @Test
    public void testDoWhileStatement(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code switch (x) { case 1: return; default: x++; }} – switch with case/default labels
     */
    @Test
    public void testSwitchStatement(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code goto end; … end: return;} – goto statement
     */
    @Test
    public void testGotoStatement(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code start: x++; end: return;} – labeled statements
     */
    @Test
    public void testLabeledStatement(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code int n = sizeof(int);} – sizeof expression with a type argument
     */
    @Test
    public void testSizeofExpression(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code -x; &x;} – unary prefix expressions (negate and address-of)
     */
    @Test
    public void testPrefixExpression(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code struct Point { int x; int y; };} – struct declaration with two fields
     */
    @Test
    public void testStructDeclaration(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code union Data { int i; int j; };} – union declaration with two fields
     */
    @Test
    public void testUnionDeclaration(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Local variable declarations using {@code short}, {@code float}, and {@code double} types
     */
    @Test
    public void testShortFloatDouble(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Typedefs of a builtin type, a struct tag, an anonymous enum and an anonymous struct; the names then used as types
     */
    @Test
    public void testTypedefs(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Several declarators per declaration, arrays, pointers, qualifiers, variadic and {@code (void)} parameters, bit-fields
     */
    @Test
    public void testDeclarators(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Function pointer parameters, variables, typedefs and arrays
     */
    @Test
    public void testFunctionPointers(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Casts to pointer, builtin and typedef types, a parenthesised operand that is no cast, a compound literal
     */
    @Test
    public void testCasts(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Designated, indexed and nested initializers
     */
    @Test
    public void testInitializers(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Comparison, shift, compound assignment, logical and sizeof operators
     */
    @Test
    public void testOperators(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * for with a declaration, do/while, a case range, goto and a label
     */
    @Test
    public void testStatements(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Basic and extended GCC asm - a template of two literals, operands and clobbers - an MSVC {@code __asm} block,
     * and an asm label on a declaration
     */
    @Test
    public void testInlineAsm(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Adjacent string literals, string macros whose definition is not visible, prefixed literals
     */
    @Test
    public void testStrings(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * K&R definitions, whose parameters are declared between the declarator and the body: {@code int g(i) int i; { }}
     */
    @Test
    public void testKnrDefinition(Context context) throws Exception {
        doTest(context, null);
    }
}
