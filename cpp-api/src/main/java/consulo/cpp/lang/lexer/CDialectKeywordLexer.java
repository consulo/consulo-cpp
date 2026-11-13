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
import consulo.cpp.lang.version.CDialectKeyword;
import consulo.language.ast.IElementType;
import consulo.language.lexer.DelegateLexer;
import consulo.language.lexer.Lexer;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The lexer of a dialect with words of its own: the names among them are its keywords, the rest stay names.
 *
 * @author VISTALL
 * @since 2026-11-14
 */
public class CDialectKeywordLexer extends DelegateLexer {
    private final Map<String, CDialectKeyword> myKeywords;

    public CDialectKeywordLexer(Lexer delegate, Map<String, CDialectKeyword> keywords) {
        super(delegate);
        myKeywords = keywords;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        IElementType type = super.getTokenType();
        if (type != CPsiTokens.IDENTIFIER) {
            return type;
        }
        CDialectKeyword keyword = myKeywords.get(getBufferSequence().subSequence(getTokenStart(), getTokenEnd()).toString());
        return keyword == null ? type : keyword.getTokenType();
    }
}
