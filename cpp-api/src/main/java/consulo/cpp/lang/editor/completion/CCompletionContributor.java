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
package consulo.cpp.lang.editor.completion;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.cpp.lang.CLanguage;
import consulo.cpp.lang.psi.CPsiTokens;
import consulo.cpp.lang.psi.resolve.CCompletionVariants;
import consulo.cpp.lang.psi.resolve.CDeclarationKind;
import consulo.cpp.lang.psi.resolve.CDeclarations;
import consulo.cpp.lang.psi.resolve.CType;
import consulo.cpp.lang.psi.resolve.CTypes;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.ParenthesesInsertHandler;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.pattern.StandardPatterns;
import consulo.language.psi.PsiElement;

import java.util.List;

/**
 * Completes the names of C/C++ code - the variables, functions, types and namespaces a name there may be, the members
 * after {@code .}, {@code ->} and {@code ::} - from what resolve sees, see {@link CCompletionVariants}.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
@ExtensionImpl
public class CCompletionContributor extends CompletionContributor {
    public CCompletionContributor() {
        extend(CompletionType.BASIC, StandardPatterns.psiElement(CPsiTokens.IDENTIFIER).withLanguage(CLanguage.INSTANCE),
            (parameters, context, result) -> addVariants(parameters, result));
    }

    @RequiredReadAction
    private static void addVariants(CompletionParameters parameters, CompletionResultSet result) {
        for (PsiElement declaration : CCompletionVariants.collect(parameters.getPosition())) {
            String name = CDeclarations.nameOf(declaration);
            if (name == null) {
                continue;
            }
            LookupElementBuilder lookup = LookupElementBuilder.create(declaration, name).withIcon(IconDescriptorUpdaters.getIcon(declaration, 0));
            CDeclarationKind kind = CDeclarations.kindOf(declaration);
            if (kind == CDeclarationKind.FUNCTION || kind == CDeclarationKind.VARIABLE) {
                CType type = CTypes.declaredType(declaration);
                if (!type.isUnknown()) {
                    lookup = lookup.withTypeText(type.presentableText());
                }
            }
            if (kind == CDeclarationKind.FUNCTION) {
                List<PsiElement> functionParameters = CTypes.parametersOf(declaration);
                lookup = lookup.withTailText(parameterText(functionParameters), true)
                    .withInsertHandler(ParenthesesInsertHandler.getInstance(!functionParameters.isEmpty()));
            }
            result.addElement(lookup);
        }
    }

    private static String parameterText(List<PsiElement> parameters) {
        StringBuilder text = new StringBuilder("(");
        for (int i = 0; i < parameters.size(); i++) {
            text.append(i == 0 ? "" : ", ").append(parameters.get(i).getText());
        }
        return text.append(')').toString();
    }

    @Override
    public Language getLanguage() {
        return CLanguage.INSTANCE;
    }
}
