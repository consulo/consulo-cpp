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
package consulo.cpp.preprocessor.psi.impl;

import consulo.annotation.access.RequiredReadAction;
import consulo.cpp.lang.psi.resolve.CDeclarations;
import consulo.cpp.lang.psi.resolve.CMacroArguments;
import consulo.document.util.TextRange;
import consulo.language.impl.ast.LeafElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReferenceBase;
import consulo.language.util.IncorrectOperationException;
import org.jspecify.annotations.Nullable;

/**
 * A name written in an argument of a macro call - {@code add} of {@code EXPECT_EQ(calc::add(2, 3), 5)} - meaning what it
 * means in the code the call expands to. Soft: a name the expansion does not use as code is no error.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public final class CMacroArgumentReference extends PsiReferenceBase<CPreprocessorMacroInvocationImpl> {
    public CMacroArgumentReference(CPreprocessorMacroInvocationImpl call, TextRange rangeInCall) {
        super(call, rangeInCall, true);
    }

    @RequiredReadAction
    @Override
    public @Nullable PsiElement resolve() {
        for (CMacroArguments.Name name : CMacroArguments.namesOf(getElement())) {
            if (name.rangeInCall().equals(getRangeInElement())) {
                return CMacroArguments.resolveExpanded(name.expanded());
            }
        }
        return null;
    }

    @RequiredReadAction
    @Override
    public boolean isReferenceTo(PsiElement element) {
        String name = CDeclarations.nameOf(element);
        return name != null && name.equals(getCanonicalText()) && getElement().getManager().areElementsEquivalent(resolve(), element);
    }

    /**
     * Rewrites the call with the new name where the old one is written; the expansion follows from the new text.
     */
    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        CPreprocessorMacroInvocationImpl call = getElement();
        String text = call.getText();
        TextRange range = getRangeInElement();
        String renamed = text.substring(0, range.getStartOffset()) + newElementName + text.substring(range.getEndOffset());
        return ((LeafElement) call.getNode()).replaceWithText(renamed).getPsi();
    }

    @Override
    public Object[] getVariants() {
        return EMPTY_ARRAY;
    }
}
