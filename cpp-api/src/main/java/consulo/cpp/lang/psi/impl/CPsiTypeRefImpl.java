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

import org.jspecify.annotations.Nullable;

import consulo.cpp.lang.psi.resolve.CDeclarationKind;

import consulo.cpp.lang.psi.resolve.CNameChainReference;

import consulo.cpp.lang.psi.resolve.CTemplateArgumentReferences;
import consulo.language.psi.PsiReference;

import consulo.cpp.lang.psi.CPsiTokens;
import consulo.cpp.lang.psi.CPsiTypeRef;
import consulo.language.ast.ASTNode;

/**
 * @author VISTALL
 * @date 23:57/10.12.2011
 */
public class CPsiTypeRefImpl extends CPsiElementBaseImpl implements CPsiTypeRef {
	public CPsiTypeRefImpl(@org.jetbrains.annotations.NotNull ASTNode node) {
		super(node);
	}

    @Override
    public @Nullable PsiReference getReference() {
        if (getNode().findChildByType(CPsiTokens.DECLTYPE_KEYWORD) != null) {
            // decltype(points) names a value, a reference of its arguments
            return null;
        }
        return CNameChainReference.of(this, CDeclarationKind.TYPES);
    }

    /**
     * Its own reference and those of the names of its template arguments.
     */
    @Override
    public PsiReference[] getReferences() {
        return CTemplateArgumentReferences.withArguments(this, getReference());
    }
}
