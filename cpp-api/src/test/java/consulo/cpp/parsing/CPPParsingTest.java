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
import consulo.annotation.access.RequiredReadAction;
import consulo.cpp.lang.CPPSourceFileType;
import consulo.cpp.lang.version.CDialectKeyword;
import consulo.cpp.lang.version.CLanguageFeature;
import consulo.cpp.lang.version.CLanguageVersion;
import consulo.cpp.lang.version.Cpp14LanguageVersion;
import consulo.cpp.lang.version.Cpp20LanguageVersion;
import consulo.language.file.LanguageFileType;
import consulo.language.version.LanguageVersion;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
    /**
     * C++20 with the kernel launches of CUDA and HIP, as their plugins' versions have them.
     */
    private static final CLanguageVersion KERNEL_LAUNCH_VERSION = new Cpp20LanguageVersion("TEST_KERNEL_LAUNCH", "C++20 with kernel launches") {
        {
            addFeature(CLanguageFeature.KERNEL_LAUNCH);
        }
    };

    /**
     * C++14 with Metal's words, as a dialect's version adds them.
     */
    private static final CLanguageVersion DIALECT_KEYWORDS_VERSION = new Cpp14LanguageVersion("TEST_DIALECT_KEYWORDS", "C++14 with dialect keywords") {
        @Override
        public Map<String, CDialectKeyword> getDialectKeywords() {
            return Map.of(
                "device", CDialectKeyword.ADDRESS_SPACE,
                "constant", CDialectKeyword.ADDRESS_SPACE,
                "threadgroup", CDialectKeyword.ADDRESS_SPACE,
                "kernel", CDialectKeyword.FUNCTION_QUALIFIER,
                "vertex", CDialectKeyword.FUNCTION_QUALIFIER
            );
        }
    };

    @RequiredReadAction
    @Override
    protected LanguageVersion resolveLanguageVersion(Context context, @Nullable Object testContext, FileType fileType) {
        String test = context.testInfo().getTestMethod().map(method -> method.getName()).orElse("");
        if (test.equals("testKernelLaunch")) {
            return KERNEL_LAUNCH_VERSION;
        }
        if (test.equals("testDialectKeywords")) {
            return DIALECT_KEYWORDS_VERSION;
        }
        return super.resolveLanguageVersion(context, testContext, fileType);
    }

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
     * Names in member initializers, lambda captures, {@code typeid} and a namespace alias
     */
    @Test
    public void testNamesWithoutReferences(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code namespace a::b::inline c { }} – namespaces inside each other – and {@code namespace alias = a::b;}
     */
    @Test
    public void testNestedNamespace(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code auto [x, y] = p;} and {@code for (auto& [key, value] : m)} – structured bindings
     */
    @Test
    public void testStructuredBinding(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * An out-of-class constructor of a class template, {@code ::new (p) T(...)} and
     * {@code this->Base<T>::operator=(r)}, as libstdc++ writes them
     */
    @Test
    public void testLibstdcxxConstructs(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * {@code template<...> inline R& operator<<(...) { ... }} – a free operator template, inside a namespace
     */
    @Test
    public void testTemplateOperatorFunction(Context context) throws Exception {
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
     * {@code const FVector2f Half(BallSize * 0.5f, ...)} - a name, an operator and then what no declarator can hold make
     * the items arguments, not pointer or reference parameters; {@code Foo f(Bar * baz)} stays a function
     * (Unreal {@code Brick2D/BrickPawn.cpp})
     */
    @Test
    public void testDirectInitOperators(Context context) throws Exception {
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

    /**
     * Kernel launches of CUDA and HIP, in a version which has them: {@code <<<...>>>} holds the execution configuration - a shift inside it still shifts,
     * the {@code >>>} closes it - and plain shifts stay shifts
     */
    @Test
    public void testKernelLaunch(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * A macro defined on continued lines in a branch of a conditional, used between the type and the name - CUDA's
     * {@code uint3 __device_builtin__ __STORAGE__ threadIdx;}
     */
    @Test
    public void testMacroContinuation(Context context) throws Exception {
        doTest(context, null);
    }

    /**
     * A dialect's words in a version which has them: address spaces where {@code const} goes - before the type, after a
     * {@code *} - and function qualifiers where {@code inline} goes
     */
    @Test
    public void testDialectKeywords(Context context) throws Exception {
        doTest(context, null);
    }
}
