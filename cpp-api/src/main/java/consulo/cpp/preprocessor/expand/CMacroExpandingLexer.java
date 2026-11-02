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
package consulo.cpp.preprocessor.expand;

import consulo.cpp.lang.psi.CPsiTokens;
import consulo.cpp.lang.version.CLanguageVersion;
import consulo.cpp.preprocessor.psi.CPreprocessorElementTypes;
import consulo.language.ast.IElementType;
import consulo.language.impl.ast.ForeignLeafType;
import consulo.language.lexer.Lexer;
import consulo.language.lexer.LexerBase;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Lexes the directive-free C text with every macro use expanded: the use itself - the name, and the parenthesised
 * arguments of a function-like macro - becomes one {@link CPreprocessorElementTypes#MACRO_INVOCATION} token, and the
 * tokens it expands to follow as zero-length {@link ForeignLeafType foreign leaves}. The parser reads the expanded
 * tokens like any others, so each ends up in the tree right in the node which consumed it.
 *
 * @author VISTALL
 * @since 2026-10-19
 */
public class CMacroExpandingLexer extends LexerBase {
    private record Token(IElementType type, int start, int end) {
    }

    private final Lexer myDelegate;
    private final PreprocessorExpander myExpander;
    private final CLanguageVersion myLanguageVersion;

    private CharSequence myBuffer = "";
    private int myBufferEnd;
    private List<Token> myTokens = List.of();
    private int myIndex;

    public CMacroExpandingLexer(PreprocessorExpander expander) {
        myExpander = expander;
        myLanguageVersion = expander.getLanguageVersion();
        myDelegate = myLanguageVersion.createLexer();
    }

    @Override
    public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
        myBuffer = buffer;
        myBufferEnd = endOffset;

        List<Token> source = new ArrayList<>();
        myDelegate.start(buffer, startOffset, endOffset, initialState);
        IElementType type;
        while ((type = myDelegate.getTokenType()) != null) {
            source.add(new Token(type, myDelegate.getTokenStart(), myDelegate.getTokenEnd()));
            myDelegate.advance();
        }

        myTokens = expand(source);
        myIndex = 0;
    }

    private List<Token> expand(List<Token> source) {
        List<Token> result = new ArrayList<>(source.size());
        for (int i = 0; i < source.size(); i++) {
            Token token = source.get(i);
            ExpandedMacro macro = token.type() == CPsiTokens.IDENTIFIER ? myExpander.getMacro(textOf(token)) : null;
            if (macro == null) {
                result.add(token);
                continue;
            }

            if (!macro.isFunctionLike()) {
                addExpansion(result, token.start(), token.end(), myExpander.expand(macro, null));
                continue;
            }

            // a function-like macro expands only where it is called
            int open = nextSignificant(source, i + 1);
            int close = open < source.size() && source.get(open).type() == CPsiTokens.LPARENTH ? closingParenthesis(source, open) : -1;
            if (close < 0) {
                result.add(token);
                continue;
            }

            List<CMacroToken> argumentTokens = new ArrayList<>();
            for (int j = open + 1; j < close; j++) {
                Token argumentToken = source.get(j);
                if (!isTrivia(argumentToken.type())) {
                    // where it is, so the name an argument passes stays the name written at that place of the file
                    argumentTokens.add(new CMacroToken(argumentToken.type(), textOf(argumentToken), argumentToken.start()));
                }
            }
            List<CMacroToken> expansion = myExpander.expand(macro, PreprocessorExpander.splitArguments(argumentTokens));
            addExpansion(result, token.start(), source.get(close).end(), expansion);
            i = close;
        }
        return result;
    }

    private static void addExpansion(List<Token> result, int start, int end, List<CMacroToken> expansion) {
        result.add(new Token(CPreprocessorElementTypes.MACRO_INVOCATION, start, end));
        for (CMacroToken expanded : expansion) {
            IElementType type = expanded.sourceOffset() >= start && expanded.sourceOffset() < end
                ? new CMacroArgumentLeafType(expanded.type(), expanded.text(), expanded.sourceOffset() - start)
                : new ForeignLeafType(expanded.type(), expanded.text());
            result.add(new Token(type, end, end));
        }
    }

    private int nextSignificant(List<Token> tokens, int from) {
        int index = from;
        while (index < tokens.size() && isTrivia(tokens.get(index).type())) {
            index++;
        }
        return index;
    }

    private static int closingParenthesis(List<Token> tokens, int open) {
        int depth = 0;
        for (int i = open; i < tokens.size(); i++) {
            IElementType type = tokens.get(i).type();
            if (type == CPsiTokens.LPARENTH) {
                depth++;
            }
            else if (type == CPsiTokens.RPARENTH && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private boolean isTrivia(IElementType type) {
        return myLanguageVersion.getWhitespaceTokens().contains(type) || myLanguageVersion.getCommentTokens().contains(type);
    }

    private String textOf(Token token) {
        return myBuffer.subSequence(token.start(), token.end()).toString();
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        return myIndex < myTokens.size() ? myTokens.get(myIndex).type() : null;
    }

    @Override
    public int getTokenStart() {
        return myIndex < myTokens.size() ? myTokens.get(myIndex).start() : myBufferEnd;
    }

    @Override
    public int getTokenEnd() {
        return myIndex < myTokens.size() ? myTokens.get(myIndex).end() : myBufferEnd;
    }

    @Override
    public void advance() {
        myIndex++;
    }

    @Override
    public CharSequence getBufferSequence() {
        return myBuffer;
    }

    @Override
    public int getBufferEnd() {
        return myBufferEnd;
    }
}
