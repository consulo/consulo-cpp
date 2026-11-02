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

import consulo.cpp.lang.psi.CPsiDoubleColonTypeRef;
import consulo.cpp.lang.psi.CPsiReferenceExpression;
import consulo.cpp.lang.psi.CPsiSuperClass;
import consulo.cpp.lang.psi.CPsiTokens;
import consulo.cpp.lang.psi.CPsiTypeRef;
import consulo.cpp.preprocessor.expand.CMacroArgumentLeafType;
import consulo.cpp.preprocessor.psi.CPreprocessorElementTypes;
import consulo.document.util.TextRange;
import consulo.language.impl.psi.ForeignLeafPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The names written in the arguments of a macro call - {@code calc::add} of {@code EXPECT_EQ(calc::add(2, 3), 5)}. The
 * call is one leaf of the file; what it expands to follows it as leaves of no length, which the C tree is made of. A
 * name of an argument is found there by where it was written, and means what its copy in the expansion means.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public final class CMacroArguments {
    /**
     * An identifier of an argument: its range in the call, and the first of its copies in the expansion.
     */
    public record Name(TextRange rangeInCall, PsiElement expanded) {
    }

    private CMacroArguments() {
    }

    /**
     * The identifiers the arguments of {@code call} pass into its expansion, each once, in the order written.
     */
    public static List<Name> namesOf(PsiElement call) {
        CharSequence text = call.getNode().getChars();
        Map<Integer, Name> byOffset = new LinkedHashMap<>();
        for (PsiElement leaf = PsiTreeUtil.nextLeaf(call); leaf instanceof ForeignLeafPsiElement; leaf = PsiTreeUtil.nextLeaf(leaf)) {
            // the leaf's own type is the token's; the foreign type keeps where the argument was written
            if (!(((ForeignLeafPsiElement) leaf).getForeignType() instanceof CMacroArgumentLeafType type) || type.getDelegate() != CPsiTokens.IDENTIFIER) {
                continue;
            }
            int offset = type.getArgumentOffset();
            String name = type.getValue().toString();
            // written there: a directive inside the call would have moved the text
            if (offset + name.length() > text.length() || !name.contentEquals(text.subSequence(offset, offset + name.length()))) {
                continue;
            }
            byOffset.putIfAbsent(offset, new Name(new TextRange(offset, offset + name.length()), leaf));
        }
        return new ArrayList<>(byOffset.values());
    }

    /**
     * Where in the file a token of an expansion was written, when an argument of the call passed it; {@code -1} else.
     */
    public static int offsetWritten(PsiElement expandedLeaf) {
        if (!(expandedLeaf instanceof ForeignLeafPsiElement foreign) || !(foreign.getForeignType() instanceof CMacroArgumentLeafType type)) {
            return -1;
        }
        PsiElement call = PsiTreeUtil.prevLeaf(expandedLeaf);
        while (call instanceof ForeignLeafPsiElement) {
            call = PsiTreeUtil.prevLeaf(call);
        }
        if (call == null || call.getNode().getElementType() != CPreprocessorElementTypes.MACRO_INVOCATION) {
            return -1;
        }
        return call.getTextRange().getStartOffset() + type.getArgumentOffset();
    }

    /**
     * What the copy of an argument's name in the expansion means: the declaration its reference resolves to, or the
     * declaration it names.
     */
    public static @Nullable PsiElement resolveExpanded(PsiElement leaf) {
        PsiElement parent = leaf.getParent();
        if (parent instanceof CPsiReferenceExpression referenceExpression && parent.getLastChild() == leaf) {
            CReferenceExpressionReference reference = CReferenceExpressionReference.forExpanded(referenceExpression, leaf);
            return reference.resolve();
        }
        if (parent instanceof CPsiTypeRef || parent instanceof CPsiDoubleColonTypeRef || parent instanceof CPsiSuperClass) {
            CNameResolver.NameChain chain = CNameResolver.nameChainOf(parent);
            int index = chain.segments().indexOf(leaf);
            if (index < 0) {
                return null;
            }
            Set<CDeclarationKind> lastKinds = parent instanceof CPsiDoubleColonTypeRef ? CDeclarationKind.SCOPES : CDeclarationKind.TYPES;
            List<@Nullable PsiElement> targets = CNameResolver.resolveChain(parent, chain, lastKinds);
            return index < targets.size() ? targets.get(index) : null;
        }
        if (parent != null && CDeclarations.nameIdentifier(parent) == leaf) {
            // a declaration the macro makes, named by an argument
            return parent;
        }
        return null;
    }
}
