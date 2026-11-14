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

import consulo.language.psi.PsiReference;
import java.util.List;
import java.util.ArrayList;
import consulo.cpp.lang.psi.resolve.CRawNameReferences;
import consulo.cpp.lang.psi.CPsiInitializerList;
import consulo.language.ast.ASTNode;

/**
 * @author VISTALL
 * @since 2026-10-19
 */
public class CPsiInitializerListImpl extends CPsiElementBaseImpl implements CPsiInitializerList {
    public CPsiInitializerListImpl(ASTNode node) {
        super(node);
    }

    /**
     * The references of the fields its designators name, {@code .kind} of {@code {.kind = CIRCLE}}.
     */
    @Override
    public PsiReference[] getReferences() {
        List<PsiReference> references = new ArrayList<>();
        CRawNameReferences.addDesignators(this, references);
        return references.toArray(PsiReference.EMPTY_ARRAY);
    }
}
