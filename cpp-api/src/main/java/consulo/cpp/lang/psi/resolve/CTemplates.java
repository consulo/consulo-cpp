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
import consulo.language.psi.PsiWhiteSpace;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The parameters of {@code template <typename T, int N>}. The parser leaves a template head as tokens before the
 * declaration it introduces, so a parameter is its identifier leaf: that is what a {@code T} of the declaration resolves
 * to.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public final class CTemplates {
    /**
     * @param type whether it is a type parameter - {@code typename T}, {@code class T}, {@code template <...> class T} -
     *             rather than a value, {@code int N}
     */
    public record Parameter(PsiElement name, boolean type) {
    }

    private CTemplates() {
    }

    /**
     * The parameters of the template heads right before {@code declaration}, outermost head first -
     * {@code template <class T> template <class U> void Box<T>::f(U)} has two.
     */
    public static List<Parameter> parametersOf(PsiElement declaration) {
        List<Parameter> parameters = new ArrayList<>();
        PsiElement end = previousSignificant(declaration);
        while (end != null && end.getNode().getElementType() == CPsiTokens.GT) {
            PsiElement keyword = headStart(end);
            if (keyword == null) {
                break;
            }
            parameters.addAll(0, parametersOfHead(keyword, end));
            end = previousSignificant(keyword);
        }
        return parameters;
    }

    /**
     * The parameter an identifier names, if it is the name of one in a template head.
     */
    public static @Nullable Parameter parameterOf(PsiElement identifier) {
        // a directory, a file of another language: no node of a C tree
        if (identifier.getNode() == null || identifier.getNode().getElementType() != CPsiTokens.IDENTIFIER || identifier.getFirstChild() != null) {
            return null;
        }
        // back to the '<' of the head holding it
        int depth = 0;
        for (PsiElement element = identifier.getPrevSibling(); element != null; element = element.getPrevSibling()) {
            if (isTrivia(element)) {
                continue;
            }
            if (element.getFirstChild() != null) {
                return null;
            }
            IElementType type = element.getNode().getElementType();
            if (type == CPsiTokens.GT) {
                depth++;
            }
            else if (type == CPsiTokens.LT) {
                if (depth > 0) {
                    depth--;
                    continue;
                }
                PsiElement keyword = previousSignificant(element);
                if (keyword == null || keyword.getNode().getElementType() != CPsiTokens.TEMPLATE_KEYWORD) {
                    return null;
                }
                PsiElement end = headEnd(element);
                if (end == null) {
                    return null;
                }
                for (Parameter parameter : parametersOfHead(keyword, end)) {
                    if (parameter.name() == identifier) {
                        return parameter;
                    }
                }
                return null;
            }
            else if (type == CPsiTokens.SEMICOLON || type == CPsiTokens.LBRACE || type == CPsiTokens.RBRACE) {
                return null;
            }
        }
        return null;
    }

    /**
     * The {@code template} keyword of the head ending at {@code end}, its {@code >}.
     */
    private static @Nullable PsiElement headStart(PsiElement end) {
        int depth = 0;
        for (PsiElement element = end; element != null; element = element.getPrevSibling()) {
            if (isTrivia(element)) {
                continue;
            }
            if (element.getFirstChild() != null) {
                return null;
            }
            IElementType type = element.getNode().getElementType();
            if (type == CPsiTokens.GT) {
                depth++;
            }
            else if (type == CPsiTokens.LT && --depth == 0) {
                PsiElement keyword = previousSignificant(element);
                return keyword != null && keyword.getNode().getElementType() == CPsiTokens.TEMPLATE_KEYWORD ? keyword : null;
            }
        }
        return null;
    }

    private static @Nullable PsiElement headEnd(PsiElement open) {
        int depth = 0;
        for (PsiElement element = open; element != null; element = element.getNextSibling()) {
            if (isTrivia(element)) {
                continue;
            }
            if (element.getFirstChild() != null) {
                return null;
            }
            IElementType type = element.getNode().getElementType();
            if (type == CPsiTokens.LT) {
                depth++;
            }
            else if (type == CPsiTokens.GT && --depth == 0) {
                return element;
            }
        }
        return null;
    }

    /**
     * The parameters between the {@code <} after {@code keyword} and {@code end}: each is named by its last identifier
     * at the top level before a default, {@code typename T = int}, {@code int N}, {@code typename... Ts}.
     */
    private static List<Parameter> parametersOfHead(PsiElement keyword, PsiElement end) {
        List<Parameter> parameters = new ArrayList<>();
        int depth = 0;
        PsiElement name = null;
        IElementType first = null;
        boolean inDefault = false;
        for (PsiElement element = keyword.getNextSibling(); element != null; element = element.getNextSibling()) {
            if (isTrivia(element)) {
                continue;
            }
            IElementType type = element.getNode().getElementType();
            boolean opens = type == CPsiTokens.LT || type == CPsiTokens.LPARENTH;
            boolean closes = type == CPsiTokens.GT || type == CPsiTokens.RPARENTH;
            if (depth == 1 && (type == CPsiTokens.COMMA || element == end)) {
                if (name != null) {
                    parameters.add(new Parameter(name, first == CPsiTokens.TYPENAME_KEYWORD || first == CPsiTokens.CLASS_KEYWORD
                        || first == CPsiTokens.TEMPLATE_KEYWORD));
                }
                name = null;
                first = null;
                inDefault = false;
            }
            else if (depth == 1 && !inDefault) {
                if (first == null) {
                    first = type;
                }
                if (type == CPsiTokens.EQ) {
                    inDefault = true;
                }
                else if (type == CPsiTokens.IDENTIFIER) {
                    name = element;
                }
            }
            if (opens) {
                depth++;
            }
            else if (closes) {
                depth--;
            }
            if (element == end) {
                break;
            }
        }
        return parameters;
    }

    private static @Nullable PsiElement previousSignificant(PsiElement element) {
        PsiElement previous = element.getPrevSibling();
        while (previous != null && isTrivia(previous)) {
            previous = previous.getPrevSibling();
        }
        return previous;
    }

    private static boolean isTrivia(PsiElement element) {
        return element instanceof PsiWhiteSpace || element instanceof PsiComment;
    }
}
