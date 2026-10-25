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
import consulo.cpp.lang.CPPSourceFileType;
import consulo.language.file.LanguageFileType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Parsing tests for the C++ language (.cpp files).
 *
 * <p>Each test method corresponds to a pair of resource files under
 * {@code src/test/resources/parsing/cpp/}:
 * <ul>
 * <li>{@code MethodName.cpp} – C++ source input</li>
 * <li>{@code MethodName.txt} – expected PSI tree dump</li>
 * </ul>
 */
public class CPPParsingTest extends CParsingBaseTest {
    public CPPParsingTest() {
        super("parsing/cpp", "cpp");
    }

    @Nonnull
    @Override
    protected LanguageFileType getFileType(@Nonnull Context context, @Nullable Object testContext) {
        return CPPSourceFileType.INSTANCE;
    }

    /**
     * {@code class Foo { public: void bar(); };} – class with a method declaration
     */
    @Test
    public void testClass(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code namespace std { void foo(); }} – namespace with a method declaration
     */
    @Test
    public void testNamespace(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code class Base {}; class Child : public Base {};} – class inheritance
     */
    @Test
    public void testInheritance(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code std::cout << result << std::endl;} – scope-resolution ({@code ::})
     * and stream-insertion ({@code <<}) inside a function body must parse without errors.
     */
    @Test
    public void testScopeResolution(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code class Foo { int x; };} – class with a field but no access specifier
     */
    @Test
    public void testClassNoAccessModifier(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code path normal(pathname, pathname + size);} – a variable initialized by constructor arguments, which only the
     * arguments tell apart from a function declaration (boost {@code filesystem/src/path.cpp})
     */
    @Test
    public void testDirectInit(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code std::locale path::imbue(std::locale const& loc)} – a qualified return type before a qualified function name
     */
    @Test
    public void testQualifiedReturnType(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code if (a && \n#ifdef X\n b && \n#endif\n c)} – a conditional directive in the middle of an expression
     */
    @Test
    public void testDirectiveInExpression(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code DECL path path_algorithms::f(path const& p)} – an unknown macro before the return type of a function with
     * a qualified name, as boost's {@code BOOST_FILESYSTEM_DECL} reads where its definition is not seen
     */
    @Test
    public void testUnknownMacroPrefix(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * What {@code TEST(CalcTest, AddsNumbers) {}} of Google Test expands to: static assertions, the test class, the
     * definition of its static member and of its {@code TestBody()}
     */
    @Test
    public void testGTestTest(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * What {@code EXPECT_EQ} and {@code EXPECT_THROW} of Google Test expand to: {@code switch (0) case 0: default:} before
     * a statement, a declaration as an {@code if} condition, {@code else label: statement}
     */
    @Test
    public void testGTestExpansion(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code if (T x = f())}, {@code if (T x{})}, {@code while (T* p = f())}, {@code if (init; cond)} – conditions which
     * declare their variable; {@code if (a * b)} and {@code if (a < b)} stay expressions
     */
    @Test
    public void testConditionDeclaration(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Statements and declarations in a branch whose condition is false
     */
    @Test
    public void testInactiveBranch(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Template arguments in an expression - {@code std::use_facet<T>(x)}, {@code std::get<0>(t)},
     * {@code std::numeric_limits<int>::max()} - and comparisons which only look like them, {@code a < b && c > (d)}
     */
    @Test
    public void testTemplateCallExpression(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code T* volatile member;} in a struct, followed by a declaration with an unknown macro prefix
     */
    @Test
    public void testVolatilePointerMember(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Out-of-class member definitions behind an export macro or attribute, with a qualified return type and a
     * parameter written {@code std::locale const& loc}
     */
    @Test
    public void testExportedMemberDefinition(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * Lines which are only macro invocations, {@code LIST_TESTS(double, f)} without a semicolon, and a builtin taking a
     * type and an expression, {@code __builtin_bit_cast(T, static_cast<void*>(p))}
     */
    @Test
    public void testMacroInvocationLines(Context context) throws Exception {
        doTest(context, null);
    }
}
