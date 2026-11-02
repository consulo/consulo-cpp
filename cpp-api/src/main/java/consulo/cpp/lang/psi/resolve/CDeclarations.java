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
import consulo.cpp.lang.psi.impl.CPsiDeclarationImpl;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What a declaration of the C tree declares: its name, its kind, its type. The tree holds one element per declarator -
 * {@code int a, b;} is two - and the name is the identifier among its own children; the type is the type reference of
 * the first declarator of the run.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public final class CDeclarations {
    private CDeclarations() {
    }

    /**
     * The three declarations {@code using} starts.
     */
    public enum Using {
        /**
         * {@code using Alias = Type;} - a type, as a typedef is.
         */
        ALIAS,
        /**
         * {@code using namespace ns;} - the members of {@code ns} become visible where it is.
         */
        DIRECTIVE,
        /**
         * {@code using ns::name;} - {@code name} is declared here as what it is there.
         */
        DECLARATION
    }

    public static @Nullable Using usingOf(PsiElement element) {
        if (!(element instanceof CPsiDeclaration) || element.getClass() != CPsiDeclarationImpl.class) {
            return null;
        }
        ASTNode node = element.getNode();
        ASTNode first = node.getFirstChildNode();
        while (first != null && (first.getPsi() instanceof CPsiModifierList || CPsiTokens.WHITE_SPACE_SET.contains(first.getElementType())
            || first.getPsi() instanceof PsiComment)) {
            first = first.getTreeNext();
        }
        if (first == null || first.getElementType() != CPsiTokens.USING_KEYWORD) {
            return null;
        }
        if (node.findChildByType(CPsiTokens.NAMESPACE_KEYWORD) != null) {
            return Using.DIRECTIVE;
        }
        return node.findChildByType(CPsiTokens.EQ) != null ? Using.ALIAS : Using.DECLARATION;
    }

    /**
     * A typedef or a {@code using Alias = Type;}: a name for a type spelled in its type reference.
     */
    public static boolean isTypeAlias(PsiElement element) {
        return element instanceof CPsiTypeDeclaration || usingOf(element) == Using.ALIAS;
    }

    public static @Nullable CDeclarationKind kindOf(PsiElement element) {
        if (element.getFirstChild() == null) {
            CTemplates.Parameter parameter = CTemplates.parameterOf(element);
            return parameter == null ? null : parameter.type() ? CDeclarationKind.TYPE : CDeclarationKind.VARIABLE;
        }
        if (element instanceof CPsiLocalVariable || element instanceof CPsiField || element instanceof CPsiDeclarationField
            || element instanceof CPsiParameter || element instanceof CPsiEnumConstant) {
            return CDeclarationKind.VARIABLE;
        }
        if (element instanceof CPsiDeclarationMethod) {
            return CDeclarationKind.FUNCTION;
        }
        if (isAggregate(element) || isTypeAlias(element)) {
            return CDeclarationKind.TYPE;
        }
        if (element instanceof CPsiNamespaceDeclaration) {
            return CDeclarationKind.NAMESPACE;
        }
        return null;
    }

    /**
     * A class, struct, union or enum - a declaration with members.
     */
    public static boolean isAggregate(PsiElement element) {
        return element instanceof CPsiClass || element instanceof CPsiStructDeclaration || element instanceof CPsiUnionDeclaration
            || element instanceof CPsiEnum;
    }

    /**
     * The identifier naming a declaration, {@code null} for an anonymous one - {@code namespace { }}, {@code struct { } s;},
     * an unnamed parameter. Of a declaration a macro expanded to, it is a leaf of no length in the source.
     */
    public static @Nullable PsiElement nameIdentifier(PsiElement declaration) {
        if (kindOf(declaration) == null) {
            return null;
        }
        if (declaration.getFirstChild() == null) {
            // a template parameter
            return declaration;
        }
        ASTNode identifier = declaration.getNode().findChildByType(CPsiTokens.IDENTIFIER);
        return identifier == null ? null : identifier.getPsi();
    }

    public static @Nullable String nameOf(PsiElement declaration) {
        PsiElement identifier = nameIdentifier(declaration);
        return identifier == null ? null : identifier.getText();
    }

    /**
     * The name a declaration makes visible in its scope: its own, or that of the last segment of
     * {@code using ns::name;}.
     */
    public static @Nullable String introducedName(PsiElement declaration) {
        if (usingOf(declaration) == Using.DECLARATION) {
            PsiElement last = CNameResolver.nameChainOf(declaration).last();
            return last == null ? null : last.getText();
        }
        return nameOf(declaration);
    }

    /**
     * The type reference of a variable or function: its own, or for a later declarator of {@code int a, b;} that of the
     * first one.
     */
    public static @Nullable CPsiTypeRef typeRefOf(PsiElement declaration) {
        for (PsiElement element = declaration; element != null; element = element.getPrevSibling()) {
            if (element != declaration && element.getClass() != declaration.getClass()) {
                if (isDeclaratorSeparator(element)) {
                    continue;
                }
                return null;
            }
            for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof CPsiTypeRef typeRef) {
                    return typeRef;
                }
            }
        }
        return null;
    }

    private static boolean isDeclaratorSeparator(PsiElement element) {
        IElementType type = element.getNode().getElementType();
        return type == CPsiTokens.COMMA || CPsiTokens.WHITE_SPACE_SET.contains(type) || element instanceof PsiComment;
    }

    public static boolean isStatic(PsiElement declaration) {
        CPsiModifierList modifiers = childOfType(declaration, CPsiModifierList.class);
        return modifiers != null && modifiers.hasModifier(CPsiTokens.STATIC_KEYWORD);
    }

    /**
     * Whether a function is a member of a class: declared in its body, or defined outside it with a qualified name which
     * does not name a namespace - {@code void Pawn::Tick()} whose class is in a header counts.
     */
    public static boolean isMemberFunction(PsiElement function) {
        if (function instanceof CPsiImplementingMethod) {
            CPsiDoubleColonTypeRef qualifier = childOfType(function, CPsiDoubleColonTypeRef.class);
            if (qualifier != null) {
                PsiElement owner = CNameResolver.resolveQualifiedOwner(qualifier);
                return !(owner instanceof CPsiNamespaceDeclaration);
            }
        }
        return enclosingAggregate(function) != null;
    }

    /**
     * The class, struct or union a declaration is a member of, through access sections; {@code null} at namespace scope.
     */
    public static @Nullable PsiElement enclosingAggregate(PsiElement declaration) {
        PsiElement parent = declaration.getParent();
        while (parent instanceof CPsiAccessorOwner) {
            parent = parent.getParent();
        }
        return parent != null && isAggregate(parent) && !(parent instanceof CPsiEnum) ? parent : null;
    }

    /**
     * The declarations a child of a scope - a block, a class body, a namespace, the file - introduces into it: itself,
     * the members of an access section, of an anonymous namespace, the constants of an unscoped enum and the aggregate a
     * typedef declares.
     */
    public static void collectIntroduced(PsiElement child, List<PsiElement> into) {
        if (child instanceof CPsiAccessorOwner) {
            for (PsiElement member = child.getFirstChild(); member != null; member = member.getNextSibling()) {
                collectIntroduced(member, into);
            }
            return;
        }
        if (usingOf(child) == Using.DECLARATION) {
            into.add(child);
            return;
        }
        CDeclarationKind kind = kindOf(child);
        if (kind == null) {
            return;
        }
        boolean named = nameIdentifier(child) != null;
        if (kind == CDeclarationKind.NAMESPACE && !named) {
            // namespace { ... }: its members are those of the enclosing scope
            for (PsiElement member = child.getFirstChild(); member != null; member = member.getNextSibling()) {
                collectIntroduced(member, into);
            }
            return;
        }
        if (named) {
            into.add(child);
        }
        if (child instanceof CPsiEnum enumElement && !isScopedEnum(enumElement)) {
            for (CPsiEnumConstant constant : enumElement.getConstants()) {
                into.add(constant);
            }
        }
        else if (child instanceof CPsiTypeDeclaration) {
            // typedef struct Point { ... } Point_t;
            for (PsiElement inner = child.getFirstChild(); inner != null; inner = inner.getNextSibling()) {
                if (isAggregate(inner)) {
                    collectIntroduced(inner, into);
                }
            }
        }
    }

    /**
     * {@code enum class E} and {@code enum struct E}: the constants are reached through {@code E::}.
     */
    public static boolean isScopedEnum(CPsiEnum element) {
        ASTNode node = element.getNode();
        return node.findChildByType(CPsiTokens.CLASS_KEYWORD) != null || node.findChildByType(CPsiTokens.STRUCT_KEYWORD) != null;
    }

    /**
     * The members a class, struct, union, enum or namespace block declares itself - not those of its bases, nor of other
     * blocks of the same namespace.
     */
    public static List<PsiElement> ownMembersOf(PsiElement owner) {
        List<PsiElement> members = new ArrayList<>();
        if (owner instanceof CPsiEnum enumElement) {
            members.addAll(List.of(enumElement.getConstants()));
            return members;
        }
        for (PsiElement child = owner.getFirstChild(); child != null; child = child.getNextSibling()) {
            collectIntroduced(child, members);
        }
        return members;
    }

    public static List<PsiElement> topLevelDeclarationsOf(PsiFile file) {
        return ownMembersOf(file);
    }

    /**
     * What all the uses of a local or a parameter are in: its function, its lambda, the handler of its {@code catch}, the
     * prototype it names a parameter of; {@code null} for a declaration seen outside its body.
     */
    public static @Nullable PsiElement localOwner(PsiElement declaration) {
        if (declaration instanceof CPsiParameter) {
            PsiElement list = declaration.getParent();
            return list instanceof CPsiParameterList ? list.getParent() : null;
        }
        if (declaration instanceof CPsiLocalVariable) {
            PsiElement owner = null;
            for (PsiElement element = declaration.getParent(); element != null && !(element instanceof PsiFile); element = element.getParent()) {
                if (element instanceof CPsiImplementingMethod) {
                    return element;
                }
                if (element instanceof CPsiCodeBlock || element instanceof CPsiCallExpression) {
                    owner = element;
                }
            }
            return owner;
        }
        return null;
    }

    static <T> @Nullable T childOfType(PsiElement element, Class<T> type) {
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
        }
        return null;
    }
}
