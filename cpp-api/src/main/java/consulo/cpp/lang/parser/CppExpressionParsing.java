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

/**
 * C++ expressions, on top of the C ones: qualified names with template arguments, temporaries, lambdas, {@code new}
 * and {@code delete}, casts by keyword, {@code throw}, pointers to members, operator functions by name, pack
 * expansions and folds, coroutine operators, requires-expressions.
 *
 * @author VISTALL
 * @since 2026-10-25
 */
public class CppExpressionParsing extends CExpressionParsing {
    /**
     * What may follow {@code &&} inside template arguments, where it is an rvalue reference - {@code T&&},
     * {@code Args&&...} - rather than a logical and.
     */
    private static final TokenSet RVALUE_REFERENCE_FOLLOWERS = TokenSet.create(GT, GTGT, COMMA, ELLIPSIS, RPARENTH);

    /**
     * Tokens which cannot start an operand: after {@code a < b >} they make the comparison incomplete, so the
     * {@code <...>} were template arguments - {@code lib::test<foo>)}, {@code x<int> == 0}.
     */
    private static final TokenSet NO_OPERAND_STARTS = TokenSet.create(RPARENTH, COMMA, SEMICOLON, RBRACKET, RBRACE, EQEQ, NE, OROR,
        ANDAND, QUEST, COLON, EQ, DOT, ARROW);

    public CppExpressionParsing(CParsingContext context) {
        super(context);
    }

    private CppDeclarationParsing cpp() {
        return (CppDeclarationParsing) declarations();
    }

    @Override
    public boolean parseAssignmentExpression() {
        if (at(THROW_KEYWORD)) {
            PsiBuilder.Marker marker = mark();
            advance();
            if (!at(SEMICOLON) && !at(RPARENTH) && !at(COMMA)) {
                parseAssignmentExpression();
            }
            marker.done(CElementTypes.PREFIX_EXPRESSION);
            return true;
        }
        return super.parseAssignmentExpression();
    }

    @Override
    protected void advanceBinaryOperator() {
        if (at(LE) && lookAhead(1) == GT) {
            // a <=> b, which the lexer reads as <= and >
            advance();
        }
        advance();
    }

    /**
     * {@code (void)[&] { ... }}: a lambda after a type which is certainly one.
     */
    @Override
    protected boolean isLanguageCastOperandStart() {
        return at(LBRACKET);
    }

    /**
     * {@code sizeof...(pack)}
     */
    @Override
    protected void parseSizeofPack() {
        consume(ELLIPSIS);
    }

    @Override
    protected boolean parseLanguageUnaryExpression() {
        IElementType type = token();

        if (type == IDENTIFIER && ("co_await".equals(tokenText()) || "co_yield".equals(tokenText()))) {
            // coroutine operators: the lexer knows no such keywords
            PsiBuilder.Marker marker = mark();
            advance();
            if (at(LBRACE)) {
                parseInitializerList();
            }
            else if (!parseAssignmentExpression()) {
                error(CLocalize.expressionExpected());
            }
            marker.done(CElementTypes.PREFIX_EXPRESSION);
            return true;
        }

        if (type == NEW_KEYWORD || type == DOUBLE_COLON && lookAhead(1) == NEW_KEYWORD) {
            parseNewExpression();
            return true;
        }

        if (type == DELETE_KEYWORD || type == DOUBLE_COLON && lookAhead(1) == DELETE_KEYWORD) {
            PsiBuilder.Marker marker = mark();
            consume(DOUBLE_COLON);
            advance();
            if (at(LBRACKET) && lookAhead(1) == RBRACKET) {
                advance();
                advance();
            }
            if (!parseCastExpression()) {
                error(CLocalize.expressionExpected());
            }
            marker.done(CElementTypes.PREFIX_EXPRESSION);
            return true;
        }
        return false;
    }

    private void parseNewExpression() {
        PsiBuilder.Marker marker = mark();
        consume(DOUBLE_COLON);
        advance();

        // placement arguments: new (buffer) T
        if (at(LPARENTH)) {
            parseArgumentList();
        }

        declarations().parseTypeName();

        if (at(LPARENTH)) {
            parseArgumentList();
        }
        else if (at(LBRACE)) {
            parseInitializerList();
        }
        marker.done(CElementTypes.NEW_EXPRESSION);
    }

    /**
     * Also pointers to members - {@code object.*pm}, {@code pointer->*pm} - and member access by a qualified name, a
     * template or an operator name: {@code p->Base<T>::member}, {@code t.template get<1>()}, {@code f.operator()(0)}.
     */
    @Override
    protected @Nullable IElementType parsePostfixSuffix() {
        if ((at(DOT) || at(ARROW)) && lookAhead(1) == ASTERISK) {
            advance();
            advance();
            if (!parseCastExpression()) {
                error(CLocalize.expressionExpected());
            }
            return CElementTypes.BINARY_EXPRESSION;
        }
        if (at(DOT) || at(ARROW)) {
            advance();
            boolean explicit = consume(TEMPLATE_KEYWORD);
            if (at(OPERATOR_KEYWORD)) {
                // f.operator()(0), l.template operator()<3>()
                cpp().parseOperatorName();
                parseTemplateArgumentsIfAhead(explicit);
                return CElementTypes.REFERENCE_EXPRESSION;
            }
            consume(TILDE);
            if (expect(IDENTIFIER, CLocalize.nameExpected())) {
                // t.template get<1>(), any.cast<int>()
                parseTemplateArgumentsIfAhead(explicit);
                // a qualified member, p->Base<T>::member
                while (at(DOUBLE_COLON) && (lookAhead(1) == IDENTIFIER || lookAhead(1) == TILDE || lookAhead(1) == TEMPLATE_KEYWORD)) {
                    advance();
                    boolean explicitPart = consume(TEMPLATE_KEYWORD);
                    consume(TILDE);
                    advance();
                    parseTemplateArgumentsIfAhead(explicitPart);
                }
            }
            return CElementTypes.REFERENCE_EXPRESSION;
        }
        return super.parsePostfixSuffix();
    }

    /**
     * Also a braced list, {@code m[{1, 2}]}.
     */
    @Override
    protected void parseIndex() {
        if (at(LBRACE)) {
            parseInitializerList();
        }
        else {
            super.parseIndex();
        }
    }

    @Override
    public boolean parsePrimaryExpression() {
        IElementType type = token();

        if (type == ELLIPSIS) {
            // the pack of a fold expression, (args + ...), (... && args)
            advance();
            return true;
        }

        if (type == OPERATOR_KEYWORD || type == DOUBLE_COLON && lookAhead(1) == OPERATOR_KEYWORD) {
            // an operator function by name, operator new(128), ::operator delete[](p)
            PsiBuilder.Marker marker = mark();
            consume(DOUBLE_COLON);
            cpp().parseOperatorName();
            // ::operator==<0, int>(a, b)
            parseTemplateArgumentsIfAhead(false);
            marker.done(CElementTypes.REFERENCE_EXPRESSION);
            return true;
        }

        if (cpp().isContextualKeyword("requires")) {
            parseRequiresExpression();
            return true;
        }

        if (type == TYPENAME_KEYWORD) {
            // a temporary of a dependent type, typename T::X{} or typename T::X(a)
            PsiBuilder.Marker marker = mark();
            advance();
            cpp().parseQualifiedName();
            if (at(LBRACE)) {
                parseInitializerList();
            }
            else if (at(LPARENTH)) {
                parseArgumentList();
            }
            marker.done(CElementTypes.CAST_EXPRESSION);
            return true;
        }

        if (type == IDENTIFIER || type == DOUBLE_COLON && lookAhead(1) == IDENTIFIER) {
            PsiBuilder.Marker reference = parseReference();
            if (at(LBRACE)) {
                // a temporary, S{}, std::vector<int>{1, 2}
                PsiBuilder.Marker marker = reference.precede();
                parseInitializerList();
                marker.done(CElementTypes.CAST_EXPRESSION);
            }
            return true;
        }

        if (type == THIS_KEYWORD) {
            PsiBuilder.Marker marker = mark();
            advance();
            marker.done(CElementTypes.REFERENCE_EXPRESSION);
            return true;
        }

        if (type == CAST_KEYWORD) {
            // static_cast<T>(e)
            PsiBuilder.Marker marker = mark();
            advance();
            if (expect(LT, CLocalize.ltExpected())) {
                declarations().parseTypeName();
                expect(GT, CLocalize.gtExpected());
            }
            if (at(LPARENTH)) {
                advance();
                parseExpressionOrError();
                expect(RPARENTH, CLocalize.rparenthExpected());
            }
            marker.done(CElementTypes.CAST_EXPRESSION);
            return true;
        }

        if (type == TYPEID_KEYWORD || type == NOEXCEPT_KEYWORD || type == DECLTYPE_KEYWORD) {
            // typeid(x), noexcept(e), decltype(e): parsed for their extent only
            PsiBuilder.Marker marker = mark();
            advance();
            if (at(LPARENTH)) {
                skipBalanced();
            }
            marker.done(CElementTypes.CALL_EXPRESSION);
            while (type == DECLTYPE_KEYWORD && at(DOUBLE_COLON) && lookAhead(1) == IDENTIFIER) {
                // decltype(S())::value
                marker = marker.precede();
                advance();
                advance();
                marker.done(CElementTypes.REFERENCE_EXPRESSION);
            }
            return true;
        }

        if (type == LBRACE) {
            parseInitializerList();
            return true;
        }

        if (type == LBRACKET) {
            return parseLambda();
        }

        if (at(TYPES) && (lookAhead(1) == LPARENTH || lookAhead(1) == LBRACE)) {
            // functional cast, int(x)
            PsiBuilder.Marker marker = mark();
            advance();
            if (at(LPARENTH)) {
                parseArgumentList();
            }
            else {
                parseInitializerList();
            }
            marker.done(CElementTypes.CAST_EXPRESSION);
            return true;
        }

        return super.parsePrimaryExpression();
    }

    /**
     * {@code requires (parameters) { requirements }}, parsed for its extent: the requirements are expressions,
     * {@code typename} names and compound requirements, {@code { e } -> C<T>;}.
     */
    public void parseRequiresExpression() {
        PsiBuilder.Marker marker = mark();
        advance();
        if (at(LPARENTH)) {
            declarations().parseParameterList();
        }
        if (at(LBRACE)) {
            skipBalanced();
        }
        else {
            error(CLocalize.lbraceExpected());
        }
        marker.done(CElementTypes.CALL_EXPRESSION);
    }

    /**
     * A qualified name: {@code a}, {@code ::a}, {@code ns::a::b}, each part with its template arguments -
     * {@code std::numeric_limits<int>::max}, {@code std::use_facet<F>}. Returns the outermost reference.
     */
    private PsiBuilder.Marker parseReference() {
        PsiBuilder.Marker marker = mark();
        consume(DOUBLE_COLON);
        advance();
        parseTemplateArgumentsIfAhead(false);
        marker.done(CElementTypes.REFERENCE_EXPRESSION);

        while (at(DOUBLE_COLON)
            && (lookAhead(1) == IDENTIFIER || lookAhead(1) == TILDE || lookAhead(1) == TEMPLATE_KEYWORD || lookAhead(1) == OPERATOR_KEYWORD)) {
            marker = marker.precede();
            advance();
            if (at(OPERATOR_KEYWORD)) {
                // &Fn::operator(), s::operator struct v
                cpp().parseOperatorName();
                marker.done(CElementTypes.REFERENCE_EXPRESSION);
                break;
            }
            boolean explicit = consume(TEMPLATE_KEYWORD);
            consume(TILDE);
            advance();
            parseTemplateArgumentsIfAhead(explicit);
            marker.done(CElementTypes.REFERENCE_EXPRESSION);
        }
        return marker;
    }

    /**
     * Template arguments after a name in an expression, when {@code <} opens them; {@code explicit} after the
     * {@code template} keyword, which says it does.
     */
    private boolean parseTemplateArgumentsIfAhead(boolean explicit) {
        if (!at(LT) || !explicit && !isTemplateArgumentsAhead()) {
            return false;
        }
        cpp().parseTemplateArguments();
        return true;
    }

    /**
     * At {@code <} after a name: whether it opens template arguments - {@code std::get<0>(t)},
     * {@code std::numeric_limits<int>::max()}, {@code std::vector<int>{1, 2}} - rather than a comparison. Which names
     * are templates is not known here, so the arguments must close before anything only an expression holds at their
     * level - {@code || ? ;}, a logical {@code &&}, an unbalanced {@code >>} - and be followed by what only follows a
     * template name: a call, a scope, a braced list - or anything when the arguments hold what no comparison does,
     * {@code is_same<int, T>}, {@code is_same<list<A>, B>}. {@code a < b && c > (d)} stays a comparison.
     */
    private boolean isTemplateArgumentsAhead() {
        PsiBuilder.Marker speculation = mark();
        try {
            int depth = 0;
            // what no comparison holds: a nested argument list, a type keyword which is no functional cast
            boolean typeEvidence = false;
            do {
                IElementType type = token();
                if (type == LT) {
                    typeEvidence |= depth > 0;
                    depth++;
                }
                else if (at(TYPES) && lookAhead(1) != LPARENTH && lookAhead(1) != LBRACE) {
                    typeEvidence = true;
                }
                else if (type == GT) {
                    depth--;
                }
                else if (type == GTGT) {
                    depth -= 2;
                    if (depth < 0) {
                        return false;
                    }
                }
                else if (type == LPARENTH || type == LBRACKET) {
                    skipBalanced();
                    continue;
                }
                else if (type == OROR || type == QUEST || type == SEMICOLON || type == LBRACE || type == RBRACE
                    || type == RPARENTH || type == RBRACKET
                    || type == ANDAND && !RVALUE_REFERENCE_FOLLOWERS.contains(lookAhead(1))) {
                    // the end of what encloses the comparison, or what only an expression holds
                    return false;
                }
                advance();
            }
            while (depth > 0 && !eof());
            if (depth != 0) {
                return false;
            }
            // (std::is_same<mp_list<A>, B>): arguments which can only be types may end the operand
            return at(LPARENTH) || at(DOUBLE_COLON) || at(LBRACE) || typeEvidence || at(NO_OPERAND_STARTS) || eof();
        }
        finally {
            speculation.rollbackTo();
        }
    }

    /**
     * {@code [captures] <template parameters> (parameters) specifiers -> type { body }}
     */
    private boolean parseLambda() {
        PsiBuilder.Marker marker = mark();
        skipBalanced();
        if (at(LT)) {
            // a template lambda, []<typename T>(T t) { ... }
            cpp().parseTemplateArguments();
            if (cpp().isContextualKeyword("requires")) {
                advance();
                cpp().parseConstraint();
            }
        }
        // [=] [[attribute]] (int x) __attribute__((...)) mutable noexcept -> int { ... }
        declarations().parseAttributes();
        if (at(LPARENTH)) {
            declarations().parseParameterList();
        }
        while (at(MODIFIERS) || at(NOEXCEPT_KEYWORD) || at(IDENTIFIER) || declarations().isAttributeStart()) {
            // mutable, constexpr, noexcept
            if (declarations().isAttributeStart()) {
                declarations().parseAttribute();
            }
            else {
                advance();
                if (at(LPARENTH)) {
                    // noexcept(...)
                    skipBalanced();
                }
            }
        }
        if (consume(ARROW)) {
            declarations().parseTypeName();
        }
        if (!at(LBRACE)) {
            marker.rollbackTo();
            return false;
        }
        statements().parseCodeBlock();
        marker.done(CElementTypes.CALL_EXPRESSION);
        return true;
    }

    /**
     * {@code f(args...)}, {@code {args...}}
     */
    @Override
    protected void parsePackExpansion() {
        consume(ELLIPSIS);
    }

    /**
     * A braced list, {@code f({1, 2})}.
     */
    @Override
    protected boolean parseLanguageArgument() {
        if (at(LBRACE)) {
            parseInitializerList();
            return true;
        }
        return false;
    }

    /**
     * Whether a type which can be nothing else makes the whole argument, as builtins taking types have them:
     * {@code __is_constructible(T, T&&)}, {@code f(int (*)(int))}. It must have a pointer or reference declarator: a
     * call {@code g(x)}, an index {@code p[1]} or a pack expansion {@code args...} read as types too.
     */
    @Override
    protected boolean isTypeArgumentAhead() {
        if (at(TYPES) && (lookAhead(1) == LPARENTH || lookAhead(1) == LBRACE)) {
            // a functional cast, int(x)
            return false;
        }
        int end;
        int errors = errorCount();
        PsiBuilder.Marker speculation = mark();
        try {
            if (declarations().parseTypeName() != CDeclarationParsing.TypeName.DEFINITE || !at(COMMA) && !at(RPARENTH)
                || errorCount() != errors) {
                return false;
            }
            end = myBuilder.getCurrentOffset();
        }
        finally {
            speculation.rollbackTo();
        }

        // the pointer must be the type's own, not one inside what was read as its parameters: g(x, &y) is a call
        PsiBuilder.Marker scan = mark();
        try {
            while (!eof() && myBuilder.getCurrentOffset() < end) {
                if (at(ASTERISK) || at(AND) || at(ANDAND)) {
                    return true;
                }
                if (at(LPARENTH)) {
                    IElementType next = lookAhead(1);
                    if (next == ASTERISK || next == AND || next == ANDAND || next == XOR || cpp().isMemberPointerAhead(1)) {
                        // a nested declarator, int (*)(int), void (A::*)()
                        return true;
                    }
                    skipBalanced();
                }
                else if (at(LBRACKET)) {
                    skipBalanced();
                }
                else if (at(LT)) {
                    cpp().parseTemplateArguments();
                }
                else {
                    advance();
                }
            }
            return false;
        }
        finally {
            scan.rollbackTo();
        }
    }
}
