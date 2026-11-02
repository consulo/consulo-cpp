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
package consulo.cpp.impl.refactoring;

import consulo.annotation.component.ExtensionImpl;
import consulo.cpp.lang.CLanguage;
import consulo.cpp.lang.psi.CPsiTokens;
import consulo.cpp.lang.version.CLanguageVersions;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.editor.refactoring.NamesValidator;
import consulo.language.lexer.Lexer;
import consulo.project.Project;

/**
 * Names a rename may give: what the C++ lexer reads as one identifier - a keyword of C++ is no name, whichever
 * standard a file is in.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
@ExtensionImpl
public class CNamesValidator implements NamesValidator {
    @Override
    public boolean isKeyword(String name, Project project) {
        IElementType type = singleToken(name);
        return type != null && CPsiTokens.KEYWORD_SET.contains(type);
    }

    @Override
    public boolean isIdentifier(String name, Project project) {
        return singleToken(name) == CPsiTokens.IDENTIFIER;
    }

    private static IElementType singleToken(String name) {
        Lexer lexer = CLanguageVersions.getDefaultCpp().createLexer();
        lexer.start(name);
        IElementType type = lexer.getTokenType();
        if (type == null) {
            return null;
        }
        lexer.advance();
        return lexer.getTokenType() == null ? type : null;
    }

    @Override
    public Language getLanguage() {
        return CLanguage.INSTANCE;
    }
}
