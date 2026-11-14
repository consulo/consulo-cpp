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

import consulo.cpp.lang.psi.resolve.CReferenceExpressionReference;

import consulo.cpp.lang.psi.resolve.CTemplateArgumentReferences;
import consulo.language.psi.PsiReference;

import consulo.cpp.lang.psi.CPsiReferenceExpression;
import consulo.language.ast.ASTNode;

/**
 * @author VISTALL
 * @date 11:42/14.12.2011
 */
public class CPsiReferenceExpressionImpl extends CPsiElementBaseImpl implements CPsiReferenceExpression {
	public CPsiReferenceExpressionImpl(@org.jetbrains.annotations.NotNull ASTNode node) {
		super(node);
	}

    @Override
    public @Nullable PsiReference getReference() {
        return CReferenceExpressionReference.of(this);
    }

    /**
     * Its own reference and those of the names of its template arguments.
     */
    @Override
    public PsiReference[] getReferences() {
        return CTemplateArgumentReferences.withArguments(this, getReference());
    }
}
