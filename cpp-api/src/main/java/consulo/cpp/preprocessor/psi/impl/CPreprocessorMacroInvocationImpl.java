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
package consulo.cpp.preprocessor.psi.impl;

import consulo.annotation.access.RequiredReadAction;
import consulo.cpp.lang.psi.visitor.CPsiElementVisitor;
import consulo.cpp.preprocessor.expand.ExpandedMacro;
import consulo.cpp.preprocessor.psi.CPreprocessorMacroReference;
import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import consulo.language.impl.psi.LeafPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import consulo.language.util.IncorrectOperationException;
import org.jspecify.annotations.Nullable;

/**
 * A macro use in the C tree: the name, and for a function-like macro its parenthesised arguments, as one leaf. The
 * tokens it expands to follow it as zero-length leaves.
 *
 * @author VISTALL
 * @since 2026-10-19
 */
public class CPreprocessorMacroInvocationImpl extends LeafPsiElement implements CPreprocessorMacroReference {
    public CPreprocessorMacroInvocationImpl(IElementType type, CharSequence text) {
        super(type, text);
    }

    @Override
    public void accept(PsiElementVisitor visitor) {
        if (visitor instanceof CPsiElementVisitor cVisitor) {
            cVisitor.visitPreprocessorMacroReference(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    @Override
    public PsiElement getReferenceElement() {
        return this;
    }

    @Override
    public PsiElement getElement() {
        return this;
    }

    /**
     * The name only - a call of a function-like macro also holds its arguments.
     */
    @Override
    public TextRange getRangeInElement() {
        return new TextRange(0, getCanonicalText().length());
    }

    @Override
    public String getCanonicalText() {
        CharSequence text = getChars();
        int end = 0;
        while (end < text.length() && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '_')) {
            end++;
        }
        return text.subSequence(0, end).toString();
    }

    @RequiredReadAction
    @Override
    public @Nullable PsiElement resolve() {
        ExpandedMacro macro = CMacroReferenceResolver.findMacro(this, getCanonicalText());
        return macro == null ? null : macro.getElement();
    }

    @Override
    public PsiReference getReference() {
        return this;
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        return this;
    }

    @Override
    public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
        return this;
    }

    @RequiredReadAction
    @Override
    public boolean isReferenceTo(PsiElement element) {
        return element.isEquivalentTo(resolve());
    }

    @Override
    public boolean isSoft() {
        return false;
    }

    @Override
    public String toString() {
        return "CPreprocessorMacroInvocation(" + getCanonicalText() + ")";
    }
}
