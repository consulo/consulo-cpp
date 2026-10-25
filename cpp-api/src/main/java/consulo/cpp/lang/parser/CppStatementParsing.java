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
import consulo.language.parser.PsiBuilder;

/**
 * C++ statements, on top of the C ones: {@code try}, conditions declaring their variable, {@code if constexpr}, range
 * {@code for}, {@code co_return}.
 *
 * @author VISTALL
 * @since 2026-10-25
 */
public class CppStatementParsing extends CStatementParsing {
    public CppStatementParsing(CParsingContext context) {
        super(context);
    }

    @Override
    protected boolean parseLanguageStatement() {
        if (!at(TRY_KEYWORD)) {
            return false;
        }
        parseTryStatement();
        return true;
    }

    /**
     * {@code try { } catch (declaration) { }...}
     */
    private void parseTryStatement() {
        PsiBuilder.Marker marker = mark();
        advance();

        if (at(LBRACE)) {
            parseCodeBlock();
        }
        else {
            error(CLocalize.lbraceExpected());
        }

        while (at(CATCH_KEYWORD)) {
            advance();
            if (at(LPARENTH)) {
                declarations().parseParameterList();
            }
            if (at(LBRACE)) {
                parseCodeBlock();
            }
            else {
                error(CLocalize.lbraceExpected());
                break;
            }
        }
        marker.done(CElementTypes.CODE_BLOCK);
    }

    /**
     * {@code co_return}: the lexer knows no such keyword.
     */
    @Override
    protected boolean isLanguageReturnStart() {
        return at(IDENTIFIER) && "co_return".equals(tokenText());
    }

    /**
     * {@code if (T x = f())}, {@code if (auto x = f(); x > 0)}
     */
    @Override
    protected boolean allowsConditionDeclaration() {
        return true;
    }

    /**
     * {@code if constexpr (...)}
     */
    @Override
    protected void parseIfSpecifiers() {
        consume(CONSTEXPR_KEYWORD);
    }

    /**
     * {@code for (declaration : range)}
     */
    @Override
    protected boolean parseRangeForColon() {
        return consume(COLON);
    }
}
