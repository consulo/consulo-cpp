package consulo.cpp.preprocessor.psi.impl;

import java.util.Collections;
import consulo.cpp.preprocessor.expand.ExpandedMacro;
import consulo.cpp.preprocessor.psi.CPreprocessorMacroReference;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.impl.psi.ASTWrapperPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import consulo.cpp.lang.psi.CPsiTokens;
import consulo.cpp.lang.psi.visitor.CPsiElementVisitor;

/**
 * @author VISTALL
 * @since 21:44/2020-07-31
 */
public class CPreprocessorMacroReferenceImpl extends ASTWrapperPsiElement implements CPreprocessorMacroReference
{
	public CPreprocessorMacroReferenceImpl(@NotNull ASTNode node)
	{
		super(node);
	}

	@Override
	public void accept(@NotNull PsiElementVisitor visitor)
	{
		if(visitor instanceof CPsiElementVisitor)
		{
			((CPsiElementVisitor) visitor).visitPreprocessorMacroReference(this);
		}
		else
		{
			visitor.visitElement(this);
		}
	}

	@NotNull
	public PsiElement getReferenceElement()
	{
		return findNotNullChildByType(CPsiTokens.IDENTIFIER);
	}

	@Override
	public @NotNull PsiElement getElement()
	{
		return this;
	}

	@Override
	public @NotNull TextRange getRangeInElement()
	{
		// the name only: a call of a function-like macro also holds its arguments
		return new TextRange(0, getReferenceElement().getTextLength());
	}

	@Override
	public
	@Nullable
	PsiElement resolve()
	{
		ExpandedMacro macro = findMacro();
		if(macro == null)
		{
			// FIXME [VISTALL] this is unknown case, when psi logic different from parser
			return null;
		}
		return macro.getElement();
	}

	@Nullable
	private ExpandedMacro findMacro()
	{
		return CMacroReferenceResolver.findMacro(this, getCanonicalText());
	}

	@Override
	public PsiReference getReference()
	{
		return this;
	}

	@Override
	public @NotNull String getCanonicalText()
	{
		return getReferenceElement().getText();
	}

	@Override
	public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException
	{
		return null;
	}

	@Override
	public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException
	{
		return null;
	}

	@Override
	public boolean isReferenceTo(@NotNull PsiElement element)
	{
		return element.isEquivalentTo(resolve());
	}

	@Override
	public boolean isSoft()
	{
		return false;
	}
}
