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

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import consulo.cpp.lang.psi.CPsiEnum;
import consulo.cpp.lang.psi.CPsiEnumConstant;
import consulo.cpp.lang.psi.CPsiTokens;
import consulo.cpp.lang.psi.visitor.CPsiElementVisitor;

/**
 * @author VISTALL
 * @date 17:41/14.12.2011
 */
public class CPsiEnumImpl extends CPsiNamedElementBaseImpl implements CPsiEnum
{
	public CPsiEnumImpl(@org.jetbrains.annotations.NotNull ASTNode node)
	{
		super(node);
	}

	@Override
	public void accept(@NotNull PsiElementVisitor visitor)
	{
		if(visitor instanceof CPsiElementVisitor)
		{
			((CPsiElementVisitor) visitor).visitEnum(this);
		}
		else
		{
			super.accept(visitor);
		}
	}

	@Override
	public CPsiEnumConstant[] getConstants()
	{
		return findChildrenByClass(CPsiEnumConstant.class);
	}
}
