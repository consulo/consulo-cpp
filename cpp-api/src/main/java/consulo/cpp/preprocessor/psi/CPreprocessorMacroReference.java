package consulo.cpp.preprocessor.psi;

import consulo.language.psi.OuterLanguageElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import consulo.cpp.lang.psi.CPsiElement;

/**
 * @author VISTALL
 * @since 21:45/2020-07-31
 */
public interface CPreprocessorMacroReference extends CPsiElement, OuterLanguageElement, PsiReference
{
	@NotNull
	PsiElement getReferenceElement();
}
