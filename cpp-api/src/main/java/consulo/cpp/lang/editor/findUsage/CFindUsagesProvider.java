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
package consulo.cpp.lang.editor.findUsage;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.cpp.lang.CLanguage;
import consulo.cpp.lang.psi.*;
import consulo.cpp.lang.psi.resolve.CDeclarationKind;
import consulo.cpp.lang.psi.resolve.CDeclarations;
import consulo.cpp.lang.version.CLanguageVersions;
import consulo.cpp.localize.CLocalize;
import consulo.language.Language;
import consulo.language.ast.TokenSet;
import consulo.language.cacheBuilder.DefaultWordsScanner;
import consulo.language.cacheBuilder.WordsScanner;
import consulo.language.findUsage.FindUsagesProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import org.jspecify.annotations.Nullable;

/**
 * Find usages of C/C++ declarations: the word index finds the files naming one, and the references there which
 * resolve to it are its usages. The words are those of the C++ lexer, so a name in a macro body or a directive counts
 * as a word too.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
@ExtensionImpl
public class CFindUsagesProvider implements FindUsagesProvider {
    @Override
    public @Nullable WordsScanner getWordsScanner() {
        return new DefaultWordsScanner(CLanguageVersions.getDefaultCpp().createLexer(), TokenSet.create(CPsiTokens.IDENTIFIER),
            CPsiTokens.COMMENT_SET, CPsiTokens.STRING_LITERAL_SET);
    }

    @Override
    public boolean canFindUsagesFor(PsiElement element) {
        return element instanceof PsiNamedElement && CDeclarations.kindOf(element) != null && CDeclarations.nameIdentifier(element) != null;
    }

    @Override
    public String getType(PsiElement element) {
        if (element instanceof CPsiParameter) {
            return CLocalize.parameter().get();
        }
        if (element instanceof CPsiLocalVariable) {
            return CLocalize.localVariable().get();
        }
        if (element instanceof CPsiEnumConstant) {
            return CLocalize.constant().get();
        }
        CDeclarationKind kind = CDeclarations.kindOf(element);
        if (kind == null) {
            return "";
        }
        return switch (kind) {
            case VARIABLE -> CDeclarations.enclosingAggregate(element) != null ? CLocalize.field().get() : CLocalize.globalVariable().get();
            case FUNCTION -> CDeclarations.isMemberFunction(element) ? CLocalize.memberFunction().get() : CLocalize.functionDeclaration().get();
            case TYPE -> CDeclarations.isTypeAlias(element) ? CLocalize.typeAlias().get() : CLocalize.className().get();
            case NAMESPACE -> CLocalize.namespaceName().get();
        };
    }

    @RequiredReadAction
    @Override
    public String getDescriptiveName(PsiElement element) {
        String name = CDeclarations.nameOf(element);
        return name == null ? "" : name;
    }

    @RequiredReadAction
    @Override
    public String getNodeText(PsiElement element, boolean useFullName) {
        return getDescriptiveName(element);
    }

    @Override
    public Language getLanguage() {
        return CLanguage.INSTANCE;
    }
}
