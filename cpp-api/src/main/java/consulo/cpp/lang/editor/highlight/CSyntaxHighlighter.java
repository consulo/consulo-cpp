/*
 * Copyright 2011 napile
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

import consulo.codeEditor.DefaultLanguageHighlighterColors;
import consulo.colorScheme.TextAttributesKey;
import consulo.cpp.lang.lexer.CHighlightingLexer;
import consulo.cpp.preprocessor.psi.CPreprocessorTokenTypes;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.editor.highlight.SyntaxHighlighterBase;
import consulo.language.lexer.Lexer;
import org.jetbrains.annotations.NotNull;
import consulo.cpp.lang.psi.CPsiTokens;
import consulo.cpp.lang.version.CLanguageVersion;

import java.util.HashMap;
import java.util.Map;

/**
 * @author VISTALL
 * @since 5:06/10.12.2011
 */
public class CSyntaxHighlighter extends SyntaxHighlighterBase
{
	public static final TextAttributesKey KEYWORD = TextAttributesKey.createTextAttributesKey("C.KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
	public static final TextAttributesKey LIGHT_KEYWORD = TextAttributesKey.createTextAttributesKey("C.LIGHT_KEYWORD");

	public static final TextAttributesKey STRING = TextAttributesKey.createTextAttributesKey("C.STRING", DefaultLanguageHighlighterColors.STRING);
	public static final TextAttributesKey LIGHT_STRING = TextAttributesKey.createTextAttributesKey("C.LIGHT_STRING");

	public static final TextAttributesKey COMPILER_VARIABLE = TextAttributesKey.createTextAttributesKey("C.COMPILER_VARIABLE", DefaultLanguageHighlighterColors.CONSTANT);

	public static final TextAttributesKey PREPROCESSOR_DIRECTIVE = TextAttributesKey.createTextAttributesKey("C.PREPROCESSOR_DIRECTIVE", DefaultLanguageHighlighterColors.MACRO_KEYWORD);
	public static final TextAttributesKey INCLUDE_PATH = TextAttributesKey.createTextAttributesKey("C.INCLUDE_PATH", DefaultLanguageHighlighterColors.STRING);
	public static final TextAttributesKey LIGHT_COMPILER_VARIABLE = TextAttributesKey.createTextAttributesKey("C.LIGHT_COMPILER_VARIABLE");

	public static final TextAttributesKey NUMBER = TextAttributesKey.createTextAttributesKey("C.NUMBER", DefaultLanguageHighlighterColors.NUMBER);
	public static final TextAttributesKey LIGHT_NUMBER = TextAttributesKey.createTextAttributesKey("C.LIGHT_NUMBER");

	public static final TextAttributesKey LIGHT_IDENTIFIER = TextAttributesKey.createTextAttributesKey("C.LIGHT_IDENTIFIER");

	public static final TextAttributesKey CONSTANT = TextAttributesKey.createTextAttributesKey("C.CONSTANT", DefaultLanguageHighlighterColors.STATIC_FIELD);

	// semantic - set by CHighlightVisitor from what names declare and resolve to
	public static final TextAttributesKey CLASS_NAME = TextAttributesKey.createTextAttributesKey("C.CLASS_NAME", DefaultLanguageHighlighterColors.CLASS_NAME);
	public static final TextAttributesKey TYPE_ALIAS = TextAttributesKey.createTextAttributesKey("C.TYPE_ALIAS", DefaultLanguageHighlighterColors.TYPE_ALIAS_NAME);
	public static final TextAttributesKey TEMPLATE_PARAMETER = TextAttributesKey.createTextAttributesKey("C.TEMPLATE_PARAMETER", DefaultLanguageHighlighterColors.TYPE_ALIAS_NAME);
	public static final TextAttributesKey NAMESPACE = TextAttributesKey.createTextAttributesKey("C.NAMESPACE", DefaultLanguageHighlighterColors.CLASS_REFERENCE);
	public static final TextAttributesKey FUNCTION_DECLARATION = TextAttributesKey.createTextAttributesKey("C.FUNCTION_DECLARATION", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
	public static final TextAttributesKey FUNCTION_CALL = TextAttributesKey.createTextAttributesKey("C.FUNCTION_CALL", DefaultLanguageHighlighterColors.FUNCTION_CALL);
	public static final TextAttributesKey MEMBER_FUNCTION = TextAttributesKey.createTextAttributesKey("C.MEMBER_FUNCTION", DefaultLanguageHighlighterColors.INSTANCE_METHOD);
	public static final TextAttributesKey FIELD = TextAttributesKey.createTextAttributesKey("C.FIELD", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
	public static final TextAttributesKey STATIC_FIELD = TextAttributesKey.createTextAttributesKey("C.STATIC_FIELD", DefaultLanguageHighlighterColors.STATIC_FIELD);
	public static final TextAttributesKey GLOBAL_VARIABLE = TextAttributesKey.createTextAttributesKey("C.GLOBAL_VARIABLE", DefaultLanguageHighlighterColors.GLOBAL_VARIABLE);
	public static final TextAttributesKey LOCAL_VARIABLE = TextAttributesKey.createTextAttributesKey("C.LOCAL_VARIABLE", DefaultLanguageHighlighterColors.LOCAL_VARIABLE);
	public static final TextAttributesKey PARAMETER = TextAttributesKey.createTextAttributesKey("C.PARAMETER", DefaultLanguageHighlighterColors.PARAMETER);

	public static final TextAttributesKey COMMA = TextAttributesKey.createTextAttributesKey("C.COMMA", DefaultLanguageHighlighterColors.COMMA);
	public static final TextAttributesKey DOT = TextAttributesKey.createTextAttributesKey("C.DOT", DefaultLanguageHighlighterColors.DOT);
	public static final TextAttributesKey LINE_COMMENT = TextAttributesKey.createTextAttributesKey("C.LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
	public static final TextAttributesKey BLOCK_COMMENT = TextAttributesKey.createTextAttributesKey("C.BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);

	public static final Map<IElementType, TextAttributesKey[]> ATTRIBUTES = new HashMap<>();

	static
	{
		fillMap(CPsiTokens.END_OF_LINE_COMMENT, LINE_COMMENT, LINE_COMMENT);
		fillMap(CPsiTokens.C_STYLE_COMMENT, BLOCK_COMMENT, BLOCK_COMMENT);
		fillMap(CPsiTokens.KEYWORD_SET, KEYWORD, LIGHT_KEYWORD);
		fillMap(CPsiTokens.STRING_LITERAL_SET, STRING, LIGHT_STRING);
		fillMap(CPsiTokens.STRING_INCLUDE_LITERAL, INCLUDE_PATH, LIGHT_STRING);
		fillMap(TokenSet.create(CPreprocessorTokenTypes.S_INCLUDE_KEYWORD, CPreprocessorTokenTypes.S_DEFINE_KEYWORD, CPreprocessorTokenTypes.S_UNDEF_KEYWORD,
			CPreprocessorTokenTypes.S_IF_KEYWORD, CPreprocessorTokenTypes.S_IFDEF_KEYWORD, CPreprocessorTokenTypes.S_IFNDEF_KEYWORD,
			CPreprocessorTokenTypes.S_ELIF_KEYWORD, CPreprocessorTokenTypes.S_ELSE_KEYWORD, CPreprocessorTokenTypes.S_ENDIF_KEYWORD,
			CPreprocessorTokenTypes.S_PRAGMA_KEYWORD, CPreprocessorTokenTypes.S_DIRECTIVE_KEYWORD), PREPROCESSOR_DIRECTIVE, LIGHT_KEYWORD);
		// numbers
		fillMap(CPsiTokens.INTEGER_LITERAL, NUMBER, LIGHT_NUMBER);
		fillMap(CPsiTokens.LONG_LITERAL, NUMBER, LIGHT_NUMBER);
		fillMap(CPsiTokens.FLOAT_LITERAL, NUMBER, LIGHT_NUMBER);
		fillMap(CPsiTokens.DOUBLE_LITERAL, NUMBER, LIGHT_NUMBER);
		fillMap(CPsiTokens.BOOL_LITERAL, KEYWORD, LIGHT_KEYWORD);
	}

	private static void fillMap(IElementType elementType, TextAttributesKey... keys)
	{
		ATTRIBUTES.put(elementType, keys);
	}

	private static void fillMap(TokenSet tokenSet, TextAttributesKey... keys)
	{
		for(IElementType elementType : tokenSet.getTypes())
		{
			ATTRIBUTES.put(elementType, keys);
		}
	}

	private final CLanguageVersion myLanguageVersion;

	public CSyntaxHighlighter(CLanguageVersion languageVersion)
	{
		myLanguageVersion = languageVersion;
	}

	@NotNull
	@Override
	public Lexer getHighlightingLexer()
	{
		return new CHighlightingLexer(myLanguageVersion.createLexer());
	}

	@NotNull
	@Override
	public TextAttributesKey[] getTokenHighlights(IElementType tokenType)
	{
		TextAttributesKey[] keys = ATTRIBUTES.get(tokenType);
		return pack(keys == null ? null : keys[0]);
	}

	public TextAttributesKey[] getAttributes(IElementType e)
	{
		return ATTRIBUTES.get(e);
	}
}
