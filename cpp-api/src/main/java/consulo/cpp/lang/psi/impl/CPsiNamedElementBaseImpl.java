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
package consulo.cpp.lang.psi.impl;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.cpp.lang.psi.CPsiNameElementOwner;
import consulo.cpp.lang.psi.resolve.CDeclarations;
import consulo.cpp.lang.psi.resolve.CMacroArguments;
import consulo.language.ast.ASTNode;
import consulo.language.impl.ast.LeafElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.content.scope.SearchScope;
import consulo.language.util.IncorrectOperationException;
import org.jspecify.annotations.Nullable;

/**
 * A declaration of the C tree with a name: what find usages searches for and a rename renames. The name is the
 * identifier {@link CDeclarations#nameIdentifier} finds; the usages of a local or a parameter are all in its function.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public class CPsiNamedElementBaseImpl extends CPsiElementBaseImpl implements CPsiNameElementOwner {
    public CPsiNamedElementBaseImpl(ASTNode node) {
        super(node);
    }

    @RequiredReadAction
    @Override
    public @Nullable PsiElement getNameIdentifier() {
        return CDeclarations.nameIdentifier(this);
    }

    @RequiredReadAction
    @Override
    public @Nullable String getName() {
        PsiElement identifier = getNameIdentifier();
        return identifier == null ? null : identifier.getText();
    }

    @RequiredWriteAction
    @Override
    public PsiElement setName(String name) throws IncorrectOperationException {
        return rename(this, name);
    }

    @RequiredReadAction
    @Override
    public int getTextOffset() {
        PsiElement identifier = getNameIdentifier();
        if (identifier != null && identifier.getTextLength() > 0) {
            return identifier.getTextOffset();
        }
        // a name a macro made from its argument - DECLARE(int, counter): where the argument is written
        int written = identifier == null ? -1 : CMacroArguments.offsetWritten(identifier);
        return written >= 0 ? written : super.getTextOffset();
    }

    @RequiredReadAction
    @Override
    public SearchScope getUseScope() {
        PsiElement owner = CDeclarations.localOwner(this);
        return owner != null ? new LocalSearchScope(owner) : super.getUseScope();
    }

    /**
     * Renames a declaration in place: its identifier gets the new text.
     */
    public static PsiElement rename(PsiElement declaration, String name) throws IncorrectOperationException {
        PsiElement identifier = CDeclarations.nameIdentifier(declaration);
        if (identifier == null || identifier.getTextLength() == 0 || !(identifier.getNode() instanceof LeafElement leaf)) {
            throw new IncorrectOperationException("A name made by a macro cannot be renamed");
        }
        leaf.replaceWithText(name);
        return declaration;
    }
}
