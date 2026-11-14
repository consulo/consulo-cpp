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
 * The references of the names in the default arguments of template heads - {@code less} and {@code T} of
 * {@code template <typename T, typename Compare = less<T>>}. A head is tokens of the file, the namespace or the class
 * around the declaration it is of, so the references are that element's, looked up from the declaration, where the
 * head's parameters are visible.
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
     * Adds the references of the defaults of the head at {@code keyword}; the last token of the head, {@code null} when
     * it does not end.
     */
    private static @Nullable PsiElement head(PsiElement container, PsiElement keyword, List<PsiReference> references) {
        PsiElement open = next(keyword);
        if (open == null || open.getNode().getElementType() != CPsiTokens.LT) {
            return keyword;
        }
        List<List<PsiElement>> chains = new ArrayList<>();
        List<Boolean> globals = new ArrayList<>();
        List<PsiElement> segments = new ArrayList<>();
        boolean global = false;
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
                if (inDefault && !skipChain) {
                    if (previous != CPsiTokens.DOUBLE_COLON && !segments.isEmpty()) {
                        chains.add(List.copyOf(segments));
                        globals.add(global);
                        segments.clear();
                        global = false;
                    }
                    segments.add(element);
                }
            }
            else if (type == CPsiTokens.DOUBLE_COLON) {
                if (previous == CPsiTokens.GT || previous == CPsiTokens.GTGT) {
                    // typename A<T>::type
                    skipChain = true;
                }
                else if (previous != CPsiTokens.IDENTIFIER) {
                    global = true;
                }
            }
            else {
                if (!segments.isEmpty()) {
                    chains.add(List.copyOf(segments));
                    globals.add(global);
                    segments.clear();
                }
                global = false;
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
            for (int i = 0; i < chains.size(); i++) {
                CRawNameReferences.addChainAt(container, new CNameResolver.NameChain(chains.get(i), globals.get(i)), KINDS, declaration,
                    references);
            }
        }
        return end;
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
