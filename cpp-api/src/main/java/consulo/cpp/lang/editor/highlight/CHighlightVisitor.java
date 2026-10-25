package consulo.cpp.lang.editor.highlight;

import consulo.cpp.lang.psi.CPsiEnumConstant;
import consulo.cpp.lang.psi.CPsiTokens;
import consulo.cpp.lang.psi.visitor.CPsiElementVisitor;
import consulo.cpp.preprocessor.psi.CPreprocessorMacroReference;
import consulo.document.util.TextRange;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoHolder;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.editor.rawHighlight.HighlightVisitor;
import consulo.language.impl.psi.ForeignLeafPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author VISTALL
 * @since 11:11/2020-08-01
 */
public class CHighlightVisitor extends CPsiElementVisitor implements HighlightVisitor {
    private HighlightInfoHolder myHolder;

    @Override
    public void visit(@NotNull PsiElement element) {
        element.accept(this);
    }

    @Override
    public void visitPreprocessorMacroReference(CPreprocessorMacroReference reference) {
        // the macro name only: the call of a function-like macro also holds its arguments
        TextRange nameRange = reference.getRangeInElement().shiftRight(reference.getElement().getTextRange().getStartOffset());
        if (expandsToKeywordsOnly(reference.getElement())) {
            myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).textAttributes(CSyntaxHighlighter.KEYWORD).range(nameRange).create());
            return;
        }

        PsiElement element = reference.resolve();
        if (element != null) {
            HighlightUtil.highlight(element, nameRange, myHolder);
        }
    }

    /**
     * Whether a macro use expands to keywords only - {@code bool} to {@code _Bool}, {@code INLINE} to {@code static inline}:
     * such a use reads as the keywords it stands for. The expansion follows the use in the tree as zero-length foreign
     * leaves, with nested macros already expanded; a use in a directive, or of a macro expanding to nothing, has none.
     */
    private static boolean expandsToKeywordsOnly(PsiElement use) {
        boolean expanded = false;
        for (PsiElement leaf = PsiTreeUtil.nextLeaf(use); leaf instanceof ForeignLeafPsiElement; leaf = PsiTreeUtil.nextLeaf(leaf)) {
            if (!CPsiTokens.KEYWORD_SET.contains(leaf.getNode().getElementType())) {
                return false;
            }
            expanded = true;
        }
        return expanded;
    }

    @Override
    public void visitEnumConstant(CPsiEnumConstant element) {
        PsiElement nameElement = element.getNameIdentifier();
        if (nameElement != null) {
            HighlightUtil.highlight(element, nameElement, myHolder);
        }
    }

    @Override
    public boolean analyze(@NotNull PsiFile file, boolean updateWholeFile, @NotNull HighlightInfoHolder holder, @NotNull Runnable action) {
        myHolder = holder;
        action.run();
        return true;
    }
}
