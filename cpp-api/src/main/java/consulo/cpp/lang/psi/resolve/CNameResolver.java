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

import consulo.cpp.lang.psi.*;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.resolve.PsiScopesUtilCore;
import consulo.language.psi.resolve.ResolveState;
import consulo.util.dataholder.Key;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Looks names up in the file they are used in. A name resolves to the declarations it may mean - all overloads of a
 * function, a declaration and its definition - the first being the one to navigate to.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public final class CNameResolver {
    private static final int MAX_TYPEDEF_DEPTH = 8;

    /**
     * Set to look a name up without the {@code using namespace} directives on the way - how the namespace a directive
     * names is found, which must not depend on the directive itself.
     */
    public static final Key<Boolean> WITHOUT_USING_DIRECTIVES = Key.create("c.resolve.without.using.directives");

    public static final ResolveState NO_DIRECTIVES = ResolveState.initial().put(WITHOUT_USING_DIRECTIVES, Boolean.TRUE);

    private CNameResolver() {
    }

    /**
     * A name without a qualifier, looked up from where it is used outwards.
     */
    public static List<PsiElement> resolveUnqualified(PsiElement place, String name, Set<CDeclarationKind> kinds) {
        return resolveUnqualified(place, name, kinds, ResolveState.initial());
    }

    public static List<PsiElement> resolveUnqualified(PsiElement place, String name, Set<CDeclarationKind> kinds, ResolveState state) {
        CResolveProcessor processor = new CResolveProcessor(name, kinds);
        PsiScopesUtilCore.treeWalkUp(processor, place, null, CScopes.withOrigin(state, place));
        return CDeclarations.definitionsFirst(processor.getResults());
    }

    /**
     * {@code ::name}: the file only.
     */
    public static List<PsiElement> resolveGlobal(PsiElement place, String name, Set<CDeclarationKind> kinds) {
        return resolveGlobal(place, name, kinds, ResolveState.initial());
    }

    public static List<PsiElement> resolveGlobal(PsiElement place, String name, Set<CDeclarationKind> kinds, ResolveState state) {
        CResolveProcessor processor = new CResolveProcessor(name, kinds);
        PsiFile file = place.getContainingFile();
        if (file != null) {
            CScopes.processFile(file, processor, CScopes.withOrigin(state, place));
        }
        return CDeclarations.definitionsFirst(processor.getResults());
    }

    /**
     * {@code owner::name} or {@code value.name}: the members of a class, enum or namespace.
     */
    public static List<PsiElement> resolveMember(PsiElement owner, String name, Set<CDeclarationKind> kinds) {
        CResolveProcessor processor = new CResolveProcessor(name, kinds);
        CScopes.processMembers(owner, processor, ResolveState.initial());
        return CDeclarations.definitionsFirst(processor.getResults());
    }

    /**
     * The same, used at {@code place}: the members of a namespace include its blocks in the headers the file of
     * {@code place} includes, not only in those around the block the qualifier resolved to.
     */
    public static List<PsiElement> resolveMember(PsiElement place, PsiElement owner, String name, Set<CDeclarationKind> kinds, ResolveState state) {
        CResolveProcessor processor = new CResolveProcessor(name, kinds);
        CScopes.processMembers(owner, processor, CScopes.withOrigin(state, place));
        return CDeclarations.definitionsFirst(processor.getResults());
    }

    /**
     * What a declaration names as a scope of members: a class, struct, union, enum or namespace itself, the type a
     * typedef stands for; {@code null} for anything else, or a typedef of a type not found.
     */
    public static @Nullable PsiElement scopeOf(@Nullable PsiElement declaration) {
        PsiElement current = declaration;
        for (int depth = 0; current != null && CDeclarations.isTypeAlias(current) && depth < MAX_TYPEDEF_DEPTH; depth++) {
            PsiElement aggregate = innerAggregate(current);
            if (aggregate != null) {
                // typedef struct { ... } Point;
                return aggregate;
            }
            CPsiTypeRef typeRef = CDeclarations.typeRefOf(current);
            current = typeRef == null ? null : resolveFirst(typeRef);
        }
        for (int depth = 0; current instanceof CPsiNamespaceDeclaration && depth < MAX_TYPEDEF_DEPTH; depth++) {
            NameChain target = namespaceAliasTarget(current);
            if (target == null) {
                return current;
            }
            // namespace alias = a::b;
            current = aliasedNamespace(current, target);
        }
        if (current == null) {
            return null;
        }
        return CDeclarations.isAggregate(current) ? current : null;
    }

    /**
     * The aliases being followed on this thread: {@code namespace views = ranges::views;} looks its name up where the alias
     * itself is visible.
     */
    private static final ThreadLocal<Set<PsiElement>> FOLLOWED_ALIASES = ThreadLocal.withInitial(HashSet::new);

    private static @Nullable PsiElement aliasedNamespace(PsiElement alias, NameChain target) {
        Set<PsiElement> followed = FOLLOWED_ALIASES.get();
        if (!followed.add(alias)) {
            return null;
        }
        try {
            for (PsiElement found : resolveChainTargets(alias, target, CDeclarationKind.SCOPES)) {
                if (found != alias) {
                    return found;
                }
            }
            return null;
        }
        finally {
            followed.remove(alias);
        }
    }

    /**
     * The name a namespace alias stands for, {@code a::b} of {@code namespace alias = a::b;}; {@code null} for a
     * namespace which is no alias.
     */
    public static @Nullable NameChain namespaceAliasTarget(PsiElement namespace) {
        // namespace alias = ...: the = right after the name, not one a broken declaration of a namespace's body left
        ASTNode name = namespace.getNode().findChildByType(CPsiTokens.IDENTIFIER);
        ASTNode eq = name == null ? null : name.getTreeNext();
        while (eq != null && (CPsiTokens.WHITE_SPACE_SET.contains(eq.getElementType()) || eq.getPsi() instanceof PsiComment)) {
            eq = eq.getTreeNext();
        }
        if (eq == null || eq.getElementType() != CPsiTokens.EQ) {
            return null;
        }
        List<PsiElement> names = new ArrayList<>();
        boolean global = false;
        for (ASTNode node = eq.getTreeNext(); node != null; node = node.getTreeNext()) {
            IElementType type = node.getElementType();
            if (type == CPsiTokens.IDENTIFIER) {
                names.add(node.getPsi());
            }
            else if (type == CPsiTokens.DOUBLE_COLON) {
                global |= names.isEmpty();
            }
            else if (!CPsiTokens.WHITE_SPACE_SET.contains(type)) {
                break;
            }
        }
        return names.isEmpty() ? null : new NameChain(names, global);
    }

    private static @Nullable PsiElement innerAggregate(PsiElement typedef) {
        for (PsiElement child = typedef.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (CDeclarations.isAggregate(child)) {
                return child;
            }
        }
        return null;
    }

    /**
     * The class whose members a {@code value.} or {@code value->} reaches, see {@link CTypes}.
     */
    public static @Nullable PsiElement typeScopeOfValue(PsiElement qualifier) {
        return CTypes.scopeOfExpression(qualifier);
    }

    /**
     * The class {@code this} stands for at {@code place}: the one around it, or the one an out-of-class member
     * definition names.
     */
    public static @Nullable PsiElement enclosingClass(PsiElement place) {
        for (PsiElement element = place.getParent(); element != null && !(element instanceof PsiFile); element = element.getParent()) {
            if (CDeclarations.isAggregate(element) && !(element instanceof CPsiEnum)) {
                return element;
            }
            if (element instanceof CPsiImplementingMethod) {
                CPsiDoubleColonTypeRef qualifier = CDeclarations.childOfType(element, CPsiDoubleColonTypeRef.class);
                if (qualifier != null) {
                    return resolveQualifiedOwner(qualifier);
                }
            }
        }
        return null;
    }

    /**
     * The class or namespace {@code Owner::} of a qualified declarator names.
     */
    public static @Nullable PsiElement resolveQualifiedOwner(CPsiDoubleColonTypeRef qualifier) {
        return scopeOf(resolveFirst(qualifier));
    }

    /**
     * The declaration the reference of {@code element} means first, if it has one.
     */
    public static @Nullable PsiElement resolveFirst(PsiElement element) {
        PsiReference reference = element.getReference();
        if (reference == null) {
            // a name a macro expansion made: it has no reference of its own, having no place in the file
            if (element instanceof CPsiReferenceExpression expression) {
                reference = CReferenceExpressionReference.ofAnyLength(expression);
            }
            else if (element instanceof CPsiTypeRef || element instanceof CPsiSuperClass) {
                reference = CNameChainReference.ofAnyLength(element, CDeclarationKind.TYPES);
            }
            else if (element instanceof CPsiDoubleColonTypeRef) {
                reference = CNameChainReference.ofAnyLength(element, CDeclarationKind.SCOPES);
            }
        }
        return reference == null ? null : reference.resolve();
    }

    // -----------------------------------------------------------------------
    // Qualified names of declarations: Type, ns::Type, Outer::Inner, ::Type
    // -----------------------------------------------------------------------

    /**
     * The identifiers of the name a type reference, a {@code Class::} of a definition or a base class spells - the last
     * {@code a::b::c} at the top level, not those of template arguments - and whether it starts with {@code ::}.
     */
    public record NameChain(List<PsiElement> segments, boolean global) {
        public @Nullable PsiElement last() {
            return segments.isEmpty() ? null : segments.get(segments.size() - 1);
        }
    }

    public static NameChain nameChainOf(PsiElement element) {
        List<PsiElement> segments = new ArrayList<>();
        boolean global = false;
        boolean afterColons = false;
        int depth = 0;
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            IElementType type = child.getNode().getElementType();
            if (type == CPsiTokens.LT) {
                depth++;
            }
            else if (type == CPsiTokens.GT) {
                depth = Math.max(0, depth - 1);
            }
            else if (depth > 0) {
                continue;
            }
            else if (type == CPsiTokens.DOUBLE_COLON) {
                if (segments.isEmpty()) {
                    global = true;
                }
                afterColons = true;
                continue;
            }
            else if (type == CPsiTokens.IDENTIFIER) {
                if (!afterColons) {
                    // a new name: a macro or an elaborated keyword came before it
                    segments.clear();
                    global = false;
                }
                segments.add(child);
            }
            else if (CPsiTokens.WHITE_SPACE_SET.contains(type)) {
                continue;
            }
            afterColons = false;
        }
        if (global && segments.isEmpty()) {
            global = false;
        }
        return new NameChain(segments, global);
    }

    /**
     * What each segment of a name chain means, the first declaration of each; a segment after one not found is not
     * looked up.
     */
    public static List<@Nullable PsiElement> resolveChain(PsiElement place, NameChain chain, Set<CDeclarationKind> lastKinds) {
        return resolveChain(place, chain, lastKinds, ResolveState.initial());
    }

    public static List<@Nullable PsiElement> resolveChain(PsiElement place, NameChain chain, Set<CDeclarationKind> lastKinds, ResolveState state) {
        List<@Nullable PsiElement> targets = new ArrayList<>(chain.segments().size());
        PsiElement owner = null;
        for (int i = 0; i < chain.segments().size(); i++) {
            String name = chain.segments().get(i).getText();
            boolean last = i == chain.segments().size() - 1;
            Set<CDeclarationKind> kinds = last ? lastKinds : CDeclarationKind.SCOPES;
            List<PsiElement> found;
            if (i == 0) {
                found = chain.global() ? resolveGlobal(place, name, kinds, state) : resolveUnqualified(place, name, kinds, state);
            }
            else if (owner != null) {
                found = resolveMember(place, owner, name, kinds, state);
            }
            else {
                found = List.of();
            }
            PsiElement target = found.isEmpty() ? null : found.get(0);
            targets.add(target);
            if (!last) {
                owner = scopeOf(target);
            }
        }
        return targets;
    }

    /**
     * Every declaration the last segment of a name chain means.
     */
    public static List<PsiElement> resolveChainTargets(PsiElement place, NameChain chain, Set<CDeclarationKind> lastKinds) {
        return resolveChainTargets(place, chain, lastKinds, ResolveState.initial());
    }

    public static List<PsiElement> resolveChainTargets(PsiElement place, NameChain chain, Set<CDeclarationKind> lastKinds, ResolveState state) {
        List<PsiElement> segments = chain.segments();
        if (segments.isEmpty()) {
            return List.of();
        }
        String lastName = segments.get(segments.size() - 1).getText();
        if (segments.size() == 1) {
            return chain.global() ? resolveGlobal(place, lastName, lastKinds, state) : resolveUnqualified(place, lastName, lastKinds, state);
        }
        List<@Nullable PsiElement> qualifiers = resolveChain(place, new NameChain(segments.subList(0, segments.size() - 1), chain.global()),
            CDeclarationKind.SCOPES, state);
        PsiElement owner = scopeOf(qualifiers.get(qualifiers.size() - 1));
        return owner == null ? List.of() : resolveMember(place, owner, lastName, lastKinds, state);
    }
}
