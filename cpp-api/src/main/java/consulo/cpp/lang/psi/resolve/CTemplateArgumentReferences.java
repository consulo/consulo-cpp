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
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiWhiteSpace;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The references of the names in the template arguments of a type, a base class or an expression - {@code Point} of
 * {@code std::vector<app::Point>}, {@code Circle} of {@code std::make_unique<Circle>(2.0)} - in a
 * {@code decltype(points)}, and of the qualifiers of its names, {@code std} of {@code std::string}. The arguments are tokens of the element they are written in, not elements of their own, so
 * each name, with the {@code a::b::} before it, is a reference of that element.
 *
 * @author VISTALL
 * @since 2026-11-14
 */
public final class CTemplateArgumentReferences {
    /**
     * An argument is a type or a constant: {@code std::array<int, Size>}.
     */
    private static final Set<CDeclarationKind> KINDS = Set.copyOf(EnumSet.of(CDeclarationKind.TYPE, CDeclarationKind.VARIABLE, CDeclarationKind.FUNCTION));

    private CTemplateArgumentReferences() {
    }

    /**
     * The element's own reference, if any, then those of the names in its template arguments.
     */
    public static PsiReference[] withArguments(PsiElement element, @Nullable PsiReference own) {
        List<PsiReference> references = new ArrayList<>();
        if (own != null) {
            references.add(own);
        }
        collect(element, references);
        return references.toArray(PsiReference.EMPTY_ARRAY);
    }

    /**
     * The references of the names in an element's template arguments and of the qualifiers of its names.
     */
    public static void addArguments(PsiElement element, List<PsiReference> references) {
        collect(element, references);
    }

    /**
     * The references of a name chain written as tokens of an element: of each qualifier, then of the whole, meaning
     * {@code kinds}.
     */
    public static void addChain(PsiElement element, CNameResolver.NameChain chain, Set<CDeclarationKind> kinds, List<PsiReference> references) {
        List<PsiElement> segments = chain.segments();
        for (int i = 1; i < segments.size(); i++) {
            add(element, new CNameResolver.NameChain(List.copyOf(segments.subList(0, i)), chain.global()), CDeclarationKind.SCOPES, references);
        }
        add(element, chain, kinds, references);
    }

    private static void collect(PsiElement element, List<PsiReference> references) {
        int level = 0;
        List<PsiElement> segments = new ArrayList<>();
        boolean global = false;
        // the names after A<T>:: or after a . or -> are members of what is before them, not found where the element is
        boolean skipChain = false;
        // the parentheses of decltype(points): its names are references as an argument's are
        int decltypeParens = 0;
        boolean afterDecltype = false;
        boolean segmentsInArgument = false;
        IElementType previous = null;
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof PsiWhiteSpace || child instanceof PsiComment || child.getNode() == null) {
                continue;
            }
            IElementType type = child.getNode().getElementType();
            boolean inArgument = level > 0 || decltypeParens > 0;
            if (type == CPsiTokens.IDENTIFIER) {
                if (!skipChain) {
                    if (previous != CPsiTokens.DOUBLE_COLON) {
                        flush(element, segments, global, segmentsInArgument, references);
                        global = false;
                    }
                    segmentsInArgument = inArgument;
                    segments.add(child);
                }
            }
            else if (type == CPsiTokens.DOUBLE_COLON) {
                if (previous != CPsiTokens.IDENTIFIER) {
                    if (previous == CPsiTokens.GT || previous == CPsiTokens.GTGT) {
                        // typename A<T>::type
                        skipChain = true;
                    }
                    else {
                        flush(element, segments, global, segmentsInArgument, references);
                        global = true;
                    }
                }
            }
            else {
                flush(element, segments, global, segmentsInArgument, references);
                global = false;
                skipChain = type == CPsiTokens.DOT || type == CPsiTokens.ARROW;
                if (type == CPsiTokens.LPARENTH && (afterDecltype || decltypeParens > 0)) {
                    decltypeParens++;
                }
                else if (type == CPsiTokens.RPARENTH && decltypeParens > 0) {
                    decltypeParens--;
                }
                else if (type == CPsiTokens.LT) {
                    level++;
                }
                else if (type == CPsiTokens.GT) {
                    level = Math.max(0, level - 1);
                }
                else if (type == CPsiTokens.GTGT) {
                    level = Math.max(0, level - 2);
                }
            }
            // typeid(Derived) as decltype(points)
            afterDecltype = type == CPsiTokens.DECLTYPE_KEYWORD || type == CPsiTokens.TYPEID_KEYWORD;
            previous = type;
        }
        flush(element, segments, global, segmentsInArgument, references);
    }

    /**
     * The references of a name chain: of each qualifier, {@code std} and {@code chrono} of {@code std::chrono::seconds},
     * and of the whole in an argument - outside one the whole is the element's own reference.
     */
    private static void flush(PsiElement element, List<PsiElement> segments, boolean global, boolean inArgument, List<PsiReference> references) {
        if (segments.isEmpty()) {
            return;
        }
        for (int i = 1; i < segments.size(); i++) {
            add(element, new CNameResolver.NameChain(List.copyOf(segments.subList(0, i)), global), CDeclarationKind.SCOPES, references);
        }
        if (inArgument) {
            add(element, new CNameResolver.NameChain(List.copyOf(segments), global), KINDS, references);
        }
        segments.clear();
    }

    private static void add(PsiElement element, CNameResolver.NameChain chain, Set<CDeclarationKind> kinds, List<PsiReference> references) {
        CNameChainReference reference = CNameChainReference.ofChain(element, chain, kinds);
        if (reference != null) {
            references.add(reference);
        }
    }
}
