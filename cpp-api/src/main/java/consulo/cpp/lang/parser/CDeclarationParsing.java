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
import consulo.language.ast.TokenSet;
import consulo.language.parser.PsiBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * C declarations: specifiers, declarators, functions, struct/union/enum bodies, K&R definitions. What C++ adds -
 * namespaces, templates, classes with access sections, qualified names, references - is in
 * {@link CppDeclarationParsing}, through the protected hooks here, whose C behaviour is the base one.
 * <p>
 * A declaration becomes one node per declarator - {@code int a, *b;} is two fields - and the first holds the
 * specifiers, like Java fields do. The node of a declarator is a function definition, a function declaration, a
 * typedef or a variable; its name is its first identifier child.
 *
 * @author VISTALL
 * @since 2026-10-19
 */
public class CDeclarationParsing extends CParsingBase {
    public enum Scope {
        FILE,
        BLOCK,
        MEMBER,
        PARAMETER,
        FOR_INIT,
        /**
         * A C++ condition declaring its variable - {@code if (T x = f())}, {@code while (T x{...})}: one declarator, no
         * semicolon
         */
        CONDITION
    }

    /**
     * What {@link #parseTypeName()} found.
     */
    public enum TypeName {
        NONE,
        /**
         * Certainly a type: it has a type keyword, a qualifier, a tag or a declarator part.
         */
        DEFINITE,
        /**
         * A bare name known to be a type.
         */
        KNOWN_NAME,
        /**
         * A bare name of which nothing is known - {@code (x)} may be a cast or not.
         */
        UNKNOWN_NAME
    }

    protected static final TokenSet AGGREGATE_KEYWORDS = TokenSet.create(STRUCT_KEYWORD, UNION_KEYWORD, ENUM_KEYWORD, CLASS_KEYWORD);

    protected static final TokenSet ATTRIBUTE_KEYWORDS = TokenSet.create(ATTRIBUTE_KEYWORD, DECLSPEC_KEYWORD, ALIGNAS_KEYWORD);

    /**
     * Words standing between the type and the declarator: calling conventions, pointer sizes and nullability, as
     * compilers and their headers write them.
     */
    private static final Set<String> DECLARATOR_QUALIFIERS = Set.of(
        "__cdecl", "_cdecl", "__stdcall", "_stdcall", "__fastcall", "_fastcall", "__thiscall", "__vectorcall", "__clrcall",
        "__pascal", "__ptr32", "__ptr64", "__w64", "__unaligned", "__sptr", "__uptr",
        "_Nonnull", "_Nullable", "_Nullable_result", "_Null_unspecified", "__nonnull", "__nullable", "__null_unspecified"
    );

    /**
     * What a declaration's specifiers held.
     */
    protected static final class Specifiers {
        boolean myTypedef;
        boolean myModifiers;
        boolean myType;
        boolean myStandalone;
    }

    protected static final class Declarator {
        @Nullable String myName;
        boolean myFunction;
        boolean myConsumed;
    }

    public CDeclarationParsing(CParsingContext context) {
        super(context);
    }

    // -----------------------------------------------------------------------
    // File level
    // -----------------------------------------------------------------------

    public void parseFile() {
        while (!eof()) {
            parseExternalDeclaration();
        }
    }

    /**
     * One declaration at file or namespace level; always consumes at least one token.
     */
    protected void parseExternalDeclaration() {
        if (consume(SEMICOLON)) {
            return;
        }

        if (!parseDeclaration(Scope.FILE)) {
            PsiBuilder.Marker error = mark();
            advance();
            errorAt(error, CLocalize.declarationExpected());
        }
    }

    /**
     * Parses a declaration; returns {@code false}, consuming nothing, when there is none here.
     */
    public boolean parseDeclaration(Scope scope) {
        IElementType type = token();

        if (type == STATIC_ASSERT_KEYWORD) {
            parseStaticAssert();
            return true;
        }
        if (type == ASM_KEYWORD) {
            parseAsm();
            return true;
        }
        if (parseLanguageDeclaration(scope)) {
            return true;
        }

        PsiBuilder.Marker node = mark();
        Specifiers specifiers = parseSpecifiers(scope);
        if (specifiers.myStandalone) {
            node.drop();
            return true;
        }

        boolean first = true;
        while (true) {
            Declarator declarator = parseDeclarator(scope, false);

            if (first && !specifiers.myType && !specifiers.myModifiers && declarator.myName == null) {
                node.rollbackTo();
                return false;
            }

            // a type and nothing to declare - friend class X; - declares the type only
            boolean typeOnly = first && specifiers.myType && at(SEMICOLON);
            if (declarator.myName == null && !declarator.myConsumed && !(scope == Scope.MEMBER && at(COLON)) && !typeOnly) {
                error(CLocalize.identifierExpected());
            }

            if (specifiers.myTypedef && declarator.myName != null) {
                myContext.addTypeName(declarator.myName);
            }

            IElementType nodeType = nodeType(scope, specifiers, declarator);

            if (first) {
                parseKnrParameterDeclarations(scope, declarator);
            }

            if (first && declarator.myFunction && (scope == Scope.FILE || scope == Scope.MEMBER) && isFunctionBodyStart()) {
                parseFunctionBody();
                node.done(CElementTypes.IMPLEMENTING_METHOD);
                return true;
            }

            parseDeclaratorTail(scope, declarator);

            if (at(COMMA)) {
                node.done(nodeType);
                advance();
                node = mark();
                first = false;
                continue;
            }

            if (scope == Scope.FOR_INIT && at(COLON)) {
                // for (int x : range) - the caller takes the colon
                node.done(nodeType);
                return true;
            }

            if (scope == Scope.CONDITION) {
                // if (T x = f()) - the caller takes the parenthesis, or the semicolon of if (T x = f(); cond)
                node.done(nodeType);
                return true;
            }

            if (!consume(SEMICOLON)) {
                error(CLocalize.semicolonExpected());
            }
            node.done(nodeType);
            return true;
        }
    }

    /**
     * A declaration form of the language beyond C - a namespace, a template - at {@code scope}; whether there was one.
     */
    protected boolean parseLanguageDeclaration(Scope scope) {
        return false;
    }

    private IElementType nodeType(Scope scope, Specifiers specifiers, Declarator declarator) {
        if (specifiers.myTypedef) {
            return CElementTypes.TYPE_DECLARATION;
        }
        if (declarator.myFunction) {
            if (scope == Scope.MEMBER && !specifiers.myType) {
                return CElementTypes.DECLARATION_CONSTRUCTOR;
            }
            return CElementTypes.DECLARATION_METHOD;
        }
        return switch (scope) {
            case FILE -> CElementTypes.FIELD;
            case MEMBER -> CElementTypes.DECLARATION_FIELD;
            case PARAMETER -> CElementTypes.PARAMETER;
            case BLOCK, FOR_INIT, CONDITION -> CElementTypes.LOCAL_VARIABLE;
        };
    }

    /**
     * After the declarator of a K&R definition: the declarations of its parameters, before the body -
     * {@code int g(i) int i; { ... }}.
     */
    protected void parseKnrParameterDeclarations(Scope scope, Declarator declarator) {
        if (declarator.myFunction && scope == Scope.FILE && !at(SEMICOLON) && !at(COMMA) && !at(LBRACE) && looksLikeDeclaration()) {
            while (!eof() && !at(LBRACE) && looksLikeDeclaration()) {
                parseDeclaration(Scope.BLOCK);
            }
        }
    }

    protected boolean isFunctionBodyStart() {
        return at(LBRACE);
    }

    /**
     * The body of a function definition.
     */
    protected void parseFunctionBody() {
        if (at(LBRACE)) {
            statements().parseCodeBlock();
        }
        else {
            error(CLocalize.lbraceExpected());
        }
    }

    /**
     * After the declarator: a bit-field width, attributes, the initializer.
     */
    private void parseDeclaratorTail(Scope scope, Declarator declarator) {
        if (scope == Scope.MEMBER && !declarator.myFunction && consume(COLON)) {
            expressions().parseConditionalExpressionOrError();
        }

        parseAttributes();

        if (consume(EQ)) {
            parseDeclaratorInitializer();
        }
        else {
            parseBraceInitializer(declarator);
        }
    }

    /**
     * What follows the {@code =} of a declarator.
     */
    protected void parseDeclaratorInitializer() {
        if (!expressions().parseInitializer()) {
            error(CLocalize.expressionExpected());
        }
    }

    /**
     * An initializer without {@code =}, {@code int x{1}}, after the declarator.
     */
    protected void parseBraceInitializer(Declarator declarator) {
    }

    // -----------------------------------------------------------------------
    // Specifiers
    // -----------------------------------------------------------------------

    /**
     * Storage classes, qualifiers and attributes, then the type. The modifiers before the type become a modifier
     * list - always present, so the type stays the second child - except before a struct/union/enum definition with
     * none, which can then be the whole declaration: {@code struct S { ... };}.
     */
    protected Specifiers parseSpecifiers(Scope scope) {
        Specifiers specifiers = new Specifiers();

        PsiBuilder.Marker modifiers = mark();
        parseModifiers(specifiers);

        if (!specifiers.myModifiers && at(AGGREGATE_KEYWORDS)) {
            modifiers.drop();

            boolean standaloneAllowed = scope == Scope.FILE || scope == Scope.BLOCK || scope == Scope.MEMBER;
            specifiers.myStandalone = parseAggregate(standaloneAllowed);
            specifiers.myType = true;
            parseTrailingModifiers();
            return specifiers;
        }

        modifiers.done(CElementTypes.MODIFIER_LIST);

        specifiers.myType = parseTypeSpecifier(scope);
        return specifiers;
    }

    private void parseModifiers(Specifiers specifiers) {
        while (true) {
            if (at(MODIFIERS) && !(at(ATOMIC_KEYWORD) && lookAhead(1) == LPARENTH)) {
                // _Atomic(T) is a type specifier, _Atomic T a qualifier
                advance();
                specifiers.myModifiers = true;
            }
            else if (at(TYPEDEF_KEYWORD)) {
                advance();
                specifiers.myTypedef = true;
                specifiers.myModifiers = true;
            }
            else if (isLanguageModifier()) {
                advance();
                specifiers.myModifiers = true;
            }
            else if (isAttributeStart()) {
                parseAttribute();
                specifiers.myModifiers = true;
            }
            else if (isUnknownMacroModifier()) {
                // a macro whose definition is not visible, in front of the type: API int f();
                advance();
                specifiers.myModifiers = true;
            }
            else {
                return;
            }
        }
    }

    /**
     * A specifier the lexer reads as an identifier.
     */
    protected boolean isLanguageModifier() {
        return false;
    }

    /**
     * An identifier followed by a keyword which can only continue the specifiers - {@code EXPORT int} - is taken for a
     * macro standing for an attribute or a calling convention.
     */
    protected boolean isUnknownMacroModifier() {
        if (!at(IDENTIFIER) || myContext.isTypeName(tokenText())) {
            return false;
        }
        IElementType next = lookAhead(1);
        if (TYPES.contains(next) || MODIFIERS.contains(next) || AGGREGATE_KEYWORDS.contains(next) || next == TYPEDEF_KEYWORD) {
            return true;
        }
        return next == IDENTIFIER && isMacroBeforeNamedType();
    }

    /**
     * At an identifier followed by another: whether it is a macro before a whole type and a declarator name.
     */
    protected boolean isMacroBeforeNamedType() {
        return false;
    }

    protected void parseTrailingModifiers() {
        while (at(MODIFIERS) || isAttributeStart()) {
            if (isAttributeStart()) {
                parseAttribute();
            }
            else {
                advance();
            }
        }
    }

    /**
     * The type part of the specifiers, as a type reference or a struct/union/enum: type keywords combined with each
     * other ({@code unsigned long long int}), or one type name; qualifiers may be mixed in. Returns whether there was
     * a type.
     */
    private boolean parseTypeSpecifier(Scope scope) {
        if (at(AGGREGATE_KEYWORDS)) {
            parseAggregate(false);
            parseTrailingModifiers();
            return true;
        }

        PsiBuilder.Marker typeRef = mark();
        boolean keyword = false;
        boolean name = false;

        while (true) {
            if (at(TYPES)) {
                advance();
                keyword = true;
            }
            else if (!name && isBitInt()) {
                // _BitInt(N), unsigned _BitInt(N)
                advance();
                skipBalanced();
                keyword = true;
            }
            else if (at(MODIFIERS) && (keyword || name)) {
                // int const, char static
                advance();
            }
            else if (isAttributeStart()) {
                parseAttribute();
            }
            else if (!keyword && !name && (at(TYPEOF_KEYWORD) || isParenthesizedTypeSpecifier())) {
                // typeof(e), _Atomic(T)
                advance();
                if (at(LPARENTH)) {
                    skipBalanced();
                }
                name = true;
            }
            else if (!keyword && !name && at(AUTO_KEYWORD)) {
                advance();
                name = true;
            }
            else if (!keyword && !name && parseLanguageTypeSpecifier()) {
                name = true;
            }
            else if (!keyword && !name && isTypeNameStart(scope)) {
                parseQualifiedName();
                name = true;
            }
            else {
                break;
            }
        }

        if (keyword || name) {
            typeRef.done(CElementTypes.TYPE_REF);
            return true;
        }
        typeRef.drop();
        return false;
    }

    /**
     * A type specifier taking its operand in parentheses which the lexer has no keyword for, or which is otherwise a
     * qualifier: {@code _Atomic(T)}.
     */
    protected boolean isParenthesizedTypeSpecifier() {
        return at(ATOMIC_KEYWORD) && lookAhead(1) == LPARENTH;
    }

    /**
     * A type specifier of the language beyond C, where no type has been read yet; whether there was one.
     */
    protected boolean parseLanguageTypeSpecifier() {
        return false;
    }

    /**
     * {@code _BitInt(N)}, a C23 integer of N bits.
     */
    protected boolean isBitInt() {
        return at(IDENTIFIER) && "_BitInt".equals(tokenText()) && lookAhead(1) == LPARENTH;
    }

    /**
     * At an identifier in the specifiers: whether it names the type, or is already the declarator. Without the
     * headers this is decided by what follows - {@code T x}, {@code T *x}, {@code T (*fp)(...)} - and by the type
     * names seen so far.
     */
    protected boolean isTypeNameStart(Scope scope) {
        if (!at(IDENTIFIER)) {
            return false;
        }

        IElementType next = lookAhead(1);
        if (myContext.isTypeName(tokenText())) {
            // a known type, unless it is redeclared: typedef int T; ... int T;
            return next != SEMICOLON && next != EQ && next != COMMA || scope == Scope.PARAMETER;
        }

        if (next == IDENTIFIER || ATTRIBUTE_KEYWORDS.contains(next) || MODIFIERS.contains(next)) {
            return true;
        }
        if (isPointerDeclaratorToken(next)) {
            return true;
        }
        if (next == LPARENTH) {
            IElementType afterParenthesis = lookAhead(2);
            return afterParenthesis == ASTERISK || afterParenthesis == XOR || isMemberPointerAhead(2);
        }
        if (scope == Scope.PARAMETER) {
            // f(T), f(T, U), f(T[])
            return next == COMMA || next == RPARENTH || next == LBRACKET;
        }
        return false;
    }

    /**
     * Whether {@code type} makes a pointer declarator after a type name: {@code T *x}.
     */
    protected boolean isPointerDeclaratorToken(@Nullable IElementType type) {
        return type == ASTERISK;
    }

    /**
     * Whether a name - qualified, where the language has qualified names - starts here.
     */
    protected boolean isQualifiedNameStart() {
        return at(IDENTIFIER);
    }

    /**
     * A name; qualified, where the language has qualified names.
     */
    protected void parseQualifiedName() {
        if (!consume(IDENTIFIER)) {
            error(CLocalize.identifierExpected());
        }
    }

    private boolean isDeclaratorQualifier() {
        return at(IDENTIFIER) && DECLARATOR_QUALIFIERS.contains(tokenText());
    }

    /**
     * Calling conventions and the like before the declarator, int __cdecl f(void); unless one is the declarator name
     * itself, int __cdecl cdecl(...).
     */
    private void skipDeclaratorQualifiers() {
        while (isDeclaratorQualifier() && (lookAhead(1) == IDENTIFIER || lookAhead(1) == ASTERISK || lookAhead(1) == LPARENTH
            || lookAhead(1) == AND || lookAhead(1) == XOR)) {
            advance();
        }
    }

    /**
     * Whether the {@code (} {@code offset} tokens ahead opens a nested declarator: {@code (*}, {@code (^}.
     */
    protected boolean isNestedDeclaratorAt(int offset) {
        IElementType afterParenthesis = lookAhead(offset + 1);
        return afterParenthesis == ASTERISK || afterParenthesis == XOR;
    }

    /**
     * Whether a pointer to member starts {@code offset} tokens ahead; C has none.
     */
    protected boolean isMemberPointerAhead(int offset) {
        return false;
    }

    /**
     * The class of a pointer to member before its {@code *}, {@code Class::}.
     */
    protected void parseMemberPointerClass() {
    }

    // -----------------------------------------------------------------------
    // Declarators
    // -----------------------------------------------------------------------

    /**
     * Pointers, then the name - or a parenthesised declarator - then array and function suffixes.
     */
    protected Declarator parseDeclarator(Scope scope, boolean abstractAllowed) {
        Declarator declarator = new Declarator();

        skipDeclaratorQualifiers();
        if (at(ATTRIBUTE_KEYWORDS)) {
            parseAttributes();
        }
        while (at(XOR) || isPointerDeclaratorToken(token()) || isMemberPointerAhead(0)) {
            if (!at(ASTERISK) && !at(XOR) && !at(AND) && !at(ANDAND)) {
                // a pointer to member, int Class::* p
                parseMemberPointerClass();
            }
            advance();
            declarator.myConsumed = true;
            while (at(POINTER_QUALIFIERS) || isAttributeStart() || isDeclaratorQualifier()) {
                if (isAttributeStart()) {
                    parseAttribute();
                }
                else {
                    advance();
                }
            }
        }

        if (parseLanguageDeclarator(scope, declarator)) {
            return declarator;
        }

        boolean nested = false;
        if (isDeclaratorNameStart()) {
            declarator.myName = parseDeclaratorName();
            declarator.myConsumed = true;
        }
        else if (at(LPARENTH) && isNestedDeclarator()) {
            advance();
            Declarator inner = parseDeclarator(scope, abstractAllowed);
            expect(RPARENTH, CLocalize.rparenthExpected());
            declarator.myName = inner.myName;
            declarator.myConsumed = true;
            nested = true;
        }
        else {
            parsePackDeclarator(declarator);
        }

        boolean firstSuffix = true;
        boolean array = false;
        while (true) {
            if (at(LBRACKET) && lookAhead(1) != LBRACKET) {
                parseArraySuffix();
                array = true;
            }
            else if (at(LPARENTH)) {
                if (parseConstructorArguments(scope, declarator, firstSuffix, nested, array)) {
                    break;
                }
                parseParameterList();
                if (firstSuffix && !nested && declarator.myName != null) {
                    declarator.myFunction = true;
                }
                parseFunctionQualifiers();
            }
            else {
                break;
            }
            declarator.myConsumed = true;
            firstSuffix = false;
        }

        parseAttributes();
        if (at(ASM_KEYWORD)) {
            // int x asm("label");
            parseAsmLabel();
        }
        return declarator;
    }

    /**
     * A whole declarator of a form beyond C, in place of the name; whether there was one.
     */
    protected boolean parseLanguageDeclarator(Scope scope, Declarator declarator) {
        return false;
    }

    protected boolean isDeclaratorNameStart() {
        return at(IDENTIFIER);
    }

    /**
     * A declarator name; returns it.
     */
    protected @Nullable String parseDeclaratorName() {
        String name = tokenText();
        if (!consume(IDENTIFIER)) {
            error(CLocalize.identifierExpected());
            return null;
        }
        return name;
    }

    /**
     * Where a declarator has neither a name nor a nested declarator: a parameter pack of C++, {@code T... args}.
     */
    protected void parsePackDeclarator(Declarator declarator) {
    }

    /**
     * At the {@code (} after a declarator: arguments initializing it rather than parameters, {@code T x(a + b)};
     * whether they were.
     */
    protected boolean parseConstructorArguments(Scope scope, Declarator declarator, boolean firstSuffix, boolean nested, boolean array) {
        return false;
    }

    /**
     * At {@code (} after the pointers: whether it opens a nested declarator - {@code (*fp)} - rather than a parameter
     * list.
     */
    protected boolean isNestedDeclarator() {
        IElementType next = lookAhead(1);
        if (next == ASTERISK || next == XOR) {
            return true;
        }
        if (ATTRIBUTE_KEYWORDS.contains(next)) {
            // auto (__attribute__((pcs("aapcs"))) f()) { ... }
            return true;
        }
        // int (name)(...), a parenthesised name
        return next == IDENTIFIER && lookAhead(2) == RPARENTH && (lookAhead(3) == LPARENTH || lookAhead(3) == LBRACKET);
    }

    private void parseArraySuffix() {
        advance();
        // C99: int a[static 10], int a[const]
        while (at(MODIFIERS)) {
            advance();
        }
        if (!at(RBRACKET)) {
            if (at(ASTERISK) && lookAhead(1) == RBRACKET) {
                advance();
            }
            else {
                expressions().parseAssignmentExpressionOrError();
            }
        }
        expect(RBRACKET, CLocalize.rbracketExpected());
    }

    /**
     * After the parameters of a function: qualifiers of the function itself; C has none.
     */
    protected void parseFunctionQualifiers() {
    }

    /**
     * {@code ( parameter, ... )}
     */
    public void parseParameterList() {
        PsiBuilder.Marker marker = mark();
        advance();

        if (!at(RPARENTH)) {
            while (true) {
                if (at(ELLIPSIS)) {
                    PsiBuilder.Marker parameter = mark();
                    advance();
                    parameter.done(CElementTypes.PARAMETER);
                }
                else if (!parseParameter()) {
                    skipUntil(TokenSet.create(COMMA, RPARENTH, SEMICOLON, LBRACE), CLocalize.typeExpected());
                }

                if (!consume(COMMA)) {
                    break;
                }
            }
        }

        expect(RPARENTH, CLocalize.rparenthExpected());
        marker.done(CElementTypes.PARAMETER_LIST);
    }

    private boolean parseParameter() {
        PsiBuilder.Marker parameter = mark();
        boolean explicitObject = parseExplicitObjectParameter();
        Specifiers specifiers = parseSpecifiers(Scope.PARAMETER);
        Declarator declarator = parseDeclarator(Scope.PARAMETER, true);

        if (!specifiers.myType && !specifiers.myModifiers && !declarator.myConsumed && !explicitObject) {
            parameter.rollbackTo();
            return false;
        }

        if (consume(EQ)) {
            // a C++ default argument
            if (!expressions().parseInitializer()) {
                error(CLocalize.expressionExpected());
            }
        }
        parameter.done(CElementTypes.PARAMETER);
        return true;
    }

    /**
     * The {@code this} of an explicit object parameter; whether there was one.
     */
    protected boolean parseExplicitObjectParameter() {
        return false;
    }

    // -----------------------------------------------------------------------
    // Type names
    // -----------------------------------------------------------------------

    /**
     * A type name, as in a cast or {@code sizeof}: specifiers and an abstract declarator. Reports what it found;
     * consumes nothing when there is no type name here.
     */
    public TypeName parseTypeName() {
        IElementType start = token();
        boolean definite = at(TYPES) || at(MODIFIERS) || at(AGGREGATE_KEYWORDS) || start == TYPEOF_KEYWORD
            || isLanguageTypeNameStart() || isAttributeStart();
        boolean known = start == IDENTIFIER && myContext.isTypeName(tokenText());

        if (!definite && !isQualifiedNameStart()) {
            return TypeName.NONE;
        }

        PsiBuilder.Marker modifiers = mark();
        boolean hasModifiers = false;
        while (at(MODIFIERS) || isAttributeStart()) {
            if (isAttributeStart()) {
                parseAttribute();
            }
            else {
                advance();
            }
            hasModifiers = true;
        }
        if (hasModifiers) {
            modifiers.done(CElementTypes.MODIFIER_LIST);
        }
        else {
            modifiers.drop();
        }

        parseTypeNamePrefix();

        if (at(AGGREGATE_KEYWORDS)) {
            parseAggregate(false);
            parseTrailingModifiers();
        }
        else {
            PsiBuilder.Marker typeRef = mark();
            boolean keyword = false;
            boolean name = false;
            while (true) {
                if (at(TYPES)) {
                    advance();
                    keyword = true;
                }
                else if (!name && isBitInt()) {
                    advance();
                    skipBalanced();
                    keyword = true;
                }
                else if (at(MODIFIERS) && (keyword || name)) {
                    advance();
                }
                else if (!keyword && !name && at(TYPEOF_KEYWORD)) {
                    advance();
                    if (at(LPARENTH)) {
                        skipBalanced();
                    }
                    name = true;
                }
                else if (!keyword && !name && parseLanguageTypeSpecifier()) {
                    name = true;
                }
                else if (!keyword && !name && isQualifiedNameStart()) {
                    parseQualifiedName();
                    name = true;
                }
                else {
                    break;
                }
            }
            if (keyword || name) {
                typeRef.done(CElementTypes.TYPE_REF);
            }
            else {
                typeRef.drop();
            }
        }

        Declarator declarator = parseDeclarator(Scope.PARAMETER, true);
        if (declarator.myName != null) {
            // a type name declares nothing: (a b) is no type
            return TypeName.NONE;
        }
        if (declarator.myConsumed || definite) {
            return TypeName.DEFINITE;
        }
        return known ? TypeName.KNOWN_NAME : TypeName.UNKNOWN_NAME;
    }

    /**
     * Whether a keyword of the language beyond C makes a type name certain here.
     */
    protected boolean isLanguageTypeNameStart() {
        return false;
    }

    /**
     * What a type name of the language beyond C may start with before its specifiers.
     */
    protected void parseTypeNamePrefix() {
    }

    /**
     * Whether a type name certainly starts here - used where an expression could also be, such as a macro
     * argument.
     */
    public boolean startsTypeNameDefinitely() {
        IElementType type = token();
        return at(TYPES) && !isFunctionalCastAhead()
            || at(AGGREGATE_KEYWORDS)
            || type == CONST_KEYWORD || type == VOLATILE_KEYWORD;
    }

    /**
     * At a type keyword: whether it makes a functional cast, {@code int(x)}, {@code int{x}}; C has none.
     */
    protected boolean isFunctionalCastAhead() {
        return false;
    }

    // -----------------------------------------------------------------------
    // Statement level
    // -----------------------------------------------------------------------

    /**
     * At the start of a statement: whether it is a declaration rather than an expression. Certain for a keyword which
     * only starts a declaration; for a name, decided by what follows it - {@code T x}, {@code T *x = ...} - since
     * {@code a * b;} alone does nothing.
     */
    public boolean looksLikeDeclaration() {
        IElementType type = token();
        if (type == EXTENSION_KEYWORD && lookAhead(1) == LPARENTH) {
            // __extension__ ({ ... }) - an expression
            return false;
        }
        if (at(MODIFIERS) || at(TYPES) || at(AGGREGATE_KEYWORDS) || isAttributeStart()
            || type == TYPEDEF_KEYWORD || type == TYPEOF_KEYWORD || type == AUTO_KEYWORD) {
            // int(x) and int{x} are functional casts, int (*fp)() declares
            return !isFunctionalCastAhead() || lookAhead(1) == LPARENTH && isNestedDeclaratorAt(1);
        }

        if (type != IDENTIFIER) {
            return false;
        }

        IElementType next = lookAhead(1);
        if (next == IDENTIFIER) {
            return true;
        }
        if (myContext.isTypeName(tokenText()) && isPointerDeclaratorToken(next)) {
            return true;
        }
        if (isPointerDeclaratorToken(next)) {
            // T *x;  T *x = ...;  T **x, *y;
            int step = 2;
            while (lookAhead(step) == ASTERISK || POINTER_QUALIFIERS.contains(lookAhead(step))) {
                step++;
            }
            if (lookAhead(step) == IDENTIFIER) {
                IElementType after = lookAhead(step + 1);
                return after == SEMICOLON || after == EQ || after == COMMA || after == LBRACKET;
            }
            return false;
        }
        if (myContext.isTypeName(tokenText()) && next == LPARENTH) {
            // T (*fp)(...), T (&r)[2], T (C::*pm)()
            return isNestedDeclaratorAt(1);
        }
        return isUnknownMacroModifier();
    }

    // -----------------------------------------------------------------------
    // Struct, union, enum
    // -----------------------------------------------------------------------

    /**
     * {@code struct Name { members }}, or {@code struct Name} alone - an elaborated type reference. Returns whether
     * it was a whole declaration, {@code struct S { ... };}, which only {@code standaloneAllowed} permits.
     */
    protected boolean parseAggregate(boolean standaloneAllowed) {
        PsiBuilder.Marker marker = mark();
        IElementType keyword = token();
        advance();

        if (keyword == ENUM_KEYWORD && (at(CLASS_KEYWORD) || at(STRUCT_KEYWORD))) {
            // enum class
            advance();
        }
        parseAttributes();

        String name = null;
        if (isQualifiedNameStart()) {
            name = tokenText();
            parseQualifiedName();
        }

        parseAggregateHeadTail(keyword);
        if (keyword == ENUM_KEYWORD && at(COLON)) {
            // the underlying type, enum E : int
            advance();
            parseTypeName();
        }

        if (!at(LBRACE)) {
            if (standaloneAllowed && name != null && at(SEMICOLON)) {
                // a forward declaration, struct S; - or an opaque enum, enum E : int;
                registerAggregateName(name);
                advance();
                marker.done(aggregateNodeType(keyword));
                return true;
            }
            marker.done(CElementTypes.TYPE_REF);
            return false;
        }

        if (keyword == ENUM_KEYWORD) {
            parseEnumBody();
        }
        else {
            parseMemberBody();
        }

        if (name != null) {
            registerAggregateName(name);
        }

        boolean standalone = standaloneAllowed && consume(SEMICOLON);
        marker.done(aggregateNodeType(keyword));
        return standalone;
    }

    /**
     * After the name of a struct, union or enum: what the language adds before the body - a base clause.
     */
    protected void parseAggregateHeadTail(IElementType keyword) {
    }

    /**
     * A declared struct, union or enum: in C its name is a tag, which names no type alone.
     */
    protected void registerAggregateName(String name) {
    }

    private static IElementType aggregateNodeType(IElementType keyword) {
        if (keyword == ENUM_KEYWORD) {
            return CElementTypes.ENUM;
        }
        if (keyword == UNION_KEYWORD) {
            return CElementTypes.UNION_DECLARATION;
        }
        if (keyword == CLASS_KEYWORD) {
            return CElementTypes.CLASS;
        }
        return CElementTypes.STRUCT_DECLARATION;
    }

    private void parseEnumBody() {
        advance();

        while (!eof() && !at(RBRACE)) {
            if (at(IDENTIFIER)) {
                PsiBuilder.Marker constant = mark();
                advance();
                parseAttributes();
                if (consume(EQ)) {
                    expressions().parseConditionalExpressionOrError();
                }
                constant.done(CElementTypes.ENUM_CONSTANT);
            }
            else {
                skipUntil(TokenSet.create(COMMA, RBRACE, SEMICOLON), CLocalize.identifierExpected());
                if (at(SEMICOLON)) {
                    break;
                }
            }

            if (!consume(COMMA)) {
                break;
            }
        }

        expect(RBRACE, CLocalize.rbraceExpected());
    }

    /**
     * The members of a struct, union or class; in a C++ class the members after an access specifier are grouped
     * under it.
     */
    private void parseMemberBody() {
        advance();

        while (!eof() && !at(RBRACE)) {
            if (isAccessSpecifier()) {
                PsiBuilder.Marker section = mark();
                advance();
                advance();
                while (!eof() && !at(RBRACE) && !isAccessSpecifier()) {
                    parseMember();
                }
                section.done(CElementTypes.ACCESSOR_OWNER);
            }
            else {
                parseMember();
            }
        }

        expect(RBRACE, CLocalize.rbraceExpected());
    }

    /**
     * {@code public:} - which C has not.
     */
    protected boolean isAccessSpecifier() {
        return false;
    }

    private void parseMember() {
        if (consume(SEMICOLON)) {
            return;
        }
        if (parseLanguageMember()) {
            return;
        }
        if (!parseDeclaration(Scope.MEMBER)) {
            PsiBuilder.Marker error = mark();
            advance();
            errorAt(error, CLocalize.declarationExpected());
        }
    }

    /**
     * A member form of the language beyond C; whether there was one.
     */
    protected boolean parseLanguageMember() {
        return false;
    }

    // -----------------------------------------------------------------------
    // Attributes, static_assert, asm
    // -----------------------------------------------------------------------

    public boolean isAttributeStart() {
        // [[...]] is C++11 and C23; in older C nothing else starts with [[ either
        return at(ATTRIBUTE_KEYWORDS) || at(LBRACKET) && lookAhead(1) == LBRACKET;
    }

    public void parseAttributes() {
        while (isAttributeStart()) {
            parseAttribute();
        }
    }

    /**
     * {@code __attribute__((...))}, {@code __declspec(...)}, {@code alignas(...)}, {@code [[...]]}
     */
    public void parseAttribute() {
        PsiBuilder.Marker marker = mark();
        if (at(LBRACKET)) {
            skipBalanced();
        }
        else {
            advance();
            if (at(LPARENTH)) {
                skipBalanced();
            }
        }
        marker.done(CElementTypes.ATTRIBUTE_LIST);
    }

    /**
     * {@code static_assert(condition, "message");}
     */
    public void parseStaticAssert() {
        PsiBuilder.Marker marker = mark();
        advance();

        if (expect(LPARENTH, CLocalize.lparenthExpected())) {
            expressions().parseConditionalExpressionOrError();
            if (consume(COMMA)) {
                expressions().parseAssignmentExpressionOrError();
            }
            expect(RPARENTH, CLocalize.rparenthExpected());
        }
        expect(SEMICOLON, CLocalize.semicolonExpected());
        marker.done(CElementTypes.STATIC_ASSERT);
    }

    /**
     * {@code asm volatile ("..." : outputs : inputs : clobbers);}
     */
    public void parseAsm() {
        PsiBuilder.Marker marker = mark();
        advance();

        // volatile, inline, goto
        while (at(VOLATILE_KEYWORD) || at(INLINE_KEYWORD) || at(GOTO_KEYWORD)) {
            advance();
        }
        if (at(LPARENTH)) {
            advance();
            parseAsmTemplate();
            // operands, clobbers and goto labels, up to the closing parenthesis
            while (!eof() && !at(RPARENTH)) {
                if (at(LPARENTH) || at(LBRACKET) || at(LBRACE)) {
                    skipBalanced();
                }
                else {
                    advance();
                }
            }
            consume(RPARENTH);
        }
        else if (at(LBRACE)) {
            // MSVC, __asm { ... }
            PsiBuilder.Marker block = mark();
            skipBalanced();
            block.done(CElementTypes.ASM_BLOCK);
        }
        consume(SEMICOLON);
        marker.done(CElementTypes.ASM);
    }

    /**
     * The assembly of {@code asm("...")}: adjacent string literals, and the macros among them which expand to more of
     * them - {@code LOCK_PREFIX "xadd %0, %1"}.
     */
    private void parseAsmTemplate() {
        if (!at(STRING_LITERAL) && !at(IDENTIFIER)) {
            return;
        }
        PsiBuilder.Marker template = mark();
        while (at(STRING_LITERAL) || at(IDENTIFIER)) {
            advance();
        }
        template.done(CElementTypes.ASM_TEMPLATE);
    }

    private void parseAsmLabel() {
        PsiBuilder.Marker marker = mark();
        advance();
        if (at(LPARENTH)) {
            skipBalanced();
        }
        marker.done(CElementTypes.ASM);
    }
}
