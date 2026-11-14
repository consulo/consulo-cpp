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
import consulo.language.psi.util.LanguageCachedValueUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The references of the names in template heads - {@code less} and {@code T} of
 * {@code template <typename T, typename Compare = less<T>>}, the concept of {@code template <HasLength T>}. A head is
 * tokens of the file, the namespace or the class around the declaration it is of, so the references are that
 * element's, looked up from the declaration, where the head's parameters are visible.
 *
 * @author VISTALL
 * @since 2026-11-14
 */
public final class CTemplateHeadReferences {
    /**
     * A default is a type or a constant.
     */
    private static final Set<CDeclarationKind> KINDS = Set.copyOf(EnumSet.of(CDeclarationKind.TYPE, CDeclarationKind.VARIABLE, CDeclarationKind.FUNCTION));

    private CTemplateHeadReferences() {
    }

    /**
     * The references of the defaults of the heads among {@code container}'s children, computed once per change of PSI:
     * a file asks for them at each lookup of a reference in it.
     */
    public static void addDefaults(PsiElement container, List<PsiReference> references) {
        references.addAll(LanguageCachedValueUtil.getProjectPsiDependentCache(container, CTemplateHeadReferences::compute));
    }

    private static List<PsiReference> compute(PsiElement container) {
        List<PsiReference> references = new ArrayList<>();
        for (PsiElement child = container.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null && child.getNode().getElementType() == CPsiTokens.TEMPLATE_KEYWORD) {
                child = head(container, child, references);
                if (child == null) {
                    break;
                }
            }
        }
        return references.isEmpty() ? List.of() : List.copyOf(references);
    }

    /**
     * Adds the references of the names of the head at {@code keyword}; the last token of the head, {@code null} when it
     * does not end.
     */
    private static @Nullable PsiElement head(PsiElement container, PsiElement keyword, List<PsiReference> references) {
        PsiElement open = next(keyword);
        if (open == null || open.getNode().getElementType() != CPsiTokens.LT) {
            return keyword;
        }
        List<CNameResolver.NameChain> chains = new ArrayList<>();
        Chain chain = new Chain();
        boolean inDefault = false;
        boolean skipChain = false;
        int depth = 0;
        IElementType previous = null;
        PsiElement end = null;
        for (PsiElement element = open; element != null; element = next(element)) {
            if (element.getFirstChild() != null) {
                // an element: the head did not end before the declaration
                break;
            }
            IElementType type = element.getNode().getElementType();
            if (type == CPsiTokens.IDENTIFIER) {
                if (!skipChain && depth >= 1) {
                    if (previous != CPsiTokens.DOUBLE_COLON) {
                        chain.flush(type, chains);
                    }
                    chain.add(element, inDefault, depth);
                }
            }
            else if (type == CPsiTokens.DOUBLE_COLON) {
                if (previous == CPsiTokens.GT || previous == CPsiTokens.GTGT) {
                    // typename A<T>::type
                    skipChain = true;
                }
                else if (previous != CPsiTokens.IDENTIFIER) {
                    chain.global = true;
                }
            }
            else {
                chain.flush(type, chains);
                skipChain = type == CPsiTokens.DOT || type == CPsiTokens.ARROW;
                if (type == CPsiTokens.LT || type == CPsiTokens.LPARENTH) {
                    depth++;
                }
                else if (type == CPsiTokens.GT || type == CPsiTokens.RPARENTH) {
                    depth--;
                }
                else if (type == CPsiTokens.GTGT) {
                    depth -= 2;
                }
                else if (depth == 1 && type == CPsiTokens.COMMA) {
                    inDefault = false;
                }
                else if (depth == 1 && type == CPsiTokens.EQ) {
                    inDefault = true;
                }
                if (depth <= 0) {
                    end = element;
                    break;
                }
            }
            previous = type;
        }
        if (end == null) {
            return null;
        }
        PsiElement declaration = declarationAfter(end);
        if (declaration != null) {
            for (CNameResolver.NameChain found : chains) {
                CRawNameReferences.addChainAt(container, found, KINDS, declaration, references);
            }
        }
        return end;
    }

    /**
     * The names of a head being read: one is a reference in a default, in the arguments of a constraint, or as the
     * constraint or the type of a parameter - {@code HasLength} of {@code HasLength T}, {@code size_t} of
     * {@code std::size_t N} - not as the name of a parameter.
     */
    private static final class Chain {
        final List<PsiElement> segments = new ArrayList<>();
        boolean global;
        boolean inDefault;
        int depth;

        void add(PsiElement identifier, boolean inDefault, int depth) {
            if (segments.isEmpty()) {
                this.inDefault = inDefault;
                this.depth = depth;
            }
            segments.add(identifier);
        }

        void flush(IElementType next, List<CNameResolver.NameChain> chains) {
            if (!segments.isEmpty()
                && (inDefault || depth >= 2 || next == CPsiTokens.IDENTIFIER || next == CPsiTokens.ELLIPSIS || next == CPsiTokens.LT)) {
                chains.add(new CNameResolver.NameChain(List.copyOf(segments), global));
            }
            segments.clear();
            global = false;
        }
    }

    /**
     * The declaration a head ending at {@code end} is of: the next element, past the heads of a member template.
     */
    private static @Nullable PsiElement declarationAfter(PsiElement end) {
        for (PsiElement element = end.getNextSibling(); element != null; element = element.getNextSibling()) {
            if (element instanceof PsiWhiteSpace || element instanceof PsiComment) {
                continue;
            }
            if (element.getFirstChild() != null) {
                return element;
            }
            if (element.getNode().getElementType() == CPsiTokens.SEMICOLON || element.getNode().getElementType() == CPsiTokens.RBRACE) {
                return null;
            }
        }
        return null;
    }

    private static @Nullable PsiElement next(PsiElement element) {
        PsiElement next = element.getNextSibling();
        while (next instanceof PsiWhiteSpace || next instanceof PsiComment) {
            next = next.getNextSibling();
        }
        return next;
    }
}
