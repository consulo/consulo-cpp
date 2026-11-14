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
package consulo.cpp.lang.psi.resolve;

import consulo.document.util.TextRange;
import consulo.language.impl.ast.LeafElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementResolveResult;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPolyVariantReferenceBase;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.resolve.ResolveCache;
import consulo.language.util.IncorrectOperationException;

import java.util.List;

/**
 * A name of the C tree: the identifier is its range, and what it means is computed once per change of PSI, by a
 * stateless resolver, in the {@link ResolveCache}. Two references of one element and one range are equal, which is
 * what the cache keys on - an element has a reference for each name of its template arguments too.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public abstract class CReferenceBase<T extends PsiElement> extends PsiPolyVariantReferenceBase<T> {
    private static final ResolveCache.PolyVariantContextResolver<CReferenceBase<?>> RESOLVER =
        (reference, file, incompleteCode) -> reference.resolveResults();

    protected CReferenceBase(T element, TextRange rangeInElement) {
        super(element, rangeInElement, false);
    }

    /**
     * The declarations the name means, the one to navigate to first.
     */
    protected abstract List<PsiElement> resolveTargets();

    /**
     * The results of {@link #resolveTargets()}, all valid; a reference choosing among them - the overloads of a call -
     * marks the others invalid.
     */
    protected ResolveResult[] resolveResults() {
        return PsiElementResolveResult.createResults(resolveTargets());
    }

    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        PsiFile file = getElement().getContainingFile();
        if (file == null) {
            return resolveResults();
        }
        return ResolveCache.getInstance(file.getProject()).resolveWithCaching(this, RESOLVER, true, incompleteCode, file);
    }

    /**
     * The declaration meant: the first valid result - the best overload - or, when none is valid, the first one, so that
     * a call with wrong arguments still navigates and colors.
     */
    @Override
    public PsiElement resolve() {
        ResolveResult[] results = multiResolve(false);
        for (ResolveResult result : results) {
            if (result.isValidResult()) {
                return result.getElement();
            }
        }
        return results.length == 0 ? null : results[0].getElement();
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
        String name = CDeclarations.nameOf(element);
        if (name == null || !name.equals(getCanonicalText())) {
            return false;
        }
        for (ResolveResult result : multiResolve(false)) {
            if (getElement().getManager().areElementsEquivalent(result.getElement(), element)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gives the identifier the reference covers the new name.
     */
    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        PsiElement identifier = getElement().findElementAt(getRangeInElement().getStartOffset());
        if (identifier == null || !(identifier.getNode() instanceof LeafElement leaf)) {
            throw new IncorrectOperationException("No identifier to rename at " + getRangeInElement());
        }
        leaf.replaceWithText(newElementName);
        return getElement();
    }

    @Override
    public Object[] getVariants() {
        return EMPTY_ARRAY;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CReferenceBase<?> that && getClass() == that.getClass() && getElement().equals(that.getElement())
            && getRangeInElement().equals(that.getRangeInElement());
    }

    @Override
    public int hashCode() {
        return getElement().hashCode() * 31 + getRangeInElement().hashCode();
    }
}
