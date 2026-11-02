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
package consulo.cpp.lang.editor.highlight;

import consulo.annotation.access.RequiredReadAction;
import consulo.cpp.lang.psi.CPsiArgumentList;
import consulo.cpp.lang.psi.CPsiCallExpression;
import consulo.cpp.lang.psi.CPsiReferenceExpression;
import consulo.cpp.lang.psi.CPsiTokens;
import consulo.cpp.lang.psi.resolve.CDeclarationKind;
import consulo.cpp.lang.psi.resolve.CDeclarations;
import consulo.cpp.lang.psi.resolve.COverloads;
import consulo.cpp.lang.psi.resolve.CReferenceExpressionReference;
import consulo.cpp.lang.psi.resolve.CType;
import consulo.cpp.lang.psi.resolve.CTypes;
import consulo.cpp.localize.CLocalize;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Reports a call no function of its name can take: the wrong number of arguments, or an argument which converts to no
 * parameter. Only what is certain is reported - both types of a mismatch known, no candidate depending on what the
 * plugin does not compute - since a file is seen without what its build defines.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public final class CCallChecker {
    private CCallChecker() {
    }

    @RequiredReadAction
    public static void check(PsiElement element, BiConsumer<TextRange, LocalizeValue> sink) {
        if (!(element instanceof CPsiCallExpression call) || !(call.getFirstChild() instanceof CPsiReferenceExpression callee)) {
            return;
        }
        CReferenceExpressionReference reference = CReferenceExpressionReference.of(callee);
        if (reference == null) {
            return;
        }
        List<PsiElement> functions = new ArrayList<>();
        for (ResolveResult result : reference.multiResolve(false)) {
            PsiElement target = result.getElement();
            if (target == null) {
                continue;
            }
            if (result.isValidResult()) {
                // a viable function, or a value or a type called
                return;
            }
            if (CDeclarations.kindOf(target) == CDeclarationKind.FUNCTION) {
                functions.add(target);
            }
        }
        if (functions.isEmpty()) {
            return;
        }

        List<PsiElement> arguments = CTypes.argumentsOf(call);
        List<COverloads.Candidate> candidates = COverloads.rank(functions, arguments, CTypes.receiverOf(callee));
        for (COverloads.Candidate candidate : candidates) {
            if (candidate.isViable()) {
                return;
            }
        }

        PsiElement argumentList = call.getLastChild() instanceof CPsiArgumentList list ? list : call;
        TextRange range = argumentList.getTextRange();
        if (range.isEmpty() || isIncomplete(argumentList)) {
            return;
        }
        String name = reference.getNameLeaf().getText();
        if (distinct(candidates) == 1) {
            COverloads.Problem problem = candidates.get(0).problem();
            if (problem != null && problem.arity()) {
                sink.accept(range, CLocalize.callArgumentCount(name, expectedCount(functions.get(0)), arguments.size()));
                return;
            }
            if (problem != null && problem.argumentType() != null && problem.parameterType() != null) {
                PsiElement argument = arguments.get(problem.argument());
                sink.accept(argument.getTextRange().isEmpty() ? range : argument.getTextRange(), CLocalize.callArgumentType(problem.argument() + 1,
                    problem.argumentType().presentableText(), problem.parameterType().presentableText()));
                return;
            }
        }
        sink.accept(range, CLocalize.callNoMatchingFunction(name + "(" + presentableTypes(arguments) + ")"));
    }

    /**
     * A call being typed - no closing parenthesis, an error inside: what it will pass is not known yet.
     */
    private static boolean isIncomplete(PsiElement argumentList) {
        PsiElement last = argumentList.getLastChild();
        return last == null || last.getNode().getElementType() != CPsiTokens.RPARENTH
            || PsiTreeUtil.findChildOfType(argumentList, PsiErrorElement.class) != null;
    }

    /**
     * The functions among the candidates, a declaration and its definition counted once.
     */
    private static int distinct(List<COverloads.Candidate> candidates) {
        List<String> signatures = new ArrayList<>();
        for (COverloads.Candidate candidate : candidates) {
            String signature = signatureOf(candidate.function());
            if (!signatures.contains(signature)) {
                signatures.add(signature);
            }
        }
        return signatures.size();
    }

    private static String signatureOf(PsiElement function) {
        StringBuilder signature = new StringBuilder();
        for (PsiElement parameter : CTypes.parametersOf(function)) {
            signature.append(CTypes.declaredType(parameter).presentableText()).append(',');
        }
        return signature.toString();
    }

    /**
     * How many arguments a function takes: {@code 2}, {@code 1 to 3} with defaults, {@code at least 2} with
     * {@code ...}.
     */
    private static String expectedCount(PsiElement function) {
        int required = 0;
        int all = 0;
        boolean variadic = false;
        for (PsiElement parameter : CTypes.parametersOf(function)) {
            if (parameter.getNode().findChildByType(CPsiTokens.ELLIPSIS) != null) {
                variadic = true;
                continue;
            }
            all++;
            if (parameter.getNode().findChildByType(CPsiTokens.EQ) == null) {
                required = all;
            }
        }
        if (variadic) {
            return CLocalize.callArgumentCountAtLeast(required).get();
        }
        return required == all ? String.valueOf(all) : CLocalize.callArgumentCountRange(required, all).get();
    }

    private static String presentableTypes(List<PsiElement> arguments) {
        StringBuilder text = new StringBuilder();
        for (PsiElement argument : arguments) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            CType type = CTypes.typeOf(argument).withReference(false);
            text.append(type.presentableText());
        }
        return text.toString();
    }
}
