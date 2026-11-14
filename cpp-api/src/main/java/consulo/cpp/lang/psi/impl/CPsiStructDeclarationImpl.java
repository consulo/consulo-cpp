/*
 * Copyright 2011 napile
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

import consulo.language.psi.PsiReference;
import java.util.List;
import java.util.ArrayList;
import consulo.cpp.lang.psi.resolve.CTemplateHeadReferences;
import org.jspecify.annotations.Nullable;

import consulo.cpp.lang.psi.resolve.CScopes;

import consulo.language.psi.PsiElement;

import consulo.language.psi.resolve.ResolveState;

import consulo.language.psi.resolve.PsiScopeProcessor;

import consulo.language.ast.ASTNode;
import org.jetbrains.annotations.NotNull;
import consulo.cpp.lang.psi.CPsiStructDeclaration;

/**
 * @author VISTALL
 * @since 2026-03-20
 */
public class CPsiStructDeclarationImpl extends CPsiNamedElementBaseImpl implements CPsiStructDeclaration {
    public CPsiStructDeclarationImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public boolean processDeclarations(PsiScopeProcessor processor, ResolveState state, @Nullable PsiElement lastParent, PsiElement place) {
        return CScopes.processOwnerFromInside(this, processor, state, lastParent);
    }

    /**
     * The references of the defaults of the heads of its member templates.
     */
    @Override
    public PsiReference[] getReferences() {
        List<PsiReference> references = new ArrayList<>();
        CTemplateHeadReferences.addDefaults(this, references);
        return references.toArray(PsiReference.EMPTY_ARRAY);
    }
}
