package consulo.cpp.lang.psi.impl;

import org.jspecify.annotations.Nullable;

import consulo.cpp.lang.psi.resolve.CScopes;
import consulo.cpp.lang.psi.resolve.CTemplateHeadReferences;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;

import consulo.language.psi.resolve.ResolveState;

import consulo.language.psi.resolve.PsiScopeProcessor;

import consulo.language.file.FileViewProvider;
import consulo.language.impl.psi.PsiFileBase;
import consulo.virtualFileSystem.fileType.FileType;
import org.jetbrains.annotations.NotNull;
import consulo.cpp.lang.CLanguage;
import consulo.cpp.lang.psi.CPsiDeclaration;
import consulo.cpp.lang.psi.CPsiFile;

import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 16:37/2020-07-31
 */
public class CFileImpl extends PsiFileBase implements CPsiFile
{
	public CFileImpl(@NotNull FileViewProvider viewProvider)
	{
		super(viewProvider, CLanguage.INSTANCE);
	}

	@Override
	public @NotNull FileType getFileType()
	{
		return getViewProvider().getFileType();
	}

	@Override
	public @NotNull CPsiDeclaration[] getDeclarations()
	{
		return findChildrenByClass(CPsiDeclaration.class);
	}

    @Override
    public boolean processDeclarations(PsiScopeProcessor processor, ResolveState state, @Nullable PsiElement lastParent, PsiElement place) {
        return CScopes.processFile(this, processor, state);
    }

	/**
	 * The references of the defaults of the template heads at its top level.
	 */
	@Override
	public PsiReference[] getReferences()
	{
		List<PsiReference> references = new ArrayList<>();
		CTemplateHeadReferences.addDefaults(this, references);
		return references.toArray(PsiReference.EMPTY_ARRAY);
	}
}
