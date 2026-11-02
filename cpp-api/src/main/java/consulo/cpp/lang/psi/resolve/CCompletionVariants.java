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

import consulo.cpp.lang.psi.CPsiDoubleColonTypeRef;
import consulo.cpp.lang.psi.CPsiReferenceExpression;
import consulo.cpp.lang.psi.CPsiTokens;
import consulo.cpp.lang.psi.CPsiTypeRef;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.resolve.PsiScopesUtilCore;
import consulo.language.psi.resolve.ResolveState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a name being typed may be: the same lookup as resolve, without a name - so nothing stops the walk - and with
 * each name kept once, as the innermost declaration of it, which is the one a resolve would find. After {@code .},
 * {@code ->} and {@code ::} the members of what is before; elsewhere what is visible there, and the top-level
 * declarations of the headers of the project the file includes - not of system headers, which would all be parsed.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public final class CCompletionVariants {
    private CCompletionVariants() {
    }

    /**
     * @param position the identifier being completed
     */
    public static List<PsiElement> collect(PsiElement position) {
        PsiElement parent = position.getParent();
        if (parent instanceof CPsiReferenceExpression) {
            return referenceVariants(parent, position);
        }
        if (parent instanceof CPsiTypeRef || parent instanceof CPsiDoubleColonTypeRef) {
            return typeVariants(parent, position);
        }
        return List.of();
    }

    private static List<PsiElement> referenceVariants(PsiElement reference, PsiElement position) {
        PsiElement operator = previousSignificant(position);
        IElementType type = operator == null ? null : operator.getNode().getElementType();
        PsiElement qualifier = operator == null ? null : previousSignificant(operator);
        if (type == CPsiTokens.DOT || type == CPsiTokens.ARROW) {
            PsiElement owner = qualifier == null ? null : CTypes.scopeOfExpression(qualifier);
            return owner == null ? List.of() : members(owner, CDeclarationKind.ALL);
        }
        if (type == CPsiTokens.DOUBLE_COLON) {
            PsiElement owner = qualifier == null ? null : CNameResolver.scopeOf(CNameResolver.resolveFirst(qualifier));
            return owner == null ? List.of() : members(owner, CDeclarationKind.ALL);
        }
        return visible(reference, CDeclarationKind.ALL);
    }

    private static List<PsiElement> typeVariants(PsiElement typeRef, PsiElement position) {
        CNameResolver.NameChain chain = CNameResolver.nameChainOf(typeRef);
        int index = chain.segments().indexOf(position);
        if (index > 0) {
            CNameResolver.NameChain qualifiers = new CNameResolver.NameChain(chain.segments().subList(0, index), chain.global());
            List<@Nullable PsiElement> targets = CNameResolver.resolveChain(typeRef, qualifiers, CDeclarationKind.SCOPES);
            PsiElement owner = CNameResolver.scopeOf(targets.get(targets.size() - 1));
            return owner == null ? List.of() : members(owner, CDeclarationKind.SCOPES);
        }
        return visible(typeRef, CDeclarationKind.SCOPES);
    }

    private static List<PsiElement> members(PsiElement owner, Set<CDeclarationKind> kinds) {
        CResolveProcessor processor = new CResolveProcessor(null, kinds);
        CScopes.processMembers(owner, processor, ResolveState.initial());
        return firstOfEachName(processor.getResults());
    }

    private static List<PsiElement> visible(PsiElement place, Set<CDeclarationKind> kinds) {
        CResolveProcessor processor = new CResolveProcessor(null, kinds);
        PsiScopesUtilCore.treeWalkUp(processor, place, null);
        List<PsiElement> found = new ArrayList<>(processor.getResults());
        PsiFile file = place.getContainingFile();
        if (file != null) {
            for (PsiFile header : CIncludedDeclarations.projectHeaders(file)) {
                for (PsiElement declaration : CDeclarations.topLevelDeclarationsOf(header)) {
                    CDeclarationKind kind = CDeclarations.kindOf(declaration);
                    if (kind != null && kinds.contains(kind)) {
                        found.add(declaration);
                    }
                }
            }
        }
        return firstOfEachName(found);
    }

    /**
     * The first declaration of each name: the walk offers the innermost first.
     */
    private static List<PsiElement> firstOfEachName(List<PsiElement> declarations) {
        Map<String, PsiElement> byName = new LinkedHashMap<>();
        for (PsiElement declaration : declarations) {
            String name = CDeclarations.nameOf(declaration);
            if (name != null && !name.isEmpty()) {
                byName.putIfAbsent(name, declaration);
            }
        }
        return List.copyOf(byName.values());
    }

    private static @Nullable PsiElement previousSignificant(PsiElement element) {
        PsiElement previous = element.getPrevSibling();
        while (previous instanceof PsiWhiteSpace || previous instanceof PsiComment) {
            previous = previous.getPrevSibling();
        }
        return previous;
    }
}
