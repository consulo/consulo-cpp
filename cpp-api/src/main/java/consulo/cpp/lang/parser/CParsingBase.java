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

import consulo.cpp.lang.psi.CPsiTokens;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.parser.PsiBuilder;
import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

/**
 * What every part of the grammar shares: reading tokens, reporting errors, and the other parts.
 *
 * @author VISTALL
 * @since 2026-10-19
 */
public abstract class CParsingBase implements CPsiTokens {
    protected final CParsingContext myContext;
    protected final PsiBuilder myBuilder;

    protected CParsingBase(CParsingContext context) {
        myContext = context;
        myBuilder = context.getBuilder();
    }

    protected CDeclarationParsing declarations() {
        return myContext.getDeclarationParsing();
    }

    protected CStatementParsing statements() {
        return myContext.getStatementParsing();
    }

    protected CExpressionParsing expressions() {
        return myContext.getExpressionParsing();
    }

    protected @Nullable IElementType token() {
        return myBuilder.getTokenType();
    }

    protected String tokenText() {
        String text = myBuilder.getTokenText();
        return text == null ? "" : text;
    }

    protected boolean at(IElementType type) {
        return myBuilder.getTokenType() == type;
    }

    protected boolean at(TokenSet types) {
        return types.contains(myBuilder.getTokenType());
    }

    protected @Nullable IElementType lookAhead(int steps) {
        return myBuilder.lookAhead(steps);
    }

    protected boolean eof() {
        return myBuilder.eof();
    }

    protected void advance() {
        myBuilder.advanceLexer();
    }

    protected boolean consume(IElementType type) {
        if (at(type)) {
            advance();
            return true;
        }
        return false;
    }

    protected boolean expect(IElementType type, LocalizeValue message) {
        if (consume(type)) {
            return true;
        }
        error(message);
        return false;
    }

    protected void error(LocalizeValue message) {
        myContext.errorReported();
        myBuilder.error(message);
    }

    /**
     * Ends {@code marker} as an error.
     */
    protected void errorAt(PsiBuilder.Marker marker, LocalizeValue message) {
        myContext.errorReported();
        marker.error(message);
    }

    /**
     * The number of errors reported so far: a speculative parse compares it before and after.
     */
    protected int errorCount() {
        return myContext.getErrorCount();
    }

    protected PsiBuilder.Marker mark() {
        return myBuilder.mark();
    }

    /**
     * Consumes a bracketed group - the opening token at the current position up to its matching close - with
     * whatever is inside it.
     */
    protected void skipBalanced() {
        int depth = 0;
        do {
            IElementType type = token();
            if (type == LPARENTH || type == LBRACKET || type == LBRACE) {
                depth++;
            }
            else if (type == RPARENTH || type == RBRACKET || type == RBRACE) {
                depth--;
            }
            advance();
        }
        while (depth > 0 && !eof());
    }

    /**
     * Error recovery: wraps everything up to one of {@code stops} - not into a bracketed group - in a single error.
     */
    protected void skipUntil(TokenSet stops, LocalizeValue message) {
        PsiBuilder.Marker error = mark();
        boolean skipped = false;
        while (!eof() && !at(stops)) {
            if (at(LPARENTH) || at(LBRACKET) || at(LBRACE)) {
                skipBalanced();
            }
            else {
                advance();
            }
            skipped = true;
        }
        if (skipped) {
            errorAt(error, message);
        }
        else {
            error.drop();
            error(message);
        }
    }
}
