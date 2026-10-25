package consulo.cpp.lang.psi.impl;

import consulo.cpp.preprocessor.expand.CMacroExpandingLexer;
import consulo.cpp.preprocessor.expand.PreprocessorExpandPsiBuilder;
import consulo.cpp.preprocessor.expand.PreprocessorExpander;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IFileElementType;
import consulo.language.lexer.Lexer;
import consulo.language.file.FileViewProvider;
import consulo.language.parser.ParserDefinition;
import consulo.language.parser.PsiBuilder;
import consulo.language.parser.PsiBuilderFactory;
import consulo.language.parser.PsiParser;
import consulo.language.psi.PsiElement;
import consulo.language.version.LanguageVersion;
import consulo.project.Project;
import org.jetbrains.annotations.NotNull;
import consulo.cpp.lang.CLanguage;

/**
 * @author VISTALL
 * @since 15:27/2020-08-15
 * <p>
 * File Element for C/C++ file but without preprocessor element removing
 */
public class CFileInnerElementType extends IFileElementType
{
	public static final CFileInnerElementType INSTANCE = new CFileInnerElementType();

	public CFileInnerElementType()
	{
		super(CLanguage.INSTANCE);
	}

	@Override
	protected ASTNode doParseContents(@NotNull ASTNode chameleon, @NotNull PsiElement psi)
	{
		FileViewProvider viewProvider = psi.getContainingFile().getViewProvider();

		PreprocessorExpander expander = viewProvider.getUserData(PreprocessorExpander.EXPANDER_KEY);

		Project project = psi.getProject();
		Language languageForParser = getLanguageForParser(psi);
		// the light file carries the version of the real file, see CFileElementType#createPsiFileFromSource. It is read
		// off the light file itself: its view provider has no event system, so the PSI file reports no virtual file and
		// would resolve the version again - for a file of no known type, the default C++ one, whatever the real file is
		LanguageVersion stored = viewProvider.getVirtualFile().getUserData(LanguageVersion.KEY);
		LanguageVersion languageVersion = stored != null && stored.getLanguage() == languageForParser ? stored : psi.getLanguageVersion();

		PsiBuilder builder;
		if(expander != null)
		{
			// macro uses are expanded by the lexer, the parser reads plain tokens
			Lexer lexer = new CMacroExpandingLexer(expander);
			builder = new PreprocessorExpandPsiBuilder(PsiBuilderFactory.getInstance().createBuilder(project, chameleon, lexer, languageForParser, languageVersion, chameleon.getChars()));
		}
		else
		{
			builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, null, languageForParser, languageVersion, chameleon.getChars());
		}

		PsiParser parser = ParserDefinition.forLanguage(languageForParser).createParser(languageVersion);
		ASTNode node = parser.parse(this, builder, languageVersion);
		return node.getFirstChildNode();
	}
}
