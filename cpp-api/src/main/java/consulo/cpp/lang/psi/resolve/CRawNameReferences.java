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
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.resolve.ResolveState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The references of names the parser leaves as tokens of the element around them: the members and bases of a
 * constructor's initializers, {@code : x(1), Base(2)}; the variables a lambda captures, {@code [top, &total]}; the
 * fields of designated initializers, {@code {.kind = CIRCLE, .u.radius = 2}}.
 *
 * @author VISTALL
 * @since 2026-11-14
 */
public final class CRawNameReferences {
    private enum Role {
        MEMBER_INITIALIZER,
        CAPTURE,
        DESIGNATOR,
        /**
         * A name chain looked up from a place of its own: a default argument of a template head, from the declaration the
         * head is of
         */
        CHAIN
    }

    private CRawNameReferences() {
    }

    /**
     * The names {@code : x(1), Base(2)} of a constructor's initializers initialize, each with the arguments after it.
     */
    public static void addMemberInitializers(PsiElement function, List<PsiReference> references) {
        boolean afterParameters = false;
        boolean initializers = false;
        PsiElement name = null;
        for (PsiElement child = function.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof PsiWhiteSpace || child instanceof PsiComment) {
                continue;
            }
            if (child instanceof CPsiParameterList) {
                afterParameters = true;
                continue;
            }
            if (child instanceof CPsiCodeBlock) {
                return;
            }
            IElementType type = child.getNode().getElementType();
            if (afterParameters && !initializers) {
                initializers = type == CPsiTokens.COLON;
                continue;
            }
            if (!initializers) {
                continue;
            }
            if (type == CPsiTokens.IDENTIFIER) {
                name = child;
            }
            else if ((child instanceof CPsiArgumentList || child instanceof CPsiInitializerList) && name != null) {
                add(function, name, Role.MEMBER_INITIALIZER, child, references);
                name = null;
            }
            else if (type == CPsiTokens.COMMA) {
                name = null;
            }
        }
    }

    /**
     * The variables {@code [top, &total, copy = top]} of a lambda captures: a name before {@code =} is the lambda's
     * own, the one after it a variable.
     */
    public static void addCaptures(PsiElement lambda, List<PsiReference> references) {
        PsiElement first = lambda.getFirstChild();
        if (first == null || first.getNode().getElementType() != CPsiTokens.LBRACKET) {
            return;
        }
        boolean initializer = false;
        for (PsiElement child = first.getNextSibling(); child != null; child = child.getNextSibling()) {
            if (child instanceof PsiWhiteSpace || child instanceof PsiComment) {
                continue;
            }
            IElementType type = child.getNode().getElementType();
            if (type == CPsiTokens.RBRACKET) {
                return;
            }
            if (type == CPsiTokens.COMMA) {
                initializer = false;
            }
            else if (type == CPsiTokens.EQ) {
                initializer = true;
            }
            else if (type == CPsiTokens.IDENTIFIER && (initializer || !isFollowedBy(child, CPsiTokens.EQ))) {
                add(lambda, child, Role.CAPTURE, lambda, references);
            }
        }
    }

    /**
     * The fields {@code .kind} and {@code .u.radius} the designators of an initializer list name.
     */
    public static void addDesignators(PsiElement initializerList, List<PsiReference> references) {
        IElementType previous = null;
        for (PsiElement child = initializerList.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof PsiWhiteSpace || child instanceof PsiComment) {
                continue;
            }
            IElementType type = child.getNode().getElementType();
            if (type == CPsiTokens.IDENTIFIER && previous == CPsiTokens.DOT) {
                add(initializerList, child, Role.DESIGNATOR, initializerList, references);
            }
            previous = type;
        }
    }

    /**
     * The references of a name chain written as tokens of {@code element}, looked up from {@code place}: of each
     * qualifier, then of the whole meaning {@code kinds}.
     */
    public static void addChainAt(PsiElement element, CNameResolver.NameChain chain, Set<CDeclarationKind> kinds, PsiElement place,
                                  List<PsiReference> references) {
        List<PsiElement> segments = chain.segments();
        for (int i = 1; i <= segments.size(); i++) {
            PsiElement last = segments.get(i - 1);
            if (last.getTextLength() > 0) {
                CNameResolver.NameChain prefix = new CNameResolver.NameChain(List.copyOf(segments.subList(0, i)), chain.global());
                references.add(new CRawNameReference(element, last, Role.CHAIN, place, prefix, i == segments.size() ? kinds : CDeclarationKind.SCOPES));
            }
        }
    }

    private static boolean isFollowedBy(PsiElement element, IElementType type) {
        PsiElement next = element.getNextSibling();
        while (next instanceof PsiWhiteSpace || next instanceof PsiComment) {
            next = next.getNextSibling();
        }
        return next != null && next.getNode().getElementType() == type;
    }

    private static void add(PsiElement element, PsiElement name, Role role, PsiElement place, List<PsiReference> references) {
        if (name.getTextLength() > 0) {
            references.add(new CRawNameReference(element, name, role, place, null, CDeclarationKind.ALL));
        }
    }

    private static final class CRawNameReference extends CReferenceBase<PsiElement> {
        private final PsiElement myName;
        private final Role myRole;
        private final PsiElement myPlace;
        private final CNameResolver.@Nullable NameChain myChain;
        private final Set<CDeclarationKind> myKinds;

        CRawNameReference(PsiElement element, PsiElement name, Role role, PsiElement place, CNameResolver.@Nullable NameChain chain,
                          Set<CDeclarationKind> kinds) {
            super(element, name.getTextRange().shiftLeft(element.getTextRange().getStartOffset()));
            myName = name;
            myRole = role;
            myPlace = place;
            myChain = chain;
            myKinds = kinds;
        }

        @Override
        protected List<PsiElement> resolveTargets() {
            String name = myName.getText();
            return switch (myRole) {
                case MEMBER_INITIALIZER -> memberInitializer(name);
                case CAPTURE -> CNameResolver.resolveUnqualified(myPlace, name, CDeclarationKind.VARIABLES);
                case DESIGNATOR -> designator(name);
                case CHAIN -> myChain == null ? List.of() : CNameResolver.resolveChainTargets(myPlace, myChain, myKinds);
            };
        }

        /**
         * A member of the class - {@code x(x)} initializes the field, whatever the parameter is called - or a base.
         */
        private List<PsiElement> memberInitializer(String name) {
            PsiElement owner = CNameResolver.enclosingClass(myPlace);
            if (owner != null) {
                List<PsiElement> fields = CNameResolver.resolveMember(myPlace, owner, name, CDeclarationKind.VARIABLES, ResolveState.initial());
                if (!fields.isEmpty()) {
                    return fields;
                }
            }
            return CNameResolver.resolveUnqualified(myPlace, name, CDeclarationKind.TYPES);
        }

        /**
         * A field of the aggregate the list initializes, or of the field the designators before it name.
         */
        private List<PsiElement> designator(String name) {
            PsiElement owner = aggregateOf(myPlace, 0);
            // .u.radius: radius is a field of u
            List<PsiElement> path = designatorPathBefore(myName);
            for (PsiElement segment : path) {
                if (owner == null) {
                    return List.of();
                }
                List<PsiElement> fields = CNameResolver.resolveMember(owner, segment.getText(), CDeclarationKind.VARIABLES);
                owner = fields.isEmpty() ? null : CTypes.scopeOfVariable(fields.get(0));
            }
            return owner == null ? List.of() : CNameResolver.resolveMember(owner, name, CDeclarationKind.VARIABLES);
        }
    }

    /**
     * The designators before {@code name} in its run, {@code u} of {@code .u.radius}.
     */
    private static List<PsiElement> designatorPathBefore(PsiElement name) {
        List<PsiElement> path = new ArrayList<>();
        PsiElement element = previousSignificant(name);
        while (element != null && element.getNode().getElementType() == CPsiTokens.DOT) {
            PsiElement segment = previousSignificant(element);
            if (segment == null || segment.getNode().getElementType() != CPsiTokens.IDENTIFIER) {
                break;
            }
            path.add(0, segment);
            element = previousSignificant(segment);
        }
        return path;
    }

    /**
     * The class, struct or union an initializer list initializes: that of the variable it initializes, of the cast
     * of a compound literal, or of the field a designator before it names in the list around it.
     */
    private static @Nullable PsiElement aggregateOf(PsiElement initializerList, int depth) {
        PsiElement parent = initializerList.getParent();
        if (parent == null || depth > 8) {
            return null;
        }
        if (parent instanceof CPsiInitializerList outer) {
            PsiElement eq = previousSignificant(initializerList);
            if (eq == null || eq.getNode().getElementType() != CPsiTokens.EQ) {
                // a positional element: of the type of the outer list's elements, not followed
                return null;
            }
            PsiElement owner = aggregateOf(outer, depth + 1);
            PsiElement last = previousSignificant(eq);
            if (owner == null || last == null || last.getNode().getElementType() != CPsiTokens.IDENTIFIER) {
                return null;
            }
            for (PsiElement segment : designatorPathBefore(last)) {
                List<PsiElement> fields = CNameResolver.resolveMember(owner, segment.getText(), CDeclarationKind.VARIABLES);
                owner = fields.isEmpty() ? null : CTypes.scopeOfVariable(fields.get(0));
                if (owner == null) {
                    return null;
                }
            }
            List<PsiElement> fields = CNameResolver.resolveMember(owner, last.getText(), CDeclarationKind.VARIABLES);
            return fields.isEmpty() ? null : CTypes.scopeOfVariable(fields.get(0));
        }
        if (parent instanceof CPsiCastExpression) {
            // (struct shape){ .kind = ... }
            CPsiTypeRef typeRef = CDeclarations.childOfType(parent, CPsiTypeRef.class);
            return typeRef == null ? null : CNameResolver.scopeOf(CNameResolver.resolveFirst(typeRef));
        }
        if (CDeclarations.kindOf(parent) == CDeclarationKind.VARIABLE) {
            return CTypes.scopeOfVariable(parent);
        }
        return null;
    }

    private static @Nullable PsiElement previousSignificant(PsiElement element) {
        PsiElement previous = element.getPrevSibling();
        while (previous instanceof PsiWhiteSpace || previous instanceof PsiComment) {
            previous = previous.getPrevSibling();
        }
        return previous;
    }
}
