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

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.util.dataholder.Key;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Collects the declarations of one name and kinds. Scopes offer their declarations a level at a time - one earlier
 * statement of a block, the members of a class, of a namespace, of the file - and ask {@link #isLevelResolved()} after
 * each: the walk stops at the end of the first level which declares the name, so an inner declaration hides the outer
 * ones and the overloads of one scope are all kept.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public final class CResolveProcessor implements PsiScopeProcessor {
    /**
     * The name looked for; absent when every name is wanted, as completion wants them.
     */
    public static final Key<String> NAME_HINT = Key.create("c.resolve.name");

    public static final Key<Set<CDeclarationKind>> KINDS_HINT = Key.create("c.resolve.kinds");

    private final @Nullable String myName;
    private final Set<CDeclarationKind> myKinds;
    private final List<PsiElement> myResults = new ArrayList<>(1);

    public CResolveProcessor(@Nullable String name, Set<CDeclarationKind> kinds) {
        myName = name;
        myKinds = kinds;
    }

    @Override
    public boolean execute(PsiElement element, ResolveState state) {
        if (CDeclarations.usingOf(element) == CDeclarations.Using.DECLARATION) {
            return executeUsingDeclaration(element, state);
        }
        CDeclarationKind kind = CDeclarations.kindOf(element);
        if (kind == null || !myKinds.contains(kind)) {
            return true;
        }
        if (myName != null && !myName.equals(CDeclarations.nameOf(element)) && !isBindingName(element)) {
            return true;
        }
        if (!myResults.contains(element)) {
            myResults.add(element);
        }
        return true;
    }

    /**
     * {@code using ns::name;} declares {@code name} as what {@code ns::name} is: the declarations it resolves to.
     */
    private boolean executeUsingDeclaration(PsiElement usingDeclaration, ResolveState state) {
        if (myName != null && !myName.equals(CDeclarations.introducedName(usingDeclaration))) {
            return true;
        }
        if (usingDeclaration.getReference() instanceof PsiPolyVariantReference reference) {
            for (ResolveResult result : reference.multiResolve(false)) {
                PsiElement target = result.getElement();
                if (target != null && !execute(target, state)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether the level just offered declared the name - then the walk stops.
     */
    public boolean isLevelResolved() {
        return myName != null && !myResults.isEmpty();
    }

    public List<PsiElement> getResults() {
        return myResults;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> @Nullable T getHint(Key<T> hintKey) {
        if (hintKey == NAME_HINT) {
            return (T) myName;
        }
        if (hintKey == KINDS_HINT) {
            return (T) myKinds;
        }
        return null;
    }

    private boolean isBindingName(PsiElement element) {
        for (PsiElement name : CDeclarations.bindingNames(element)) {
            if (name.getText().equals(myName)) {
                return true;
            }
        }
        return false;
    }
}
