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

import consulo.cpp.lang.psi.CPsiCallExpression;
import consulo.cpp.lang.psi.CPsiNamespaceDeclaration;
import consulo.cpp.lang.psi.CPsiReferenceExpression;
import consulo.cpp.lang.psi.CPsiTokens;
import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementResolveResult;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.PsiWhiteSpace;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The name of a reference expression: {@code x}, {@code f(...)}, {@code ns::x}, {@code value.x}, {@code value->x},
 * {@code ::x}. Its position decides what it may name, once, before anything is looked up.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public final class CReferenceExpressionReference extends CReferenceBase<CPsiReferenceExpression> {
    private static final Set<CDeclarationKind> VALUE = Set.copyOf(EnumSet.of(CDeclarationKind.VARIABLE, CDeclarationKind.FUNCTION, CDeclarationKind.TYPE));

    private final PsiElement myNameLeaf;

    private CReferenceExpressionReference(CPsiReferenceExpression element, PsiElement nameLeaf) {
        this(element, nameLeaf, nameLeaf.getTextRange().shiftLeft(element.getTextRange().getStartOffset()));
    }

    private CReferenceExpressionReference(CPsiReferenceExpression element, PsiElement nameLeaf, TextRange rangeInElement) {
        super(element, rangeInElement);
        myNameLeaf = nameLeaf;
    }

    /**
     * The reference of an expression ending with a name in the source; none for {@code this}, an operator name, or a
     * name a macro expansion produced.
     */
    public static @Nullable CReferenceExpressionReference of(CPsiReferenceExpression element) {
        PsiElement last = element.getLastChild();
        if (last == null || last.getNode().getElementType() != CPsiTokens.IDENTIFIER || last.getTextLength() == 0) {
            return null;
        }
        return new CReferenceExpressionReference(element, last);
    }

    /**
     * The reference of an expression ending with a name, whether written in the file or made by a macro expansion -
     * what resolving a qualifier inside an expansion needs.
     */
    public static @Nullable CReferenceExpressionReference ofAnyLength(CPsiReferenceExpression element) {
        PsiElement last = element.getLastChild();
        if (last == null || last.getNode().getElementType() != CPsiTokens.IDENTIFIER) {
            return null;
        }
        return last.getTextLength() == 0 ? forExpanded(element, last) : new CReferenceExpressionReference(element, last);
    }

    /**
     * The reference of a name a macro expansion produced: of no length in the file, it is what the name an argument of the
     * macro call passed means, see {@link CMacroArguments}.
     */
    public static CReferenceExpressionReference forExpanded(CPsiReferenceExpression element, PsiElement nameLeaf) {
        // an expanded name has no place in the file: the range is empty
        return new CReferenceExpressionReference(element, nameLeaf, TextRange.EMPTY_RANGE);
    }

    @Override
    protected List<PsiElement> resolveTargets() {
        String name = myNameLeaf.getText();
        PsiElement operator = previousSignificant(myNameLeaf);
        IElementType operatorType = operator == null ? null : operator.getNode().getElementType();
        Set<CDeclarationKind> kinds = kinds();

        if (operatorType == CPsiTokens.DOUBLE_COLON) {
            PsiElement qualifier = previousSignificant(operator);
            if (qualifier == null) {
                // ::name
                return CNameResolver.resolveGlobal(getElement(), name, kinds);
            }
            PsiElement owner = CNameResolver.scopeOf(CNameResolver.resolveFirst(qualifier));
            return owner == null ? List.of() : CNameResolver.resolveMember(owner, name, kinds);
        }
        if (operatorType == CPsiTokens.DOT || operatorType == CPsiTokens.ARROW) {
            PsiElement qualifier = previousSignificant(operator);
            PsiElement owner = qualifier == null ? null : CNameResolver.typeScopeOfValue(qualifier);
            return owner == null ? List.of() : CNameResolver.resolveMember(owner, name, VALUE);
        }
        return CNameResolver.resolveUnqualified(getElement(), name, kinds);
    }

    /**
     * What a call means is decided among the functions found by the types of its arguments, see {@link COverloads};
     * an unqualified call looks in the namespaces of the classes of its arguments too. Wherever a function or a variable
     * and a class have one name - {@code struct stat} and {@code stat()} - the name means the function or the variable.
     */
    @Override
    protected ResolveResult[] resolveResults() {
        List<PsiElement> targets = resolveTargets();
        List<PsiElement> functions = new ArrayList<>();
        List<PsiElement> values = new ArrayList<>();
        List<PsiElement> types = new ArrayList<>();
        for (PsiElement target : targets) {
            CDeclarationKind kind = CDeclarations.kindOf(target);
            (kind == CDeclarationKind.FUNCTION ? functions : kind == CDeclarationKind.TYPE ? types : values).add(target);
        }

        PsiElement parent = getElement().getParent();
        boolean callee = parent instanceof CPsiCallExpression && parent.getFirstChild() == getElement();
        List<ResolveResult> results = new ArrayList<>(targets.size());
        if (callee) {
            List<PsiElement> arguments = CTypes.argumentsOf(parent);
            if (isUnqualified() && values.isEmpty() && types.isEmpty() && !anyMember(functions)) {
                for (PsiElement found : argumentDependentLookup(arguments)) {
                    if (!functions.contains(found)) {
                        functions.add(found);
                    }
                }
            }
            if (!functions.isEmpty()) {
                results.addAll(List.of(COverloads.select(functions, arguments, CTypes.receiverOf(getElement()))));
            }
        }
        else {
            for (PsiElement function : functions) {
                results.add(new PsiElementResolveResult(function, true));
            }
        }
        boolean hidden = !functions.isEmpty() || !values.isEmpty();
        for (PsiElement value : values) {
            results.add(new PsiElementResolveResult(value, functions.isEmpty()));
        }
        for (PsiElement type : types) {
            results.add(new PsiElementResolveResult(type, !hidden));
        }
        return results.toArray(ResolveResult.EMPTY_ARRAY);
    }

    private boolean isUnqualified() {
        PsiElement operator = previousSignificant(myNameLeaf);
        return operator == null;
    }

    private static boolean anyMember(List<PsiElement> functions) {
        for (PsiElement function : functions) {
            if (CDeclarations.enclosingAggregate(function) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * The functions of the name in the namespaces the classes and enums of the arguments are declared in -
     * {@code distance(a, b)} finds {@code geo::distance} for points of {@code geo}.
     */
    private List<PsiElement> argumentDependentLookup(List<PsiElement> arguments) {
        List<PsiElement> found = new ArrayList<>();
        List<PsiElement> namespaces = new ArrayList<>();
        for (PsiElement argument : arguments) {
            CType type = CTypes.typeOf(argument);
            PsiElement declaration = type.builtin() == CType.Builtin.DECLARED && !type.isTemplateParameter() ? type.declaration() : null;
            PsiElement namespace = declaration == null ? null : PsiTreeUtil.getParentOfType(declaration, CPsiNamespaceDeclaration.class);
            while (namespace != null && CDeclarations.nameOf(namespace) == null) {
                namespace = PsiTreeUtil.getParentOfType(namespace, CPsiNamespaceDeclaration.class);
            }
            if (namespace != null && !namespaces.contains(namespace)) {
                namespaces.add(namespace);
                found.addAll(CNameResolver.resolveMember(namespace, myNameLeaf.getText(), CDeclarationKind.FUNCTIONS));
            }
        }
        return found;
    }

    /**
     * Before {@code ::} a name is a class or a namespace; anywhere else a value - a variable, a function, or a type
     * constructed or cast to, {@code FVector2f(x, y)}.
     */
    private Set<CDeclarationKind> kinds() {
        CPsiReferenceExpression element = getElement();
        if (element.getParent() instanceof CPsiReferenceExpression) {
            PsiElement next = nextSignificant(element);
            if (next != null && next.getNode().getElementType() == CPsiTokens.DOUBLE_COLON) {
                return CDeclarationKind.SCOPES;
            }
        }
        return VALUE;
    }

    public PsiElement getNameLeaf() {
        return myNameLeaf;
    }

    private static @Nullable PsiElement previousSignificant(PsiElement element) {
        PsiElement previous = element.getPrevSibling();
        while (previous instanceof PsiWhiteSpace || previous instanceof PsiComment) {
            previous = previous.getPrevSibling();
        }
        return previous;
    }

    private static @Nullable PsiElement nextSignificant(PsiElement element) {
        PsiElement next = element.getNextSibling();
        while (next instanceof PsiWhiteSpace || next instanceof PsiComment) {
            next = next.getNextSibling();
        }
        return next;
    }
}
