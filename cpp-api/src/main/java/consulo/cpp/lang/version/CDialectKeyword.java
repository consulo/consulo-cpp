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
package consulo.cpp.lang.version;

import consulo.cpp.lang.psi.CPsiTokens;
import consulo.language.ast.IElementType;

/**
 * What a word a dialect adds to the language is - see {@link CLanguageVersion#getDialectKeywords()}. Each kind is one
 * token type, which the parser accepts where the standard words of that kind go.
 *
 * @author VISTALL
 * @since 2026-11-14
 */
public enum CDialectKeyword {
    /**
     * An address space - {@code device}, {@code constant}, {@code threadgroup} of Metal: a qualifier of types and
     * pointers, accepted where {@code const} is.
     */
    ADDRESS_SPACE(CPsiTokens.ADDRESS_SPACE_KEYWORD),
    /**
     * A function qualifier - {@code kernel}, {@code vertex}, {@code fragment} of Metal: in front of a function
     * declaration, accepted where {@code inline} is.
     */
    FUNCTION_QUALIFIER(CPsiTokens.FUNCTION_QUALIFIER_KEYWORD);

    private final IElementType myTokenType;

    CDialectKeyword(IElementType tokenType) {
        myTokenType = tokenType;
    }

    public IElementType getTokenType() {
        return myTokenType;
    }
}
