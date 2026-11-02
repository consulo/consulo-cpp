package consulo.cpp.preprocessor.fileProvider;

import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiElement;
import consulo.language.psi.OuterLanguageElement;
import consulo.document.util.TextRange;
import consulo.annotation.access.RequiredReadAction;
import consulo.cpp.preprocessor.CPreprocessorLanguage;
import consulo.cpp.preprocessor.psi.CPreprocessorMacroReference;
import consulo.cpp.preprocessor.psi.impl.CPreprocessorFileImpl;
import consulo.language.Language;
import consulo.language.impl.file.MultiplePsiFilesPerDocumentFileViewProvider;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import consulo.cpp.lang.CLanguage;

import java.util.Set;

/**
 * @author VISTALL
 * @since 19:21/2020-07-15
 */
public class CFileViewProvider extends MultiplePsiFilesPerDocumentFileViewProvider
{
	CFileViewProvider(@NotNull PsiManager manager, @NotNull VirtualFile virtualFile, boolean eventSystemEnabled)
	{
		super(manager, virtualFile, eventSystemEnabled);
	}

	@Override
	@NotNull
	public Language getBaseLanguage()
	{
		return CLanguage.INSTANCE;
	}

	@Override
	@NotNull
	public Set<Language> getLanguages()
	{
		return Set.of(CLanguage.INSTANCE, CPreprocessorLanguage.INSTANCE);
	}

	@Override
	public boolean hasLanguage(@NotNull Language language)
	{
		return language == CLanguage.INSTANCE || language == CPreprocessorLanguage.INSTANCE;
	}

	@Override
	protected
	@Nullable
	PsiFile createFile(@NotNull Language lang)
	{
		if(lang == CPreprocessorLanguage.INSTANCE)
		{
			return new CPreprocessorFileImpl(this);
		}
		return super.createFile(lang);
	}

	@Override
	@NotNull
	protected MultiplePsiFilesPerDocumentFileViewProvider cloneInner(@NotNull VirtualFile fileCopy)
	{
		return new CFileViewProvider(getManager(), fileCopy, false);
	}

	/**
	 * The element of the C tree there: in code it is the one which means something - the preprocessor tree holds code as
	 * plain tokens. In a directive the C tree has only an outer element, and the preprocessor tree answers.
	 */
	@Override
	@RequiredReadAction
	public @Nullable PsiElement findElementAt(int offset)
	{
		PsiElement element = findElementAt(getPsi(CLanguage.INSTANCE), offset);
		if(element != null && isCodeOfCTree(element))
		{
			return element;
		}
		return super.findElementAt(offset);
	}

	/**
	 * The reference of the C tree there - the first one covering the offset, from the leaf up; a macro call holds those
	 * of the names its arguments pass. Elsewhere, in a directive, the preprocessor tree's.
	 */
	@Override
	@RequiredReadAction
	public @Nullable PsiReference findReferenceAt(int offset)
	{
		PsiElement leaf = findElementAt(getPsi(CLanguage.INSTANCE), offset);
		if(leaf != null && isCodeOfCTree(leaf))
		{
			for(PsiElement element = leaf; element != null && !(element instanceof PsiFile); element = element.getParent())
			{
				int inElement = offset - element.getTextRange().getStartOffset();
				for(PsiReference reference : element.getReferences())
				{
					TextRange range = reference.getRangeInElement();
					if(!range.isEmpty() && range.getStartOffset() <= inElement && inElement <= range.getEndOffset())
					{
						return reference;
					}
				}
			}
		}
		return super.findReferenceAt(offset);
	}

	/**
	 * Code of the C tree - not a directive's outer element. A macro call is outer to the C language too, but it is code:
	 * the name of the macro and the names its arguments pass are references there.
	 */
	private static boolean isCodeOfCTree(PsiElement element)
	{
		return !(element instanceof OuterLanguageElement) || element instanceof CPreprocessorMacroReference;
	}
}
