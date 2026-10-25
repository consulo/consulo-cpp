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
 * C expressions, by precedence: comma, assignment, conditional, the binary operators, cast, unary, postfix, primary.
 * What C++ adds - template arguments, lambdas, {@code new}, casts by keyword, pack expansions - is in
 * {@link CppExpressionParsing}, through the protected hooks here, whose C behaviour is the base one.
 *
 * @author VISTALL
 * @since 2026-10-19
 */
public class CExpressionParsing extends CParsingBase {
    /**
     * Binary operators, loosest binding first.
     */
    private static final TokenSet[] BINARY_LEVELS = {
        TokenSet.create(OROR),
        TokenSet.create(ANDAND),
        TokenSet.create(OR),
        TokenSet.create(XOR),
        TokenSet.create(AND),
        EQUALITY_OPERATIONS,
        RELATIONAL_OPERATIONS,
        SHIFT_OPERATIONS,
        ADDITIVE_OPERATIONS,
        MULTIPLICATIVE_OPERATIONS
    };

    /**
     * Tokens which can start the operand of a cast whatever the parenthesised name is - none of them could continue
     * a parenthesised expression instead.
     */
    private static final TokenSet UNAMBIGUOUS_OPERAND_STARTS = TokenSet.orSet(
        TokenSet.create(IDENTIFIER, LPARENTH, EXCL, TILDE, SIZEOF_KEYWORD, ALIGNOF_KEYWORD, THIS_KEYWORD, NEW_KEYWORD,
            DELETE_KEYWORD, CAST_KEYWORD),
        LITERAL_EXPRESSION_SET
    );

    /**
     * Tokens which can start the operand of a cast only when the parenthesised name is known to be a type:
     * {@code (x) - 1} subtracts, {@code (int) -1} casts.
     */
    private static final TokenSet AMBIGUOUS_OPERAND_STARTS = TokenSet.create(PLUS, MINUS, ASTERISK, AND, PLUSPLUS, MINUSMINUS, DOUBLE_COLON);

    /**
     * What may follow a name continuing a string literal: another literal, or the end of the operand.
     */
    private static final TokenSet STRING_MACRO_FOLLOWERS = TokenSet.create(STRING_LITERAL, RPARENTH, COMMA, SEMICOLON, RBRACKET, RBRACE, COLON);

    public CExpressionParsing(CParsingContext context) {
        super(context);
    }

    /**
     * {@code assignment (, assignment)*}
     */
    public boolean parseExpression() {
        PsiBuilder.Marker marker = mark();
        if (!parseAssignmentExpression()) {
            marker.drop();
            return false;
        }

        while (at(COMMA)) {
            advance();
            if (!parseAssignmentExpression()) {
                error(CLocalize.expressionExpected());
            }
            marker.done(CElementTypes.COMMA_EXPRESSION);
            marker = marker.precede();
        }
        marker.drop();
        return true;
    }

    public void parseExpressionOrError() {
        if (!parseExpression()) {
            error(CLocalize.expressionExpected());
        }
    }

    public boolean parseAssignmentExpression() {
        PsiBuilder.Marker marker = mark();
        if (!parseConditionalExpression()) {
            marker.drop();
            return false;
        }

        if (at(ASSIGNMENT_OPERATIONS)) {
            advance();
            if (!parseInitializer()) {
                error(CLocalize.expressionExpected());
            }
            marker.done(CElementTypes.ASSIGNMENT_EXPRESSION);
        }
        else {
            marker.drop();
        }
        return true;
    }

    public void parseAssignmentExpressionOrError() {
        if (!parseAssignmentExpression()) {
            error(CLocalize.expressionExpected());
        }
    }

    public boolean parseConditionalExpression() {
        PsiBuilder.Marker marker = mark();
        if (!parseBinaryExpression(0)) {
            marker.drop();
            return false;
        }

        if (at(QUEST)) {
            advance();
            // GNU: a ?: b
            if (!at(COLON)) {
                parseExpressionOrError();
            }
            expect(COLON, CLocalize.colonExpected());
            parseAssignmentExpressionOrError();
            marker.done(CElementTypes.CONDITIONAL_EXPRESSION);
        }
        else {
            marker.drop();
        }
        return true;
    }

    public void parseConditionalExpressionOrError() {
        if (!parseConditionalExpression()) {
            error(CLocalize.expressionExpected());
        }
    }

    private boolean parseBinaryExpression(int level) {
        if (level == BINARY_LEVELS.length) {
            return parseCastExpression();
        }

        PsiBuilder.Marker marker = mark();
        if (!parseBinaryExpression(level + 1)) {
            marker.drop();
            return false;
        }

        while (at(BINARY_LEVELS[level])) {
            advanceBinaryOperator();
            if (!parseBinaryExpression(level + 1)) {
                error(CLocalize.expressionExpected());
            }
            marker.done(CElementTypes.BINARY_EXPRESSION);
            marker = marker.precede();
        }
        marker.drop();
        return true;
    }

    /**
     * Past a binary operator, which may take more than one token.
     */
    protected void advanceBinaryOperator() {
        advance();
    }

    public boolean parseCastExpression() {
        if (at(LPARENTH) && isCast()) {
            PsiBuilder.Marker marker = mark();
            advance();
            declarations().parseTypeName();
            expect(RPARENTH, CLocalize.rparenthExpected());

            // a compound literal, (struct point) { 1, 2 }
            if (at(LBRACE)) {
                parseInitializerList();
            }
            else if (!parseCastExpression()) {
                error(CLocalize.expressionExpected());
            }
            marker.done(CElementTypes.CAST_EXPRESSION);
            return true;
        }
        return parseUnaryExpression();
    }

    /**
     * At {@code (}: whether a cast follows rather than a parenthesised expression. Decided by reading ahead: the
     * parentheses must hold a type name, and what follows must be able to start the operand.
     */
    private boolean isCast() {
        PsiBuilder.Marker speculation = mark();
        try {
            advance();
            CDeclarationParsing.TypeName typeName = declarations().parseTypeName();
            if (typeName == CDeclarationParsing.TypeName.NONE || !at(RPARENTH)) {
                return false;
            }
            advance();

            if (typeName == CDeclarationParsing.TypeName.UNKNOWN_NAME) {
                return at(UNAMBIGUOUS_OPERAND_STARTS);
            }
            return at(LBRACE) || at(UNAMBIGUOUS_OPERAND_STARTS) || at(AMBIGUOUS_OPERAND_STARTS) || isLanguageCastOperandStart();
        }
        finally {
            speculation.rollbackTo();
        }
    }

    /**
     * After a parenthesised type which is certainly one: whether a form of operand of the language beyond C starts.
     */
    protected boolean isLanguageCastOperandStart() {
        return false;
    }

    public boolean parseUnaryExpression() {
        IElementType type = token();

        if (type == SIZEOF_KEYWORD || type == ALIGNOF_KEYWORD) {
            PsiBuilder.Marker marker = mark();
            advance();
            parseSizeofPack();
            if (at(LPARENTH) && isParenthesizedTypeName()) {
                advance();
                declarations().parseTypeName();
                expect(RPARENTH, CLocalize.rparenthExpected());
            }
            else if (!parseUnaryExpression()) {
                error(CLocalize.expressionExpected());
            }
            marker.done(CElementTypes.SIZEOF_EXPRESSION);
            return true;
        }

        if (at(UNARY_OPERATIONS) || type == ANDAND) {
            // ANDAND: GNU address of a label, &&label
            PsiBuilder.Marker marker = mark();
            advance();
            if (!parseCastExpression()) {
                error(CLocalize.expressionExpected());
            }
            marker.done(CElementTypes.PREFIX_EXPRESSION);
            return true;
        }

        if (parseLanguageUnaryExpression()) {
            return true;
        }

        if (type == EXTENSION_KEYWORD) {
            PsiBuilder.Marker marker = mark();
            advance();
            if (!parseCastExpression()) {
                error(CLocalize.expressionExpected());
            }
            marker.done(CElementTypes.PREFIX_EXPRESSION);
            return true;
        }

        return parsePostfixExpression();
    }

    /**
     * The {@code ...} of a C++ {@code sizeof...(pack)}.
     */
    protected void parseSizeofPack() {
    }

    /**
     * A unary expression of the language beyond C; whether there was one.
     */
    protected boolean parseLanguageUnaryExpression() {
        return false;
    }

    private boolean isParenthesizedTypeName() {
        PsiBuilder.Marker speculation = mark();
        int errors = errorCount();
        try {
            advance();
            CDeclarationParsing.TypeName typeName = declarations().parseTypeName();
            // sizeof(check<T>(0)) is a call: a type which needed an error to read is none
            return typeName != CDeclarationParsing.TypeName.NONE && typeName != CDeclarationParsing.TypeName.UNKNOWN_NAME
                && at(RPARENTH) && errorCount() == errors;
        }
        finally {
            speculation.rollbackTo();
        }
    }

    public boolean parsePostfixExpression() {
        PsiBuilder.Marker marker = mark();
        if (!parsePrimaryExpression()) {
            marker.drop();
            return false;
        }

        while (true) {
            IElementType suffix = parsePostfixSuffix();
            if (suffix == null) {
                break;
            }
            marker.done(suffix);
            marker = marker.precede();
        }
        marker.drop();
        return true;
    }

    /**
     * One suffix of a postfix expression - an index, a call, a member access, {@code ++} - returning the type of the
     * expression it makes, or {@code null} when there is none.
     */
    protected @Nullable IElementType parsePostfixSuffix() {
        if (at(LBRACKET)) {
            advance();
            parseIndex();
            expect(RBRACKET, CLocalize.rbracketExpected());
            return CElementTypes.INDEXED_PROPERTY_ACCESS_EXPRESSION;
        }
        if (at(LPARENTH)) {
            parseArgumentList();
            return CElementTypes.CALL_EXPRESSION;
        }
        if (at(DOT) || at(ARROW)) {
            advance();
            expect(IDENTIFIER, CLocalize.nameExpected());
            return CElementTypes.REFERENCE_EXPRESSION;
        }
        if (at(PLUSPLUS) || at(MINUSMINUS)) {
            advance();
            return CElementTypes.POSTFIX_EXPRESSION;
        }
        return null;
    }

    /**
     * The index between {@code [} and {@code ]}.
     */
    protected void parseIndex() {
        parseExpressionOrError();
    }

    public boolean parsePrimaryExpression() {
        IElementType type = token();

        if (type == IDENTIFIER) {
            PsiBuilder.Marker marker = mark();
            advance();
            marker.done(CElementTypes.REFERENCE_EXPRESSION);
            return true;
        }

        if (type == STRING_LITERAL) {
            // adjacent string literals are one string; a name among them can only be a string macro whose definition
            // is not visible - "version " VERSION "\n"
            PsiBuilder.Marker marker = mark();
            while (at(STRING_LITERAL) || at(IDENTIFIER) && STRING_MACRO_FOLLOWERS.contains(lookAhead(1))) {
                advance();
            }
            marker.done(CElementTypes.LITERAL_EXPRESSION);
            return true;
        }

        if (at(LITERAL_EXPRESSION_SET)) {
            PsiBuilder.Marker marker = mark();
            advance();
            marker.done(CElementTypes.LITERAL_EXPRESSION);
            return true;
        }

        if (type == LPARENTH) {
            PsiBuilder.Marker marker = mark();
            advance();
            if (at(LBRACE)) {
                // GNU statement expression, ({ ... })
                statements().parseCodeBlock();
            }
            else {
                parseExpressionOrError();
            }
            expect(RPARENTH, CLocalize.rparenthExpected());
            marker.done(CElementTypes.PARENTHESIZED_EXPRESSION);
            return true;
        }

        if (type == GENERIC_KEYWORD) {
            // _Generic(e, int: a, default: b): parsed for its extent only
            PsiBuilder.Marker marker = mark();
            advance();
            if (at(LPARENTH)) {
                skipBalanced();
            }
            marker.done(CElementTypes.CALL_EXPRESSION);
            return true;
        }

        return false;
    }

    public void parseArgumentList() {
        PsiBuilder.Marker marker = mark();
        advance();

        if (!at(RPARENTH)) {
            while (true) {
                if (!parseArgument()) {
                    error(CLocalize.expressionExpected());
                    if (!at(COMMA)) {
                        break;
                    }
                }
                parsePackExpansion();
                if (!consume(COMMA)) {
                    break;
                }
            }
        }

        expect(RPARENTH, CLocalize.rparenthExpected());
        marker.done(CElementTypes.ARGUMENT_LIST);
    }

    /**
     * The {@code ...} expanding a C++ pack argument, {@code f(args...)}.
     */
    protected void parsePackExpansion() {
    }

    /**
     * An argument, or a type name - which a macro such as {@code va_arg(ap, int)} or {@code offsetof(struct s, f)}
     * takes, and which stays one when the macro is not known.
     */
    private boolean parseArgument() {
        if (declarations().startsTypeNameDefinitely() || isTypeArgumentAhead()) {
            declarations().parseTypeName();
            return true;
        }
        if (parseLanguageArgument()) {
            return true;
        }
        return parseAssignmentExpression();
    }

    /**
     * Whether a type which can be nothing else makes the whole argument, where the start alone does not tell.
     */
    protected boolean isTypeArgumentAhead() {
        return false;
    }

    /**
     * An argument of a form beyond C; whether there was one.
     */
    protected boolean parseLanguageArgument() {
        return false;
    }

    /**
     * An initializer: a braced list, or an assignment expression.
     */
    public boolean parseInitializer() {
        if (at(LBRACE)) {
            parseInitializerList();
            return true;
        }
        return parseAssignmentExpression();
    }

    /**
     * {@code { initializer, .field = initializer, [index] = initializer, }}
     */
    public void parseInitializerList() {
        PsiBuilder.Marker marker = mark();
        advance();

        while (!eof() && !at(RBRACE)) {
            boolean designated = false;
            while (at(DOT) && lookAhead(1) == IDENTIFIER || at(LBRACKET)) {
                if (at(DOT)) {
                    advance();
                    advance();
                }
                else {
                    advance();
                    parseConditionalExpressionOrError();
                    // GNU range, [1 ... 5]
                    if (consume(ELLIPSIS)) {
                        parseConditionalExpressionOrError();
                    }
                    expect(RBRACKET, CLocalize.rbracketExpected());
                }
                designated = true;
            }

            if (designated) {
                expect(EQ, CLocalize.eqExpected());
            }
            else if (at(IDENTIFIER) && lookAhead(1) == COLON) {
                // old GNU designator, field: value
                advance();
                advance();
            }

            if (!parseInitializer()) {
                skipUntil(TokenSet.create(COMMA, RBRACE, SEMICOLON), CLocalize.expressionExpected());
            }
            parsePackExpansion();

            if (!consume(COMMA)) {
                break;
            }
        }

        expect(RBRACE, CLocalize.rbraceExpected());
        marker.done(CElementTypes.INITIALIZER_LIST);
    }
}
