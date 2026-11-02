/*
 * Copyright 2010-2012 napile.org
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

package consulo.cpp.preprocessor.psi;

import consulo.language.ast.IElementType;
import consulo.cpp.lang.psi.CBaseElementType;

/**
 * @author VISTALL
 * @date 13:37/29.12.12
 */
public interface CPreprocessorTokenTypes
{
	// sharp keywords
	IElementType S_INCLUDE_KEYWORD = new CBaseElementType("S_INCLUDE_KEYWORD"); // #include
	IElementType S_DEFINE_KEYWORD = new CBaseElementType("S_DEFINE_KEYWORD"); // #define
	IElementType S_IFDEF_KEYWORD = new CBaseElementType("S_IFDEF_KEYWORD"); // #ifdef
	IElementType S_IFNDEF_KEYWORD = new CBaseElementType("S_IFNDEF_KEYWORD"); // #ifndef
	IElementType S_ENDIF_KEYWORD = new CBaseElementType("S_ENDIF_KEYWORD"); // #endif
	IElementType S_ELSE_KEYWORD = new CBaseElementType("S_ELSE_KEYWORD"); // #else
	IElementType S_PRAGMA_KEYWORD = new CBaseElementType("S_PRAGMA_KEYWORD"); // #pragma
	IElementType S_UNDEF_KEYWORD = new CBaseElementType("S_UNDEF_KEYWORD"); // #undef
	IElementType S_IF_KEYWORD = new CBaseElementType("S_IF_KEYWORD"); // #if
	IElementType S_ELIF_KEYWORD = new CBaseElementType("S_ELIF_KEYWORD"); // #elif
	// a directive the preprocessor does not act on - #error, #warning, #line, #elifdef - and defined() in a condition;
	// only the highlighting lexer makes it
	IElementType S_DIRECTIVE_KEYWORD = new CBaseElementType("S_DIRECTIVE_KEYWORD");

	IElementType SYMBOL = new CBaseElementType("C_PREPROCESSOR_SYMBOL");
}
