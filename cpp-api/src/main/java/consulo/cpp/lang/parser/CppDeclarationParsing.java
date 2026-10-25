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
package consulo.cpp.lang.parser;

import consulo.cpp.lang.psi.CElementTypes;
import consulo.cpp.localize.CLocalize;
import consulo.language.ast.IElementType;
import consulo.language.parser.PsiBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * C++ declarations, on top of the C ones: namespaces, {@code using}, templates, concepts and constraints, modules,
 * linkage specifications, classes with base clauses and access sections, qualified names and template arguments,
 * references and pointers to members, operator functions, constructor initializers.
 *
 * @author VISTALL
 * @since 2026-10-25
 */
public class CppDeclarationParsing extends CDeclarationParsing {
    /**
     * C++ specifiers the lexer reads as identifiers.
     */
    private static final Set<String> CONTEXTUAL_MODIFIERS = Set.of("consteval", "constinit");

    /**
     * Words starting an expression which the lexer reads as identifiers.
     */
    private static final Set<String> CONTEXTUAL_EXPRESSION_STARTS = Set.of("co_await", "co_yield", "co_return", "requires");

    public CppDeclarationParsing(CParsingContext context) {
        super(context);
    }

    // -----------------------------------------------------------------------
    // Declarations
    // -----------------------------------------------------------------------

    @Override
    protected boolean parseLanguageDeclaration(Scope scope) {
        if (scope == Scope.FILE && (parseModuleDeclaration() || parseExport(scope))) {
            return true;
        }
        if (scope == Scope.PARAMETER) {
            return false;
        }
        IElementType type = token();
        if (type == NAMESPACE_KEYWORD) {
            parseNamespace();
            return true;
        }
        if (type == INLINE_KEYWORD && lookAhead(1) == NAMESPACE_KEYWORD) {
            // inline namespace v1 { ... }
            advance();
            parseNamespace();
            return true;
        }
        if (type == USING_KEYWORD) {
            parseUsing();
            return true;
        }
        if (type == TEMPLATE_KEYWORD) {
            parseTemplate(scope);
            return true;
        }
        if (type == EXTERN_KEYWORD && lookAhead(1) == STRING_LITERAL) {
            parseLinkageSpecification(scope);
            return true;
        }
        return false;
    }

    @Override
    protected void parseKnrParameterDeclarations(Scope scope, Declarator declarator) {
        // C++ has no K&R definitions: what follows the parameters is the function's own
    }

    @Override
    protected boolean isFunctionBodyStart() {
        return at(LBRACE) || at(COLON) || at(TRY_KEYWORD);
    }

    /**
     * The body of a function definition, with a constructor's member initializers before it, and the handlers of a
     * function try block after it.
     */
    @Override
    protected void parseFunctionBody() {
        boolean functionTry = consume(TRY_KEYWORD);

        if (consume(COLON)) {
            // : member(args), base{args}
            while (!eof() && !at(LBRACE)) {
                if (at(IDENTIFIER) || at(DOUBLE_COLON)) {
                    parseQualifiedName();
                }
                else {
                    error(CLocalize.identifierExpected());
                    break;
                }

                if (at(LPARENTH)) {
                    expressions().parseArgumentList();
                }
                else if (at(LBRACE)) {
                    expressions().parseInitializerList();
                }
                consume(ELLIPSIS);

                if (!consume(COMMA)) {
                    break;
                }
            }
        }

        super.parseFunctionBody();

        while (functionTry && at(CATCH_KEYWORD)) {
            advance();
            if (at(LPARENTH)) {
                parseParameterList();
            }
            if (!at(LBRACE)) {
                break;
            }
            statements().parseCodeBlock();
        }
    }

    @Override
    protected void parseDeclaratorInitializer() {
        if (at(DEFAULT_KEYWORD) || at(DELETE_KEYWORD)) {
            // = default, = delete
            advance();
            return;
        }
        super.parseDeclaratorInitializer();
    }

    @Override
    protected void parseBraceInitializer(Declarator declarator) {
        if (!declarator.myFunction && at(LBRACE)) {
            // int x{1};
            expressions().parseInitializerList();
        }
    }

    // -----------------------------------------------------------------------
    // Specifiers
    // -----------------------------------------------------------------------

    @Override
    protected boolean isLanguageModifier() {
        // consteval, constinit
        return at(IDENTIFIER) && CONTEXTUAL_MODIFIERS.contains(tokenText());
    }

    /**
     * {@code API path path_algorithms::normal(...)}, {@code API std::locale path::imbue(...)},
     * {@code API path::codecvt_type const& path::codecvt()}: nothing else puts a name before a whole type.
     */
    @Override
    protected boolean isMacroBeforeNamedType() {
        PsiBuilder.Marker speculation = mark();
        try {
            advance();
            parseQualifiedName();
            // the type's own qualifiers and the pointers of the declarator, each qualified too: T const* volatile& name
            while (at(POINTER_QUALIFIERS)) {
                advance();
            }
            while (at(ASTERISK) || at(AND) || at(ANDAND)) {
                advance();
                while (at(POINTER_QUALIFIERS)) {
                    advance();
                }
            }
            return at(IDENTIFIER);
        }
        finally {
            speculation.rollbackTo();
        }
    }

    /**
     * Also clang's {@code __decltype(e)} and the builtin type transformations, {@code __add_rvalue_reference(T)}.
     */
    @Override
    protected boolean isParenthesizedTypeSpecifier() {
        if (super.isParenthesizedTypeSpecifier()) {
            return true;
        }
        return lookAhead(1) == LPARENTH && at(IDENTIFIER) && ("__decltype".equals(tokenText()) || isTypeTransform(tokenText()));
    }

    /**
     * Clang's and GCC's builtin type transformations, {@code __add_rvalue_reference(T)}, {@code __underlying_type(E)}.
     */
    private static boolean isTypeTransform(String name) {
        return name.startsWith("__add_") || name.startsWith("__remove_") || name.equals("__decay") || name.equals("__underlying_type")
            || name.equals("__make_signed") || name.equals("__make_unsigned");
    }

    /**
     * {@code typename T::type}, {@code decltype(e)}.
     */
    @Override
    protected boolean parseLanguageTypeSpecifier() {
        if (at(TYPENAME_KEYWORD)) {
            // a dependent type, typename T::type
            advance();
            parseQualifiedName();
            return true;
        }
        if (at(DECLTYPE_KEYWORD)) {
            advance();
            if (at(LPARENTH)) {
                skipBalanced();
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean isTypeNameStart(Scope scope) {
        if (at(DOUBLE_COLON)) {
            return true;
        }
        if (at(IDENTIFIER)) {
            IElementType next = lookAhead(1);
            if (myContext.isTypeName(tokenText())) {
                if (next == DOUBLE_COLON) {
                    // T::f() defines a member of T, T::type x declares x
                    return isQualifiedTypeAhead(scope);
                }
                if (next == LPARENTH && scope != Scope.PARAMETER) {
                    // T(...) is T's constructor; T (*fp)(), T (&r)[2], T (C::*m) declare with the type T
                    IElementType afterParenthesis = lookAhead(2);
                    return afterParenthesis == ASTERISK || afterParenthesis == XOR || afterParenthesis == AND || isMemberPointerAhead(2);
                }
            }
            else {
                if (next == OPERATOR_KEYWORD) {
                    // the return type of an operator, X operator=(const X&)
                    return true;
                }
                if (next == DOUBLE_COLON || next == LT) {
                    return isQualifiedTypeAhead(scope);
                }
                if (scope == Scope.PARAMETER && next == ELLIPSIS) {
                    // f(Args...)
                    return true;
                }
            }
        }
        return super.isTypeNameStart(scope);
    }

    /**
     * At {@code A::...} or {@code A<...>}: whether the qualified name is a type - followed by a declarator - rather
     * than itself the declarator, as in {@code Foo::Foo()} and {@code void Foo::bar()}. A parameter may have no
     * declarator: {@code f(U<int>)}.
     */
    private boolean isQualifiedTypeAhead(Scope scope) {
        PsiBuilder.Marker speculation = mark();
        try {
            parseQualifiedName();
            if (scope == Scope.PARAMETER && (at(COMMA) || at(RPARENTH) || at(LBRACKET) || at(EQ))) {
                return true;
            }
            return at(IDENTIFIER) || at(ASTERISK) || at(AND) || at(ANDAND) || at(ELLIPSIS) || at(MODIFIERS) || at(OPERATOR_KEYWORD)
                || at(LPARENTH) && (lookAhead(1) == ASTERISK || lookAhead(1) == AND || isMemberPointerAhead(1))
                || isMemberPointerAhead(0);
        }
        finally {
            speculation.rollbackTo();
        }
    }

    /**
     * {@code T &x}, {@code T &&x} besides {@code T *x}.
     */
    @Override
    protected boolean isPointerDeclaratorToken(@Nullable IElementType type) {
        return type == ASTERISK || type == AND || type == ANDAND;
    }

    @Override
    protected boolean isQualifiedNameStart() {
        return at(IDENTIFIER) || at(DOUBLE_COLON);
    }

    /**
     * {@code ::? name <args>? (:: name <args>?)*}
     */
    @Override
    protected void parseQualifiedName() {
        consume(DOUBLE_COLON);
        while (true) {
            consume(TEMPLATE_KEYWORD);
            if (!consume(IDENTIFIER)) {
                error(CLocalize.identifierExpected());
                return;
            }
            if (at(LT)) {
                parseTemplateArguments();
            }
            if (at(DOUBLE_COLON) && (lookAhead(1) == IDENTIFIER || lookAhead(1) == TEMPLATE_KEYWORD)) {
                advance();
            }
            else {
                return;
            }
        }
    }

    /**
     * {@code < ... >}, nested ones and {@code >>} included; parsed for their extent only.
     */
    public void parseTemplateArguments() {
        int depth = 0;
        do {
            IElementType type = token();
            if (type == LT) {
                depth++;
            }
            else if (type == GT) {
                depth--;
            }
            else if (type == GTGT) {
                depth -= 2;
            }
            else if (type == LPARENTH || type == LBRACKET || type == LBRACE) {
                // f<X{}>(), f<[] { return 1; }>()
                skipBalanced();
                continue;
            }
            else if (type == SEMICOLON || type == RBRACE) {
                error(CLocalize.gtExpected());
                return;
            }
            advance();
        }
        while (depth > 0 && !eof());
    }

    /**
     * Also {@code (&}, and {@code (Class::*}.
     */
    @Override
    protected boolean isNestedDeclaratorAt(int offset) {
        return lookAhead(offset + 1) == AND || isMemberPointerAhead(offset + 1) || super.isNestedDeclaratorAt(offset);
    }

    /**
     * At {@code typename}: whether the dependent type is made a temporary, {@code typename T::X{}} or
     * {@code typename T::X(a)}, rather than declared with.
     */
    private boolean isDependentTemporaryAhead() {
        PsiBuilder.Marker speculation = mark();
        try {
            advance();
            parseQualifiedName();
            return at(LBRACE) || at(LPARENTH) && !isNestedDeclaratorAt(0);
        }
        finally {
            speculation.rollbackTo();
        }
    }

    /**
     * Whether a pointer to member, {@code Class::*} or {@code ns::Class<T>::*}, starts {@code offset} tokens ahead.
     */
    @Override
    public boolean isMemberPointerAhead(int offset) {
        PsiBuilder.Marker speculation = mark();
        try {
            for (int i = 0; i < offset; i++) {
                advance();
            }
            return skipMemberPointerClass() && at(ASTERISK);
        }
        finally {
            speculation.rollbackTo();
        }
    }

    @Override
    protected void parseMemberPointerClass() {
        PsiBuilder.Marker memberClass = mark();
        skipMemberPointerClass();
        memberClass.done(CElementTypes.DOUBLE_COLON_TYPE_REF);
    }

    /**
     * The class of a pointer to member, {@code ::? name <args>? (:: name <args>?)* ::}; whether it was one.
     */
    private boolean skipMemberPointerClass() {
        consume(DOUBLE_COLON);
        boolean any = false;
        while (at(IDENTIFIER)) {
            advance();
            if (at(LT)) {
                parseTemplateArguments();
            }
            if (!consume(DOUBLE_COLON)) {
                return false;
            }
            any = true;
            consume(TEMPLATE_KEYWORD);
        }
        return any;
    }

    // -----------------------------------------------------------------------
    // Declarators
    // -----------------------------------------------------------------------

    /**
     * A structured binding, {@code auto [key, value] = pair}.
     */
    @Override
    protected boolean parseLanguageDeclarator(Scope scope, Declarator declarator) {
        if (!at(LBRACKET) || lookAhead(1) != IDENTIFIER || declarator.myConsumed
            || !(scope == Scope.BLOCK || scope == Scope.FILE || scope == Scope.FOR_INIT || scope == Scope.CONDITION)) {
            return false;
        }
        advance();
        declarator.myName = tokenText();
        while (!eof() && !at(RBRACKET) && !at(SEMICOLON)) {
            advance();
        }
        expect(RBRACKET, CLocalize.rbracketExpected());
        declarator.myConsumed = true;
        return true;
    }

    @Override
    protected boolean isDeclaratorNameStart() {
        return at(IDENTIFIER) || at(TILDE) || at(OPERATOR_KEYWORD) || at(DOUBLE_COLON);
    }

    /**
     * A declarator name: an identifier, qualified ({@code Foo::bar}), a destructor ({@code ~Foo}) or an operator
     * ({@code operator+}). The qualifier becomes a type reference, so the name is the declaration's first identifier
     * child.
     */
    @Override
    protected @Nullable String parseDeclaratorName() {
        if (at(DOUBLE_COLON) || at(IDENTIFIER) && (lookAhead(1) == DOUBLE_COLON || lookAhead(1) == LT && isQualifierAhead())) {
            PsiBuilder.Marker qualifier = mark();
            consume(DOUBLE_COLON);
            // A<T>::x<U*> = 1: the last name with arguments is the declarator, a specialization of a variable template
            while (at(IDENTIFIER) && (lookAhead(1) == DOUBLE_COLON || lookAhead(1) == LT && isQualifierAhead())) {
                advance();
                if (at(LT)) {
                    parseTemplateArguments();
                }
                if (!consume(DOUBLE_COLON)) {
                    break;
                }
            }
            qualifier.done(CElementTypes.DOUBLE_COLON_TYPE_REF);
        }

        if (consume(TILDE)) {
            String name = tokenText();
            expect(IDENTIFIER, CLocalize.identifierExpected());
            return "~" + name;
        }

        if (at(OPERATOR_KEYWORD)) {
            parseOperatorName();
            if (at(LT) && isTemplateArgumentsAhead()) {
                // a specialization, operator==<0>(...)
                parseTemplateArguments();
            }
            return "operator";
        }

        String name = super.parseDeclaratorName();
        if (name != null && at(LT) && isTemplateArgumentsAhead()) {
            // an explicit specialization, f<int>(...), or one of a variable template, is_same_v<T, T> = true
            parseTemplateArguments();
        }
        return name;
    }

    private boolean isQualifierAhead() {
        PsiBuilder.Marker speculation = mark();
        try {
            advance();
            parseTemplateArguments();
            return at(DOUBLE_COLON);
        }
        finally {
            speculation.rollbackTo();
        }
    }

    private boolean isTemplateArgumentsAhead() {
        PsiBuilder.Marker speculation = mark();
        try {
            parseTemplateArguments();
            return at(LPARENTH) || at(EQ) || at(SEMICOLON) || at(LBRACE);
        }
        finally {
            speculation.rollbackTo();
        }
    }

    /**
     * A parameter pack, {@code T... args}.
     */
    @Override
    protected void parsePackDeclarator(Declarator declarator) {
        if (!at(ELLIPSIS)) {
            return;
        }
        advance();
        declarator.myConsumed = true;
        if (at(IDENTIFIER)) {
            declarator.myName = parseDeclaratorName();
        }
    }

    @Override
    protected boolean parseConstructorArguments(Scope scope, Declarator declarator, boolean firstSuffix, boolean nested, boolean array) {
        if (array && scope != Scope.PARAMETER) {
            // an array initialized in parentheses, char s[]("hello")
            expressions().parseArgumentList();
            return true;
        }
        if (firstSuffix && !nested && declarator.myName != null
            && (scope == Scope.BLOCK || scope == Scope.FILE || scope == Scope.FOR_INIT) && isArgumentListAhead(scope)) {
            // T x(a + b): a variable initialized by constructor arguments, not a function
            expressions().parseArgumentList();
            declarator.myConsumed = true;
            return true;
        }
        return false;
    }

    /**
     * At the {@code (} after a declarator name: whether it holds constructor arguments - {@code T x(a + b)} - rather than
     * parameters. An item which can only be an expression decides it. {@code T x()} declares a function, and items which
     * are bare names count as parameters at file scope - {@code void f(Foo, Bar);} with types declared elsewhere - and
     * as arguments in a block, unless they are known types.
     */
    private boolean isArgumentListAhead(Scope scope) {
        PsiBuilder.Marker speculation = mark();
        try {
            advance();
            if (at(RPARENTH)) {
                return false;
            }
            while (true) {
                if (isExpressionItem(scope)) {
                    return true;
                }
                while (!eof() && !at(COMMA) && !at(RPARENTH) && !at(SEMICOLON) && !at(LBRACE)) {
                    if (at(LPARENTH) || at(LBRACKET)) {
                        skipBalanced();
                    }
                    else {
                        advance();
                    }
                }
                if (!consume(COMMA)) {
                    return false;
                }
            }
        }
        finally {
            speculation.rollbackTo();
        }
    }

    /**
     * Whether the item at the current position of a parenthesised list can only be an expression. Consumes the name
     * it starts with, if any.
     */
    private boolean isExpressionItem(Scope scope) {
        if (at(TYPES) || at(MODIFIERS) || at(AGGREGATE_KEYWORDS) || at(ELLIPSIS) || at(TYPENAME_KEYWORD) || at(DECLTYPE_KEYWORD)
            || isAttributeStart()) {
            return false;
        }
        if (!at(IDENTIFIER) && !at(DOUBLE_COLON)) {
            // a literal, a parenthesised expression, &x, this, a lambda
            return true;
        }

        String last = null;
        consume(DOUBLE_COLON);
        while (at(IDENTIFIER)) {
            last = tokenText();
            advance();
            if (at(LT)) {
                parseTemplateArguments();
            }
            if (!consume(DOUBLE_COLON)) {
                break;
            }
        }
        if (last == null) {
            return true;
        }
        if (at(IDENTIFIER) || at(ASTERISK) || at(AND) || at(ANDAND) || at(ELLIPSIS) || at(MODIFIERS)) {
            // T name, T*, T&, T const
            return false;
        }
        if (at(COMMA) || at(RPARENTH)) {
            // a bare name: a type, or a variable
            return scope != Scope.FILE && !myContext.isTypeName(last);
        }
        if (at(EQ)) {
            // a parameter with its default argument, f(Number<I> = Number<0>{})
            return false;
        }
        if (at(LPARENTH) && isNestedDeclaratorAt(0)) {
            // a parameter declaring a pointer has suffixes after it, R (*func)(P); a call has none, f(&x), move(*this)
            skipBalanced();
            return !at(LPARENTH) && !at(LBRACKET);
        }
        // a call, a member access, an operator
        return true;
    }

    /**
     * {@code operator+}, {@code operator()}, {@code operator[]}, {@code operator new[]}, {@code operator int}...
     */
    public void parseOperatorName() {
        advance();
        if (at(LPARENTH) || at(LBRACKET)) {
            advance();
            consume(RPARENTH);
            consume(RBRACKET);
        }
        else if (at(NEW_KEYWORD) || at(DELETE_KEYWORD)) {
            advance();
            if (at(LBRACKET)) {
                advance();
                consume(RBRACKET);
            }
        }
        else if (at(LE) && lookAhead(1) == GT) {
            // operator<=>, which the lexer reads as <= and >
            advance();
            advance();
        }
        else if (at(STRING_LITERAL) && lookAhead(1) == IDENTIFIER) {
            // a literal operator, operator ""_km
            advance();
            advance();
        }
        else if (at(OPERATION_SET) || at(COMMA) || at(ARROW) || at(GE) || at(GTGT) || at(GTGTEQ)) {
            advance();
        }
        else {
            // a conversion operator, operator bool
            parseConversionType();
        }
    }

    /**
     * The type of a conversion function - {@code operator const T&}, {@code operator struct v} - its specifiers and
     * pointers only: the parentheses which follow are the function's own parameters, not a function type.
     */
    private void parseConversionType() {
        PsiBuilder.Marker typeRef = mark();
        while (at(MODIFIERS)) {
            advance();
        }
        consume(TYPENAME_KEYWORD);
        if (at(AGGREGATE_KEYWORDS)) {
            typeRef.drop();
            parseAggregate(false);
        }
        else {
            boolean keyword = false;
            while (at(TYPES) || at(MODIFIERS) || isBitInt()) {
                keyword |= !at(MODIFIERS);
                if (isBitInt()) {
                    advance();
                    skipBalanced();
                    continue;
                }
                advance();
            }
            if (!keyword && (at(DECLTYPE_KEYWORD) || at(TYPEOF_KEYWORD))) {
                advance();
                if (at(LPARENTH)) {
                    skipBalanced();
                }
            }
            else if (!keyword && (at(IDENTIFIER) || at(DOUBLE_COLON))) {
                parseQualifiedName();
            }
            else if (!keyword) {
                consume(AUTO_KEYWORD);
            }
            while (at(MODIFIERS)) {
                advance();
            }
            typeRef.done(CElementTypes.TYPE_REF);
        }
        while (at(ASTERISK) || at(AND) || at(ANDAND) || at(POINTER_QUALIFIERS) || isMemberPointerAhead(0)) {
            if (isMemberPointerAhead(0)) {
                skipMemberPointerClass();
            }
            advance();
        }
    }

    /**
     * Also {@code (&}, {@code (&&} and {@code (Class::*}.
     */
    @Override
    protected boolean isNestedDeclarator() {
        IElementType next = lookAhead(1);
        if (next == AND || next == ANDAND) {
            return true;
        }
        if (isMemberPointerAhead(1)) {
            // void (Class::*method)()
            return true;
        }
        return super.isNestedDeclarator();
    }

    /**
     * After the parameters of a function: {@code const}, {@code &}, {@code noexcept(...)}, {@code throw(...)},
     * {@code override}, {@code final}, attributes, a requires-clause, {@code -> type}.
     */
    @Override
    protected void parseFunctionQualifiers() {
        while (true) {
            if (at(POINTER_QUALIFIERS) || at(AND) || at(ANDAND)) {
                advance();
            }
            else if (at(NOEXCEPT_KEYWORD) || at(THROW_KEYWORD)) {
                advance();
                if (at(LPARENTH)) {
                    skipBalanced();
                }
            }
            else if (at(IDENTIFIER) && ("override".equals(tokenText()) || "final".equals(tokenText()))) {
                advance();
            }
            else if (isAttributeStart()) {
                // f() const [[clang::lifetimebound]] -> T
                parseAttribute();
            }
            else if (isContextualKeyword("requires")) {
                // void f(T) requires C<T>;
                advance();
                parseConstraint();
            }
            else if (at(ARROW)) {
                advance();
                parseTypeName();
            }
            else {
                return;
            }
        }
    }

    /**
     * An explicit object parameter, {@code f(this const S& self)}.
     */
    @Override
    protected boolean parseExplicitObjectParameter() {
        return consume(THIS_KEYWORD);
    }

    // -----------------------------------------------------------------------
    // Type names
    // -----------------------------------------------------------------------

    @Override
    protected boolean isLanguageTypeNameStart() {
        return at(DECLTYPE_KEYWORD) || at(TYPENAME_KEYWORD);
    }

    @Override
    protected void parseTypeNamePrefix() {
        consume(TYPENAME_KEYWORD);
    }

    @Override
    protected boolean isFunctionalCastAhead() {
        return at(TYPES) && (lookAhead(1) == LPARENTH || lookAhead(1) == LBRACE);
    }

    // -----------------------------------------------------------------------
    // Statement level
    // -----------------------------------------------------------------------

    @Override
    public boolean looksLikeDeclaration() {
        IElementType type = token();
        if (type == IDENTIFIER && CONTEXTUAL_EXPRESSION_STARTS.contains(tokenText())) {
            // coroutine and constraint words the lexer reads as identifiers start expressions
            return false;
        }
        if (type == IDENTIFIER && CONTEXTUAL_MODIFIERS.contains(tokenText())) {
            return true;
        }
        if (type == DOUBLE_COLON && lookAhead(1) == OPERATOR_KEYWORD) {
            // ::operator delete(p);
            return false;
        }
        if (type == TYPENAME_KEYWORD) {
            // typename T::X x; - but typename T::X{}.f() and typename T::X(a) are expressions
            return !isDependentTemporaryAhead();
        }
        if (type == USING_KEYWORD || type == TEMPLATE_KEYWORD || type == NAMESPACE_KEYWORD || type == DECLTYPE_KEYWORD) {
            return true;
        }

        IElementType next = lookAhead(1);
        if (type == DOUBLE_COLON || type == IDENTIFIER && (next == DOUBLE_COLON || next == LT)) {
            PsiBuilder.Marker speculation = mark();
            try {
                parseQualifiedName();
                if (at(IDENTIFIER)) {
                    // two names in a row start no expression: std::string s;  std::string s(10, 'x');
                    return true;
                }
                if (at(ASTERISK) || at(AND) || at(ANDAND)) {
                    return lookAhead(1) == IDENTIFIER && (lookAhead(2) == SEMICOLON || lookAhead(2) == EQ || lookAhead(2) == COMMA);
                }
                return false;
            }
            finally {
                speculation.rollbackTo();
            }
        }
        return super.looksLikeDeclaration();
    }

    // -----------------------------------------------------------------------
    // Classes
    // -----------------------------------------------------------------------

    /**
     * {@code final}, and the base clause of a class.
     */
    @Override
    protected void parseAggregateHeadTail(IElementType keyword) {
        if (at(IDENTIFIER) && "final".equals(tokenText())) {
            advance();
        }
        if (keyword != ENUM_KEYWORD && at(COLON)) {
            parseBaseClause();
        }
    }

    /**
     * A declared class, struct, union or enum names a type.
     */
    @Override
    protected void registerAggregateName(String name) {
        myContext.addTypeName(name);
    }

    /**
     * {@code : public Base, virtual protected Other<T>}
     */
    private void parseBaseClause() {
        PsiBuilder.Marker list = mark();
        advance();

        while (true) {
            PsiBuilder.Marker base = mark();
            while (at(ACCESS_MODIFIERS) || at(VIRTUAL_KEYWORD)) {
                advance();
            }
            if (at(IDENTIFIER) || at(DOUBLE_COLON)) {
                parseQualifiedName();
            }
            else {
                error(CLocalize.identifierExpected());
            }
            consume(ELLIPSIS);
            base.done(CElementTypes.SUPER_CLASS);

            if (!consume(COMMA)) {
                break;
            }
        }
        list.done(CElementTypes.SUPER_CLASS_LIST);
    }

    @Override
    protected boolean isAccessSpecifier() {
        return at(ACCESS_MODIFIERS) && lookAhead(1) == COLON;
    }

    /**
     * {@code friend class X;}
     */
    @Override
    protected boolean parseLanguageMember() {
        if (!at(FRIEND_KEYWORD) || lookAhead(1) != CLASS_KEYWORD && lookAhead(1) != STRUCT_KEYWORD) {
            return false;
        }
        PsiBuilder.Marker marker = mark();
        advance();
        advance();
        parseQualifiedName();
        expect(SEMICOLON, CLocalize.semicolonExpected());
        marker.done(CElementTypes.DECLARATION);
        return true;
    }

    // -----------------------------------------------------------------------
    // Namespace level
    // -----------------------------------------------------------------------

    /**
     * {@code namespace a::b { ... }}, {@code namespace alias = a::b;}
     */
    private void parseNamespace() {
        PsiBuilder.Marker marker = mark();
        advance();
        // namespace [[deprecated]] N { ... }
        parseAttributes();

        if (at(IDENTIFIER)) {
            parseQualifiedName();
        }
        parseAttributes();

        if (consume(EQ)) {
            parseQualifiedName();
            expect(SEMICOLON, CLocalize.semicolonExpected());
            marker.done(CElementTypes.NAMESPACE_DECLARATION);
            return;
        }

        if (expect(LBRACE, CLocalize.lbraceExpected())) {
            while (!eof() && !at(RBRACE)) {
                parseExternalDeclaration();
            }
            expect(RBRACE, CLocalize.rbraceExpected());
        }
        marker.done(CElementTypes.NAMESPACE_DECLARATION);
    }

    /**
     * {@code using namespace a;}, {@code using a::b;}, {@code using T = type;}, {@code using enum E;}
     */
    private void parseUsing() {
        PsiBuilder.Marker marker = mark();
        advance();

        if (consume(NAMESPACE_KEYWORD)) {
            parseQualifiedName();
        }
        else if (consume(ENUM_KEYWORD)) {
            // using enum E;
            parseQualifiedName();
        }
        else {
            consume(TYPENAME_KEYWORD);
            parseQualifiedName();
            if (at(DOUBLE_COLON) && lookAhead(1) == OPERATOR_KEYWORD) {
                // using Base::operator(); using Base::operator new;
                advance();
                parseOperatorName();
            }
            parseAttributes();
            if (consume(EQ)) {
                parseTypeName();
            }
        }
        expect(SEMICOLON, CLocalize.semicolonExpected());
        marker.done(CElementTypes.DECLARATION);
    }

    /**
     * {@code template <parameters> declaration} - the parameters parsed for their extent only.
     */
    private void parseTemplate(Scope scope) {
        advance();
        if (at(LT)) {
            parseTemplateArguments();
        }
        if (isContextualKeyword("requires")) {
            // template<typename T> requires C<T> ...
            advance();
            parseConstraint();
        }
        if (at(TEMPLATE_KEYWORD)) {
            parseTemplate(scope);
            return;
        }
        if (isContextualKeyword("concept")) {
            parseConcept();
            return;
        }
        if (!parseDeclaration(scope == Scope.BLOCK ? Scope.FILE : scope)) {
            error(CLocalize.declarationExpected());
        }
    }

    /**
     * {@code concept Name = constraint;}, after its template header.
     */
    private void parseConcept() {
        PsiBuilder.Marker marker = mark();
        advance();
        expect(IDENTIFIER, CLocalize.identifierExpected());
        if (expect(EQ, CLocalize.eqExpected())) {
            // unlike a requires-clause, a whole expression: concept C = sizeof(T) == 4;
            expressions().parseConditionalExpressionOrError();
        }
        expect(SEMICOLON, CLocalize.semicolonExpected());
        marker.done(CElementTypes.DECLARATION);
    }

    /**
     * The constraint of a requires-clause: primaries joined by {@code &&} and {@code ||}, a name with template
     * arguments taken as such whatever follows it - {@code C<T> && (D<T> || requires { ... })}.
     */
    public void parseConstraint() {
        parseConstraintPrimary();
        while (at(ANDAND) || at(OROR)) {
            advance();
            parseConstraintPrimary();
        }
    }

    private void parseConstraintPrimary() {
        if (consume(EXCL)) {
            parseConstraintPrimary();
        }
        else if (at(LPARENTH)) {
            advance();
            expressions().parseExpressionOrError();
            expect(RPARENTH, CLocalize.rparenthExpected());
        }
        else if (isContextualKeyword("requires")) {
            ((CppExpressionParsing) expressions()).parseRequiresExpression();
        }
        else if (at(IDENTIFIER) || at(DOUBLE_COLON)) {
            PsiBuilder.Marker reference = mark();
            parseQualifiedName();
            reference.done(CElementTypes.REFERENCE_EXPRESSION);
            if (at(LPARENTH)) {
                expressions().parseArgumentList();
            }
        }
        else if (!expressions().parsePrimaryExpression()) {
            error(CLocalize.expressionExpected());
        }
    }

    /**
     * Whether the identifier here is {@code word}, which C++ reserves in context only: {@code requires},
     * {@code concept}, {@code module}...
     */
    public boolean isContextualKeyword(String word) {
        return at(IDENTIFIER) && word.equals(tokenText());
    }

    /**
     * A C++20 module declaration or import: {@code module;}, {@code export module a.b:part;}, {@code module :private;},
     * {@code import a.b;}, {@code import :part;}, {@code import <header>;}. Returns whether there was one.
     */
    private boolean parseModuleDeclaration() {
        boolean module = isContextualKeyword("module");
        boolean importing = isContextualKeyword("import");
        if (!module && !importing) {
            return false;
        }
        IElementType next = lookAhead(1);
        boolean declaration = next == SEMICOLON || next == IDENTIFIER || next == COLON
            || importing && (next == STRING_LITERAL || next == LT);
        if (!declaration) {
            return false;
        }

        PsiBuilder.Marker marker = mark();
        advance();
        if (at(STRING_LITERAL)) {
            advance();
        }
        else if (at(LT)) {
            // import <header>;
            while (!eof() && !at(GT) && !at(SEMICOLON)) {
                advance();
            }
            expect(GT, CLocalize.gtExpected());
        }
        else {
            // a.b.c, then a partition :part or :private
            while (at(IDENTIFIER) || at(DOT) || at(COLON) || at(PRIVATE_KEYWORD)) {
                advance();
            }
        }
        parseAttributes();
        expect(SEMICOLON, CLocalize.semicolonExpected());
        marker.done(CElementTypes.DECLARATION);
        return true;
    }

    /**
     * {@code export} before a module declaration, an import, a declaration or a block of them.
     */
    private boolean parseExport(Scope scope) {
        if (!isContextualKeyword("export")) {
            return false;
        }
        IElementType next = lookAhead(1);
        if (next == SEMICOLON || next == EQ || next == COMMA || next == LPARENTH || next == RPARENTH) {
            // export as a name of its own
            return false;
        }
        advance();
        if (consume(LBRACE)) {
            while (!eof() && !at(RBRACE)) {
                parseExternalDeclaration();
            }
            expect(RBRACE, CLocalize.rbraceExpected());
        }
        else if (!parseModuleDeclaration() && !parseDeclaration(scope)) {
            error(CLocalize.declarationExpected());
        }
        return true;
    }

    /**
     * {@code extern "C" { ... }}, {@code extern "C" declaration}
     */
    private void parseLinkageSpecification(Scope scope) {
        advance();
        advance();

        if (consume(LBRACE)) {
            while (!eof() && !at(RBRACE)) {
                parseExternalDeclaration();
            }
            expect(RBRACE, CLocalize.rbraceExpected());
        }
        else if (!parseDeclaration(scope)) {
            error(CLocalize.declarationExpected());
        }
    }
}
