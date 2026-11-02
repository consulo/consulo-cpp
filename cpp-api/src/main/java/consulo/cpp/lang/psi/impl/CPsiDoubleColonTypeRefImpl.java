/*
 * Copyright 2010-2013 napile.org
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

import consulo.language.psi.PsiReference;

import org.jetbrains.annotations.NotNull;
import consulo.cpp.lang.psi.CPsiDoubleColonTypeRef;
import consulo.language.ast.ASTNode;

/**
 * @author VISTALL
 * @date 16:00/07.01.13
 */
public class CPsiDoubleColonTypeRefImpl extends CPsiElementBaseImpl implements CPsiDoubleColonTypeRef {
	public CPsiDoubleColonTypeRefImpl(@NotNull ASTNode node) {
		super(node);
	}

    @Override
    public @Nullable PsiReference getReference() {
        return CNameChainReference.of(this, CDeclarationKind.SCOPES);
    }
}
