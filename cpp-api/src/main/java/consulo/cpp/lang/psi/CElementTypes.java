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

package consulo.cpp.lang.psi;

import consulo.language.ast.IElementType;
import consulo.cpp.lang.psi.impl.*;

/**
 * All C PSI element types. Parsers call
 * {@link consulo.language.parser.PsiBuilder.Marker#done(IElementType)} with these
 * constants directly — no registry or lookup is needed.
 *
 * @author VISTALL
 */
public interface CElementTypes {
    IElementType ACCESSOR_OWNER =
        new CElementType(CPsiAccessorOwner.class.getSimpleName(), CPsiAccessorOwnerImpl::new);

    IElementType ARGUMENT_LIST =
        new CElementType(CPsiArgumentList.class.getSimpleName(), CPsiArgumentListImpl::new);

    IElementType ARRAY_LITERAL_EXPRESSION =
        new CElementType(CPsiArrayLiteralExpression.class.getSimpleName(), CPsiArrayLiteralExpressionImpl::new);

    IElementType ASSIGNMENT_EXPRESSION =
        new CElementType(CPsiAssignmentExpression.class.getSimpleName(), CPsiAssignmentExpressionImpl::new);

    IElementType BINARY_EXPRESSION =
        new CElementType(CPsiBinaryExpression.class.getSimpleName(), CPsiBinaryExpressionImpl::new);

    IElementType BREAK_STATEMENT =
        new CElementType(CPsiBreakStatement.class.getSimpleName(), CPsiBreakStatementImpl::new);

    IElementType CALL_EXPRESSION =
        new CElementType(CPsiCallExpression.class.getSimpleName(), CPsiCallExpressionImpl::new);

    IElementType CASE_STATEMENT =
        new CElementType(CPsiCaseStatement.class.getSimpleName(), CPsiCaseStatementImpl::new);

    IElementType CLASS =
        new CElementType(CPsiClass.class.getSimpleName(), CPsiClassImpl::new);

    IElementType CODE_BLOCK =
        new CElementType(CPsiCodeBlock.class.getSimpleName(), CPsiCodeBlockImpl::new);

    IElementType COMMA_EXPRESSION =
        new CElementType(CPsiCommaExpression.class.getSimpleName(), CPsiCommaExpressionImpl::new);

    IElementType CONDITIONAL_EXPRESSION =
        new CElementType(CPsiConditionalExpression.class.getSimpleName(), CPsiConditionalExpressionImpl::new);

    IElementType CONTINUE_STATEMENT =
        new CElementType(CPsiContinueStatement.class.getSimpleName(), CPsiContinueStatementImpl::new);

    IElementType DECLARATION =
        new CElementType(CPsiDeclaration.class.getSimpleName(), CPsiDeclarationImpl::new);

    IElementType DECLARATION_CONSTRUCTOR =
        new CElementType(CPsiDeclarationConstructor.class.getSimpleName(), CPsiDeclarationConstructorImpl::new);

    IElementType DECLARATION_FIELD =
        new CElementType(CPsiDeclarationField.class.getSimpleName(), CPsiDeclarationFieldImpl::new);

    IElementType DECLARATION_METHOD =
        new CElementType(CPsiDeclarationMethod.class.getSimpleName(), CPsiDeclarationMethodImpl::new);

    IElementType DEFAULT_STATEMENT =
        new CElementType(CPsiDefaultStatement.class.getSimpleName(), CPsiDefaultStatementImpl::new);

    IElementType DO_WHILE_STATEMENT =
        new CElementType(CPsiDoWhileStatement.class.getSimpleName(), CPsiDoWhileStatementImpl::new);

    IElementType DOUBLE_COLON_TYPE_REF =
        new CElementType(CPsiDoubleColonTypeRef.class.getSimpleName(), CPsiDoubleColonTypeRefImpl::new);

    IElementType ENUM =
        new CElementType(CPsiEnum.class.getSimpleName(), CPsiEnumImpl::new);

    IElementType ENUM_CONSTANT =
        new CElementType(CPsiEnumConstant.class.getSimpleName(), CPsiEnumConstantImpl::new);

    IElementType EXPRESSION_STATEMENT =
        new CElementType(CPsiExpressionStatement.class.getSimpleName(), CPsiExpressionStatementImpl::new);

    IElementType FIELD =
        new CElementType(CPsiField.class.getSimpleName(), CPsiFieldImpl::new);

    IElementType FOR_STATEMENT =
        new CElementType(CPsiForStatement.class.getSimpleName(), CPsiForStatementImpl::new);

    IElementType GOTO_STATEMENT =
        new CElementType(CPsiGotoStatement.class.getSimpleName(), CPsiGotoStatementImpl::new);

    IElementType IF_STATEMENT =
        new CElementType(CPsiIfStatement.class.getSimpleName(), CPsiIfStatementImpl::new);

    IElementType IMPLEMENTING_METHOD =
        new CElementType(CPsiImplementingMethod.class.getSimpleName(), CPsiImplementingMethodImpl::new);

    IElementType INDEXED_PROPERTY_ACCESS_EXPRESSION =
        new CElementType(CPsiIndexedPropertyAccessExpression.class.getSimpleName(), CPsiIndexedPropertyAccessExpressionImpl::new);

    IElementType KERNEL_LAUNCH_CONFIGURATION =
        new CElementType(CPsiKernelLaunchConfiguration.class.getSimpleName(), CPsiKernelLaunchConfigurationImpl::new);

    IElementType LABELED_STATEMENT =
        new CElementType(CPsiLabeledStatement.class.getSimpleName(), CPsiLabeledStatementImpl::new);

    IElementType LITERAL_EXPRESSION =
        new CElementType(CPsiLiteralExpression.class.getSimpleName(), CPsiLiteralExpressionImpl::new);

    IElementType LOCAL_VARIABLE =
        new CElementType(CPsiLocalVariable.class.getSimpleName(), CPsiLocalVariableImpl::new);

    IElementType MODIFIER_LIST =
        new CElementType(CPsiModifierList.class.getSimpleName(), CPsiModifierListImpl::new);

    IElementType NAMESPACE_DECLARATION =
        new CElementType(CPsiNamespaceDeclaration.class.getSimpleName(), CPsiNamespaceDeclarationImpl::new);

    IElementType NEW_EXPRESSION =
        new CElementType(CPsiNewExpression.class.getSimpleName(), CPsiNewExpressionImpl::new);

    IElementType PARAMETER =
        new CElementType(CPsiParameter.class.getSimpleName(), CPsiParameterImpl::new);

    IElementType PARAMETER_LIST =
        new CElementType(CPsiParameterList.class.getSimpleName(), CPsiParameterListImpl::new);

    IElementType PARENTHESIZED_EXPRESSION =
        new CElementType(CPsiParenthesizedExpression.class.getSimpleName(), CPsiParenthesizedExpressionImpl::new);

    IElementType POSTFIX_EXPRESSION =
        new CElementType(CPsiPostfixExpression.class.getSimpleName(), CPsiPostfixExpressionImpl::new);

    IElementType PREFIX_EXPRESSION =
        new CElementType(CPsiPrefixExpression.class.getSimpleName(), CPsiPrefixExpressionImpl::new);

    IElementType REFERENCE_EXPRESSION =
        new CElementType(CPsiReferenceExpression.class.getSimpleName(), CPsiReferenceExpressionImpl::new);

    IElementType RETURN_STATEMENT =
        new CElementType(CPsiReturnStatement.class.getSimpleName(), CPsiReturnStatementImpl::new);

    IElementType SIZEOF_EXPRESSION =
        new CElementType(CPsiSizeofExpression.class.getSimpleName(), CPsiSizeofExpressionImpl::new);

    IElementType STRUCT_DECLARATION =
        new CElementType(CPsiStructDeclaration.class.getSimpleName(), CPsiStructDeclarationImpl::new);

    IElementType SUPER_CLASS =
        new CElementType(CPsiSuperClass.class.getSimpleName(), CPsiSuperClassImpl::new);

    IElementType SUPER_CLASS_LIST =
        new CElementType(CPsiSuperClassList.class.getSimpleName(), CPsiSuperClassListImpl::new);

    IElementType SWITCH_STATEMENT =
        new CElementType(CPsiSwitchStatement.class.getSimpleName(), CPsiSwitchStatementImpl::new);

    IElementType TYPE_DECLARATION =
        new CElementType(CPsiTypeDeclaration.class.getSimpleName(), CPsiTypeDeclarationImpl::new);

    IElementType TYPE_REF =
        new CElementType(CPsiTypeRef.class.getSimpleName(), CPsiTypeRefImpl::new);

    IElementType UNION_DECLARATION =
        new CElementType(CPsiUnionDeclaration.class.getSimpleName(), CPsiUnionDeclarationImpl::new);

    IElementType WHILE_STATEMENT =
        new CElementType(CPsiWhileStatement.class.getSimpleName(), CPsiWhileStatementImpl::new);

    IElementType CAST_EXPRESSION =
        new CElementType(CPsiCastExpression.class.getSimpleName(), CPsiCastExpressionImpl::new);

    IElementType INITIALIZER_LIST =
        new CElementType(CPsiInitializerList.class.getSimpleName(), CPsiInitializerListImpl::new);

    IElementType ATTRIBUTE_LIST =
        new CElementType(CPsiAttributeList.class.getSimpleName(), CPsiAttributeListImpl::new);

    IElementType STATIC_ASSERT =
        new CElementType(CPsiStaticAssert.class.getSimpleName(), CPsiStaticAssertImpl::new);

    IElementType ASM =
        new CElementType(CPsiAsm.class.getSimpleName(), CPsiAsmImpl::new);

    IElementType ASM_TEMPLATE =
        new CElementType(CPsiAsmTemplate.class.getSimpleName(), CPsiAsmTemplateImpl::new);

    IElementType ASM_BLOCK =
        new CElementType(CPsiAsmBlock.class.getSimpleName(), CPsiAsmBlockImpl::new);
}
