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
import consulo.colorScheme.TextAttributesKey;
import consulo.cpp.lang.psi.*;
import consulo.cpp.lang.psi.resolve.CDeclarationKind;
import consulo.cpp.lang.psi.resolve.CDeclarations;
import consulo.cpp.lang.psi.resolve.CNameChainReference;
import consulo.cpp.lang.psi.resolve.CNameResolver;
import consulo.cpp.lang.psi.resolve.CReferenceBase;
import consulo.cpp.lang.psi.resolve.CReferenceExpressionReference;
import consulo.cpp.preprocessor.CPreprocessorLanguage;
import consulo.cpp.preprocessor.psi.impl.CMacroReferenceResolver;
import consulo.cpp.lang.psi.resolve.CMacroArguments;
import consulo.cpp.preprocessor.psi.CPreprocessorDefineDirective;
import consulo.cpp.preprocessor.psi.impl.CPreprocessorMacroInvocationImpl;
import consulo.cpp.preprocessor.psi.CPreprocessorIfBlock;
import consulo.cpp.preprocessor.psi.CPreprocessorUndefDirective;
import consulo.cpp.preprocessor.psi.CPsiSharpDefineValue;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * The colors of the names of the C tree by what they mean: a declaration by what it declares, a use by what it
 * resolves to. A name not found - declared in a header not seen, made by a macro not expanded - keeps the color of an
 * identifier, and is not reported: what the file cannot see is no error of the file.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public final class CSemanticHighlighting {
    private CSemanticHighlighting() {
    }

    /**
     * Reports the colored names of {@code element} itself - not of its children, which are visited on their own.
     */
    @RequiredReadAction
    public static void highlight(PsiElement element, BiConsumer<TextRange, TextAttributesKey> sink) {
        Set<TextRange> reported = new HashSet<>();
        BiConsumer<TextRange, TextAttributesKey> tracking = (range, key) -> {
            reported.add(range);
            sink.accept(range, key);
        };
        highlightOwn(element, tracking);
        // the names of its template arguments, its qualifiers, its initializers and captures: references of their own
        for (PsiReference reference : element.getReferences()) {
            if (!(reference instanceof CReferenceBase<?>)) {
                continue;
            }
            TextRange range = reference.getRangeInElement().shiftRight(element.getTextRange().getStartOffset());
            if (range.isEmpty() || reported.contains(range)) {
                continue;
            }
            TextAttributesKey key = keyOf(reference.resolve(), true);
            if (key != null) {
                tracking.accept(range, key);
            }
        }
    }

    private static void highlightOwn(PsiElement element, BiConsumer<TextRange, TextAttributesKey> sink) {
        if (element instanceof CPreprocessorDefineDirective define) {
            // #define LIMIT 10: the name it defines
            PsiElement name = define.getNameIdentifier();
            if (name != null) {
                report(name, CSyntaxHighlighter.COMPILER_VARIABLE, sink);
            }
            return;
        }
        if (element instanceof CPreprocessorMacroInvocationImpl call) {
            // the names the arguments of a macro call pass: colored as what they mean in the expansion
            int start = call.getTextRange().getStartOffset();
            for (CMacroArguments.Name name : CMacroArguments.namesOf(call)) {
                TextAttributesKey key = keyOf(CMacroArguments.resolveExpanded(name.expanded()), true);
                if (key != null) {
                    sink.accept(name.rangeInCall().shiftRight(start), key);
                }
            }
            return;
        }
        if (isMacroUseInDirective(element)) {
            report(element, CSyntaxHighlighter.COMPILER_VARIABLE, sink);
            return;
        }
        PsiElement nameIdentifier = CDeclarations.nameIdentifier(element);
        if (nameIdentifier != null) {
            report(nameIdentifier, keyOf(element, false), sink);
        }

        if (element instanceof CPsiReferenceExpression referenceExpression) {
            CReferenceExpressionReference reference = CReferenceExpressionReference.of(referenceExpression);
            if (reference != null) {
                report(reference.getNameLeaf(), keyOf(reference.resolve(), true), sink);
            }
        }
        else if (element instanceof CPsiTypeRef || element instanceof CPsiDoubleColonTypeRef || element instanceof CPsiSuperClass
            || CDeclarations.usingOf(element) == CDeclarations.Using.DIRECTIVE || CDeclarations.usingOf(element) == CDeclarations.Using.DECLARATION) {
            highlightNameChain(element, sink);
        }
    }

    private static void highlightNameChain(PsiElement element, BiConsumer<TextRange, TextAttributesKey> sink) {
        if (!(element.getReference() instanceof CNameChainReference reference)) {
            return;
        }
        List<PsiElement> segments = reference.getChain().segments();
        if (segments.size() > 1) {
            CNameResolver.NameChain qualifiers = new CNameResolver.NameChain(segments.subList(0, segments.size() - 1), reference.getChain().global());
            List<@Nullable PsiElement> targets = CNameResolver.resolveChain(element, qualifiers, CDeclarationKind.SCOPES, reference.getState());
            for (int i = 0; i < targets.size(); i++) {
                report(segments.get(i), keyOf(targets.get(i), true), sink);
            }
        }
        report(segments.get(segments.size() - 1), keyOf(reference.resolve(), true), sink);
    }

    /**
     * A name in a directive - the condition of {@code #if}, the body of a {@code #define}, {@code #undef} - which a macro
     * of the file defines: {@code LIMIT} of {@code #if defined(LIMIT) && LIMIT > 2}.
     */
    private static boolean isMacroUseInDirective(PsiElement element) {
        if (element.getFirstChild() != null || element.getContainingFile().getLanguage() != CPreprocessorLanguage.INSTANCE) {
            return false;
        }
        PsiElement parent = element.getParent();
        if (!(parent instanceof CPreprocessorIfBlock || parent instanceof CPsiSharpDefineValue || parent instanceof CPreprocessorUndefDirective)) {
            return false;
        }
        String text = element.getText();
        if (!IDENTIFIER.matcher(text).matches() || "defined".equals(text)) {
            return false;
        }
        return CMacroReferenceResolver.macroNames(element).contains(text);
    }

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z_0-9]*");

    private static void report(PsiElement name, @Nullable TextAttributesKey key, BiConsumer<TextRange, TextAttributesKey> sink) {
        TextRange range = name.getTextRange();
        // a name a macro expansion made has no text of its own in the file
        if (key != null && !range.isEmpty()) {
            sink.accept(range, key);
        }
    }

    /**
     * @param usage whether the name uses the declaration rather than declares it: a call colors apart from a
     *              definition
     */
    public static @Nullable TextAttributesKey keyOf(@Nullable PsiElement declaration, boolean usage) {
        if (declaration == null) {
            return null;
        }
        CDeclarationKind kind = CDeclarations.kindOf(declaration);
        if (kind == null) {
            return null;
        }
        if (declaration.getFirstChild() == null) {
            // a template parameter: typename T, int N
            return kind == CDeclarationKind.TYPE ? CSyntaxHighlighter.TEMPLATE_PARAMETER : CSyntaxHighlighter.PARAMETER;
        }
        return switch (kind) {
            case VARIABLE -> variableKey(declaration);
            case FUNCTION -> CDeclarations.isMemberFunction(declaration)
                ? CSyntaxHighlighter.MEMBER_FUNCTION
                : usage ? CSyntaxHighlighter.FUNCTION_CALL : CSyntaxHighlighter.FUNCTION_DECLARATION;
            case TYPE -> CDeclarations.isTypeAlias(declaration) ? CSyntaxHighlighter.TYPE_ALIAS : CSyntaxHighlighter.CLASS_NAME;
            case NAMESPACE -> CSyntaxHighlighter.NAMESPACE;
        };
    }

    private static TextAttributesKey variableKey(PsiElement variable) {
        if (variable instanceof CPsiEnumConstant) {
            return CSyntaxHighlighter.CONSTANT;
        }
        if (variable instanceof CPsiParameter) {
            return CSyntaxHighlighter.PARAMETER;
        }
        if (variable instanceof CPsiLocalVariable) {
            return CSyntaxHighlighter.LOCAL_VARIABLE;
        }
        if (variable instanceof CPsiDeclarationField || CDeclarations.enclosingAggregate(variable) != null) {
            return CDeclarations.isStatic(variable) ? CSyntaxHighlighter.STATIC_FIELD : CSyntaxHighlighter.FIELD;
        }
        return CSyntaxHighlighter.GLOBAL_VARIABLE;
    }
}
