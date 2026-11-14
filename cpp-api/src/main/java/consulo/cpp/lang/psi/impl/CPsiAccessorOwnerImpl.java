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

import consulo.language.psi.PsiReference;
import java.util.List;
import java.util.ArrayList;
import consulo.cpp.lang.psi.resolve.CTemplateHeadReferences;
import consulo.language.ast.ASTNode;
import org.jetbrains.annotations.NotNull;
import consulo.cpp.lang.psi.CPsiAccessorOwner;

/**
 * @author VISTALL
 * @date 12:59/02.01.13
 */
public class CPsiAccessorOwnerImpl extends CPsiElementBaseImpl implements CPsiAccessorOwner {
	public CPsiAccessorOwnerImpl(@NotNull ASTNode node) {
		super(node);
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
