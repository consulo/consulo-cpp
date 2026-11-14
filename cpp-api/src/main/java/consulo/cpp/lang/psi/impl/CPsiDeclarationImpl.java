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

import java.util.List;
import java.util.ArrayList;
import consulo.cpp.lang.psi.CPsiDeclarationMethod;
import consulo.cpp.lang.psi.resolve.CRawNameReferences;
import consulo.cpp.lang.psi.resolve.CTemplateArgumentReferences;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import consulo.cpp.lang.psi.CPsiDeclaration;
import consulo.cpp.lang.psi.CPsiTokens;
import consulo.cpp.lang.psi.resolve.CDeclarationKind;
import consulo.cpp.lang.psi.resolve.CDeclarations;
import consulo.cpp.lang.psi.resolve.CNameChainReference;
import consulo.cpp.lang.psi.resolve.CNameResolver;
import consulo.cpp.lang.psi.resolve.CScopes;
import consulo.language.psi.PsiReference;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;

/**
 * @author VISTALL
 * @date 17:42/07.01.13
 */
public class CPsiDeclarationImpl extends CPsiNamedElementBaseImpl implements CPsiDeclaration
{
	public CPsiDeclarationImpl(@NotNull ASTNode node)
	{
		super(node);
	}

	/**
	 * {@code using namespace ns;} and {@code using ns::name;} name what they bring in.
	 */
	@Override
	public @Nullable PsiReference getReference()
	{
		CDeclarations.Using using = CDeclarations.usingOf(this);
		if(using == CDeclarations.Using.DIRECTIVE)
		{
			return CNameChainReference.of(this, CDeclarationKind.NAMESPACES, CNameResolver.NO_DIRECTIVES);
		}
		if(using == CDeclarations.Using.DECLARATION)
		{
			return CNameChainReference.of(this, CDeclarationKind.ALL);
		}
		return super.getReference();
	}

	@Override
	public boolean processDeclarations(PsiScopeProcessor processor, ResolveState state, @Nullable PsiElement lastParent, PsiElement place)
	{
		return CScopes.processDeclaration(this, processor, state, lastParent);
	}

	/**
	 * Its own reference, those of the qualifiers of a using-declaration and of the names of a constructor's
	 * initializers.
	 */
	@Override
	public PsiReference[] getReferences()
	{
		List<PsiReference> references = new ArrayList<>();
		PsiReference own = getReference();
		if(own != null)
		{
			references.add(own);
		}
		if(CDeclarations.usingOf(this) != null)
		{
			CTemplateArgumentReferences.addArguments(this, references);
		}
		if(this instanceof CPsiDeclarationMethod)
		{
			CRawNameReferences.addMemberInitializers(this, references);
		}
		return references.toArray(PsiReference.EMPTY_ARRAY);
	}
}
