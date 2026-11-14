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

import consulo.cpp.lang.psi.CPsiTokens;
import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementResolveResult;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.resolve.ResolveState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The type a type reference, a base class or the {@code Owner::} of a qualified definition names - {@code Point},
 * {@code geo::Point}, {@code Outer::Inner}. The reference is the last segment; the highlighting asks for the others
 * with {@link CNameResolver#resolveChain}.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public final class CNameChainReference extends CReferenceBase<PsiElement> {
    private final CNameResolver.NameChain myChain;
    private final Set<CDeclarationKind> myKinds;
    private final ResolveState myState;

    private CNameChainReference(PsiElement element, CNameResolver.NameChain chain, PsiElement last, Set<CDeclarationKind> kinds, ResolveState state) {
        this(element, chain, last.getTextRange().shiftLeft(element.getTextRange().getStartOffset()), kinds, state);
    }

    private CNameChainReference(PsiElement element, CNameResolver.NameChain chain, TextRange range, Set<CDeclarationKind> kinds, ResolveState state) {
        super(element, range);
        myChain = chain;
        myKinds = kinds;
        myState = state;
    }

    /**
     * @param kinds what the last segment may name: a type, or a type or a namespace before {@code ::}
     */
    public static @Nullable CNameChainReference of(PsiElement element, Set<CDeclarationKind> kinds) {
        return of(element, kinds, ResolveState.initial());
    }

    /**
     * @param state {@link CNameResolver#NO_DIRECTIVES} for the namespace of a {@code using namespace} directive
     */
    public static @Nullable CNameChainReference of(PsiElement element, Set<CDeclarationKind> kinds, ResolveState state) {
        CNameResolver.NameChain chain = CNameResolver.nameChainOf(element);
        PsiElement last = chain.last();
        if (last == null || last.getTextLength() == 0) {
            return null;
        }
        return new CNameChainReference(element, chain, last, kinds, state);
    }

    /**
     * The reference of a name chain written somewhere in an element other than its own name - a name of its template
     * arguments, see {@link CTemplateArgumentReferences}.
     */
    public static @Nullable CNameChainReference ofChain(PsiElement element, CNameResolver.NameChain chain, Set<CDeclarationKind> kinds) {
        PsiElement last = chain.last();
        if (last == null || last.getTextLength() == 0) {
            return null;
        }
        return new CNameChainReference(element, chain, last, kinds, ResolveState.initial());
    }

    /**
     * The reference of a name chain made by a macro expansion as well as of one written in the file.
     */
    public static @Nullable CNameChainReference ofAnyLength(PsiElement element, Set<CDeclarationKind> kinds) {
        CNameResolver.NameChain chain = CNameResolver.nameChainOf(element);
        PsiElement last = chain.last();
        if (last == null) {
            return null;
        }
        TextRange range = last.getTextLength() == 0 ? TextRange.EMPTY_RANGE : last.getTextRange().shiftLeft(element.getTextRange().getStartOffset());
        return new CNameChainReference(element, chain, range, kinds, ResolveState.initial());
    }

    public CNameResolver.NameChain getChain() {
        return myChain;
    }

    public Set<CDeclarationKind> getKinds() {
        return myKinds;
    }

    public ResolveState getState() {
        return myState;
    }

    /**
     * {@code struct stat s;}, {@code enum Color c;}: an elaborated name means the aggregate - in C it is in a name space
     * of its own, apart from the function or the typedef of the same name.
     */
    @Override
    protected ResolveResult[] resolveResults() {
        List<PsiElement> targets = resolveTargets();
        if (!isElaborated()) {
            return PsiElementResolveResult.createResults(targets);
        }
        List<ResolveResult> results = new ArrayList<>(targets.size());
        for (PsiElement target : targets) {
            if (CDeclarations.isAggregate(target)) {
                results.add(new PsiElementResolveResult(target, true));
            }
        }
        return results.toArray(ResolveResult.EMPTY_ARRAY);
    }

    private boolean isElaborated() {
        PsiElement first = myChain.segments().isEmpty() ? null : myChain.segments().get(0);
        for (PsiElement child = getElement().getFirstChild(); child != null && child != first; child = child.getNextSibling()) {
            IElementType type = child.getNode().getElementType();
            if (type == CPsiTokens.STRUCT_KEYWORD || type == CPsiTokens.CLASS_KEYWORD || type == CPsiTokens.UNION_KEYWORD
                || type == CPsiTokens.ENUM_KEYWORD) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected List<PsiElement> resolveTargets() {
        return CNameResolver.resolveChainTargets(getElement(), myChain, myKinds, myState);
    }
}
