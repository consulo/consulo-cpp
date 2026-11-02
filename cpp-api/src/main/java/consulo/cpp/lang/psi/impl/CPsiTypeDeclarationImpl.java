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

package consulo.cpp.lang.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.cpp.lang.psi.CPsiTypeDeclaration;

/**
 * @author VISTALL
 * @date 14:49/11.12.2011
 */
public class CPsiTypeDeclarationImpl extends CPsiNamedElementBaseImpl implements CPsiTypeDeclaration {
	public CPsiTypeDeclarationImpl(@org.jetbrains.annotations.NotNull ASTNode node) {
		super(node);
	}
}
