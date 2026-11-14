/*
 * Copyright 2010-2012 napile.org
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

package consulo.cpp.preprocessor.parser;

import consulo.cpp.localize.CLocalize;
import consulo.cpp.preprocessor.psi.*;
import consulo.language.ast.IElementType;
import consulo.language.parser.PsiBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * @author VISTALL
 * @date 13:34/29.12.12
 */
public class CPreprocessorDirectiveParser extends CPreprocessorParserHelper {
    public static final int EAT_LAST_END_IF = 1 << 0;

    public static void parse(@NotNull PsiBuilder builder, int f) {
        while (!builder.eof()) {
            skipLines(builder);

            if (builder.getTokenType() == S_INCLUDE_KEYWORD) {
                parseInclude(builder);
            }
            else if (builder.getTokenType() == S_DEFINE_KEYWORD) {
                parseDefine(builder);
            }
            else if (builder.getTokenType() == S_UNDEF_KEYWORD) {
                parseUndef(builder);
            }
            else if (builder.getTokenType() == S_PRAGMA_KEYWORD) {
                parsePragma(builder);
            }
            else if (builder.getTokenType() == S_IFNDEF_KEYWORD || builder.getTokenType() == S_IFDEF_KEYWORD
                    || builder.getTokenType() == S_IF_KEYWORD) {
                parseIf(builder);
            }
            else if (builder.getTokenType() == S_ENDIF_KEYWORD || builder.getTokenType() == S_ELSE_KEYWORD
                    || builder.getTokenType() == S_ELIF_KEYWORD) {
                if (isSet(f, EAT_LAST_END_IF)) {
                    error(builder, CLocalize.sIfdefOrSIfndefExpected());
                    advanceLexerAndSkipLines(builder);
                }
                else {
                    break;
                }
            }
            else {
                builder.advanceLexer();
            }
        }
    }

    public static void parseInclude(PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();

        IElementType nextElement = builder.lookAhead(1);

        if (nextElement != STRING_LITERAL && nextElement != STRING_INCLUDE_LITERAL) {
            builder.error("Incorrect include name");
        }
        else {
            builder.advanceLexer();
        }

        builder.advanceLexer();

        marker.done(nextElement == STRING_LITERAL ? CPreprocessorElementTypes.INCLUDE : CPreprocessorElementTypes.INDEP_INCLUDE);

        skipLines(builder);
    }

    public static void parseDefine(PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();

        // #define
        builder.advanceLexer();

        if (builder.getTokenType() == IDENTIFIER) {
            builder.advanceLexer();

            // a '(' right after the name - no space between - makes the macro function-like
            if (builder.getTokenType() == LPARENTH && builder.rawLookup(-1) == IDENTIFIER) {
                parseDefineParameters(builder);
            }
        }
        else {
            error(builder, CLocalize.identifierExpected());
        }

        PsiBuilder.Marker valueMarker = builder.mark();

        while (!builder.eof() && builder.getTokenType() != NEW_LINE) {
            boolean continuation = builder.getTokenType() == NEXT_LINE;

            builder.advanceLexer();

            // a backslash at the end of the line continues the macro on the next one
            if (continuation && builder.getTokenType() == NEW_LINE) {
                builder.advanceLexer();
            }
        }

        valueMarker.done(CPreprocessorElementTypes.DEFINE_VALUE);

        marker.done(CPreprocessorElementTypes.DEFINE_DIRECTIVE);

        skipLines(builder);
    }

    private static void parseDefineParameters(PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();

        // (
        builder.advanceLexer();
        skipContinuation(builder);

        while (!builder.eof() && builder.getTokenType() != RPARENTH && builder.getTokenType() != NEW_LINE) {
            if (builder.getTokenType() == IDENTIFIER || builder.getTokenType() == ELLIPSIS) {
                builder.advanceLexer();
                skipContinuation(builder);
            }
            else {
                error(builder, CLocalize.identifierExpected());
                break;
            }

            if (builder.getTokenType() == COMMA) {
                builder.advanceLexer();
                skipContinuation(builder);
            }
            else {
                break;
            }
        }

        checkMatchesWithoutLines(builder, RPARENTH, CLocalize.rparenthExpected());

        marker.done(CPreprocessorElementTypes.DEFINE_PARAMETER_LIST);
    }

    /**
     * Over a backslash ending the line, which continues the parameters on the next: {@code #define F(a, \}.
     */
    private static void skipContinuation(PsiBuilder builder) {
        while (builder.getTokenType() == NEXT_LINE) {
            builder.advanceLexer();
            if (builder.getTokenType() == NEW_LINE) {
                builder.advanceLexer();
            }
        }
    }

    public static void parseUndef(PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();

        // #undef
        advanceLexerAndSkipLines(builder);

        // macro name
        if (builder.getTokenType() == IDENTIFIER) {
            builder.advanceLexer();
        }
        else {
            error(builder, CLocalize.identifierExpected());
        }

        marker.done(CPreprocessorElementTypes.UNDEF_DIRECTIVE);

        skipLines(builder);
    }

    public static void parsePragma(PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();

        // #pragma — consume everything until end-of-line
        skipToLineEnd(builder);

        marker.done(CPreprocessorElementTypes.PRAGMA_DIRECTIVE);

        skipLines(builder);
    }

    /**
     * To the end of a directive's line, over the lines a backslash at the end of one continues it on.
     */
    private static void skipToLineEnd(PsiBuilder builder) {
        while (!builder.eof() && builder.getTokenType() != NEW_LINE) {
            boolean continuation = builder.getTokenType() == NEXT_LINE;
            builder.advanceLexer();
            if (continuation && builder.getTokenType() == NEW_LINE) {
                builder.advanceLexer();
            }
        }
    }

    public static void parseIf(PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();

        boolean isIfDef = builder.getTokenType() == S_IFDEF_KEYWORD || builder.getTokenType() == S_IFNDEF_KEYWORD;

        builder.advanceLexer();

        if (isIfDef) {
            // #ifdef / #ifndef: expect a single identifier
            if (builder.getTokenType() == IDENTIFIER) {
                doneOneToken(builder, CPreprocessorElementTypes.MACRO_REFERENCE);
            }
            else {
                error(builder, CLocalize.identifierExpected());
            }
        }
        else {
            // #if: consume the condition expression tokens until end-of-line
            skipToLineEnd(builder);
        }

        if (builder.getTokenType() == NEW_LINE) {
            builder.advanceLexer();
        }

        skipLines(builder);

        if (builder.getTokenType() != S_ENDIF_KEYWORD) {
            PsiBuilder.Marker bodyMarker = builder.mark();

            while (!builder.eof()) {
                parse(builder, 0);

                if (builder.getTokenType() == S_ENDIF_KEYWORD || builder.getTokenType() == S_ELSE_KEYWORD
                        || builder.getTokenType() == S_ELIF_KEYWORD) {
                    break;
                }
            }

            bodyMarker.done(CPreprocessorElementTypes.IF_BODY);

            // handle #elif chains
            while (builder.getTokenType() == S_ELIF_KEYWORD) {
                builder.advanceLexer();

                // consume the #elif condition until end-of-line
                skipToLineEnd(builder);
                if (builder.getTokenType() == NEW_LINE) {
                    builder.advanceLexer();
                }
                skipLines(builder);

                PsiBuilder.Marker elifBody = builder.mark();

                while (!builder.eof()) {
                    parse(builder, 0);

                    if (builder.getTokenType() == S_ENDIF_KEYWORD || builder.getTokenType() == S_ELSE_KEYWORD
                            || builder.getTokenType() == S_ELIF_KEYWORD) {
                        break;
                    }
                }
                elifBody.done(CPreprocessorElementTypes.IF_BODY);
            }

            if (builder.getTokenType() == S_ELSE_KEYWORD) {
                builder.advanceLexer();

                PsiBuilder.Marker elseBody = builder.mark();

                while (!builder.eof()) {
                    parse(builder, 0);

                    // parse() stops without advancing at any of these, so a stray #elif after #else must end the
                    // body too - otherwise this loop never moves on
                    if (builder.getTokenType() == S_ENDIF_KEYWORD || builder.getTokenType() == S_ELSE_KEYWORD
                            || builder.getTokenType() == S_ELIF_KEYWORD) {
                        break;
                    }
                }
                elseBody.done(CPreprocessorElementTypes.IF_BODY);
            }
        }
        checkMatchesWithoutLines(builder, S_ENDIF_KEYWORD, CLocalize.sEndIfExpected());

        marker.done(CPreprocessorElementTypes.IF_BLOCK);
    }
}
