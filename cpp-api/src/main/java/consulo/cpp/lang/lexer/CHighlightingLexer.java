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
package consulo.cpp.lang.lexer;

import consulo.cpp.lang.psi.CPsiTokens;
import consulo.cpp.preprocessor.psi.CPreprocessorTokenTypes;
import consulo.language.ast.IElementType;
import consulo.language.lexer.Lexer;
import consulo.language.lexer.LexerBase;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * The lexer the editor colors a C/C++ file with: the C lexer, which knows no directives, with the directive lines
 * recognized - {@code #include}, {@code # define}, {@code #pragma} as one keyword, the path of an include as one
 * token, and the rest of the line lexed as C, so the numbers and comments of {@code #if X > 1} color as they do in
 * code. A directive starts at a {@code #} first on its line and ends at a line end not escaped by a backslash.
 * <p>
 * The state is the mode and the state of the C lexer, so the editor can restart at any token.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public final class CHighlightingLexer extends LexerBase {
    private static final int CODE = 0;
    private static final int DIRECTIVE = 1;
    private static final int AFTER_INCLUDE = 2;

    private static final Map<String, IElementType> DIRECTIVES = Map.ofEntries(
        Map.entry("include", CPreprocessorTokenTypes.S_INCLUDE_KEYWORD),
        Map.entry("include_next", CPreprocessorTokenTypes.S_INCLUDE_KEYWORD),
        Map.entry("import", CPreprocessorTokenTypes.S_INCLUDE_KEYWORD),
        Map.entry("define", CPreprocessorTokenTypes.S_DEFINE_KEYWORD),
        Map.entry("undef", CPreprocessorTokenTypes.S_UNDEF_KEYWORD),
        Map.entry("if", CPreprocessorTokenTypes.S_IF_KEYWORD),
        Map.entry("ifdef", CPreprocessorTokenTypes.S_IFDEF_KEYWORD),
        Map.entry("ifndef", CPreprocessorTokenTypes.S_IFNDEF_KEYWORD),
        Map.entry("elif", CPreprocessorTokenTypes.S_ELIF_KEYWORD),
        Map.entry("else", CPreprocessorTokenTypes.S_ELSE_KEYWORD),
        Map.entry("endif", CPreprocessorTokenTypes.S_ENDIF_KEYWORD),
        Map.entry("pragma", CPreprocessorTokenTypes.S_PRAGMA_KEYWORD)
    );

    /**
     * Operators of a condition which read as part of the directive: {@code #if defined(X) && __has_include(<y.h>)}.
     */
    private static final Set<String> CONDITION_OPERATORS = Set.of("defined", "__has_include", "__has_include_next",
        "__has_cpp_attribute", "__has_attribute", "__has_builtin", "__has_feature", "__has_extension");

    private final Lexer myCode;

    private CharSequence myBuffer = "";
    private int myEnd;
    private int myMode;
    private int myDirectiveEnd;

    private @Nullable IElementType myTokenType;
    private int myTokenStart;
    private int myTokenEnd;
    private int myTokenState;

    public CHighlightingLexer(Lexer code) {
        myCode = code;
    }

    @Override
    public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
        myBuffer = buffer;
        myEnd = endOffset;
        myMode = initialState >>> 16;
        int codeState = initialState & 0xFFFF;
        if (myMode == CODE) {
            myCode.start(buffer, startOffset, endOffset, codeState);
            takeCodeToken();
        }
        else {
            myDirectiveEnd = directiveEnd(startOffset);
            if (myMode == DIRECTIVE) {
                myCode.start(buffer, startOffset, myDirectiveEnd, codeState);
                takeCodeToken();
            }
            else {
                lexAfterInclude(startOffset);
            }
        }
    }

    @Override
    public void advance() {
        if (myTokenType == null) {
            return;
        }
        if (myMode == AFTER_INCLUDE) {
            if (myTokenType == CPsiTokens.STRING_INCLUDE_LITERAL) {
                // what follows the path is the rest of the directive: a comment, most often
                startDirectiveRest(myTokenEnd, false);
            }
            else {
                lexAfterInclude(myTokenEnd);
            }
            return;
        }
        if (isDirectiveKeyword(myTokenType) && myMode == CODE) {
            // the keyword just given: the directive goes on after it
            startDirectiveRest(myTokenEnd, myTokenType == CPreprocessorTokenTypes.S_INCLUDE_KEYWORD);
            return;
        }
        myCode.advance();
        takeCodeToken();
    }

    /**
     * Makes the current token of the C lexer the current one - or, at a {@code #} opening a line, the keyword of the
     * directive starting there; at the end of a directive goes back to code.
     */
    private void takeCodeToken() {
        while (true) {
            IElementType type = myCode.getTokenType();
            if (type == null && myMode == DIRECTIVE) {
                // the directive ended: what follows is code again
                myMode = CODE;
                myCode.start(myBuffer, myDirectiveEnd, myEnd, 0);
                continue;
            }
            myTokenType = type;
            myTokenStart = myCode.getTokenStart();
            myTokenEnd = myCode.getTokenEnd();
            myTokenState = (myMode << 16) | (myCode.getState() & 0xFFFF);
            if (type == null) {
                return;
            }
            if (myMode == CODE && type == CPsiTokens.SHARP && isLineStart(myTokenStart)) {
                lexDirectiveKeyword(myTokenStart);
            }
            else if (myMode == DIRECTIVE && type == CPsiTokens.IDENTIFIER
                && CONDITION_OPERATORS.contains(myBuffer.subSequence(myTokenStart, myTokenEnd).toString())) {
                myTokenType = CPreprocessorTokenTypes.S_DIRECTIVE_KEYWORD;
            }
            return;
        }
    }

    /**
     * {@code #}, blanks, the name of the directive: one token.
     */
    private void lexDirectiveKeyword(int sharp) {
        int position = sharp + 1;
        while (position < myEnd && (myBuffer.charAt(position) == ' ' || myBuffer.charAt(position) == '\t')) {
            position++;
        }
        int nameStart = position;
        while (position < myEnd && (Character.isLetterOrDigit(myBuffer.charAt(position)) || myBuffer.charAt(position) == '_')) {
            position++;
        }
        String name = myBuffer.subSequence(nameStart, position).toString();
        myTokenType = DIRECTIVES.getOrDefault(name, CPreprocessorTokenTypes.S_DIRECTIVE_KEYWORD);
        myTokenStart = sharp;
        myTokenEnd = name.isEmpty() ? sharp + 1 : position;
        // a restart here lexes the '#' again, and comes to the same keyword
        myTokenState = CODE << 16;
        myDirectiveEnd = directiveEnd(sharp);
    }

    private void startDirectiveRest(int offset, boolean include) {
        if (include) {
            lexAfterInclude(offset);
            return;
        }
        myMode = DIRECTIVE;
        myCode.start(myBuffer, offset, Math.max(offset, myDirectiveEnd), 0);
        takeCodeToken();
    }

    /**
     * After {@code #include}: blanks, then the path - {@code <stdio.h>} or {@code "util.h"} - as one token; anything else,
     * a macro naming the header, is lexed as the rest of a directive.
     */
    private void lexAfterInclude(int offset) {
        myMode = AFTER_INCLUDE;
        int position = offset;
        while (position < myDirectiveEnd && (myBuffer.charAt(position) == ' ' || myBuffer.charAt(position) == '\t')) {
            position++;
        }
        if (position > offset) {
            setToken(CPsiTokens.WHITE_SPACE, offset, position);
            return;
        }
        char first = position < myDirectiveEnd ? myBuffer.charAt(position) : 0;
        if (first == '<' || first == '"') {
            char closing = first == '<' ? '>' : '"';
            int end = position + 1;
            while (end < myDirectiveEnd && myBuffer.charAt(end) != closing) {
                end++;
            }
            setToken(CPsiTokens.STRING_INCLUDE_LITERAL, position, Math.min(end + 1, myDirectiveEnd));
            return;
        }
        myMode = DIRECTIVE;
        myCode.start(myBuffer, offset, Math.max(offset, myDirectiveEnd), 0);
        takeCodeToken();
    }

    private void setToken(IElementType type, int start, int end) {
        myTokenType = type;
        myTokenStart = start;
        myTokenEnd = end;
        myTokenState = myMode << 16;
    }

    private static boolean isDirectiveKeyword(@Nullable IElementType type) {
        return type == CPreprocessorTokenTypes.S_DIRECTIVE_KEYWORD || DIRECTIVES.containsValue(type);
    }

    /**
     * Whether only blanks are before {@code offset} on its line.
     */
    private boolean isLineStart(int offset) {
        for (int i = offset - 1; i >= 0; i--) {
            char c = myBuffer.charAt(i);
            if (c == '\n') {
                return true;
            }
            if (c != ' ' && c != '\t' && c != '\r') {
                return false;
            }
        }
        return true;
    }

    /**
     * Where the directive holding {@code offset} ends: before the first line end not escaped by a backslash.
     */
    private int directiveEnd(int offset) {
        for (int i = offset; i < myEnd; i++) {
            if (myBuffer.charAt(i) == '\n') {
                int previous = i - 1;
                if (previous >= 0 && myBuffer.charAt(previous) == '\r') {
                    previous--;
                }
                if (previous < 0 || myBuffer.charAt(previous) != '\\') {
                    return i;
                }
            }
        }
        return myEnd;
    }

    @Override
    public int getState() {
        return myTokenState;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        return myTokenType;
    }

    @Override
    public int getTokenStart() {
        return myTokenStart;
    }

    @Override
    public int getTokenEnd() {
        return myTokenEnd;
    }

    @Override
    public CharSequence getBufferSequence() {
        return myBuffer;
    }

    @Override
    public int getBufferEnd() {
        return myEnd;
    }
}
