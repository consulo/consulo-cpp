package consulo.cpp.impl;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AllIcons;
import consulo.language.icon.IconDescriptor;
import consulo.language.icon.IconDescriptorUpdater;
import consulo.language.psi.PsiElement;
import consulo.cpp.lang.CLanguage;
import consulo.cpp.lang.psi.CPsiEnum;
import consulo.cpp.lang.psi.CPsiEnumConstant;
import consulo.cpp.lang.psi.CPsiParameter;
import consulo.cpp.lang.psi.resolve.CDeclarationKind;
import consulo.cpp.lang.psi.resolve.CDeclarations;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class CppIconProvider implements IconDescriptorUpdater
{
	@RequiredReadAction
	@Override
	public void updateIcon(@Nonnull IconDescriptor iconDescriptor, @Nonnull PsiElement element, int flags)
	{
		if(element.getNode() == null || element.getLanguage() != CLanguage.INSTANCE)
		{
			return;
		}
		CDeclarationKind kind = CDeclarations.kindOf(element);
		if(kind == null)
		{
			return;
		}
		switch(kind)
		{
			case FUNCTION:
				iconDescriptor.setMainIcon(CDeclarations.isMemberFunction(element) ? AllIcons.Nodes.Method : AllIcons.Nodes.Function);
				break;
			case TYPE:
				if(element.getFirstChild() == null || CDeclarations.isTypeAlias(element))
				{
					iconDescriptor.setMainIcon(AllIcons.Nodes.TypeAlias);
				}
				else
				{
					iconDescriptor.setMainIcon(element instanceof CPsiEnum ? AllIcons.Nodes.Enum : AllIcons.Nodes.Class);
				}
				break;
			case NAMESPACE:
				iconDescriptor.setMainIcon(AllIcons.Nodes.Package);
				break;
			case VARIABLE:
				if(element instanceof CPsiParameter)
				{
					iconDescriptor.setMainIcon(AllIcons.Nodes.Parameter);
				}
				else if(element instanceof CPsiEnumConstant || CDeclarations.enclosingAggregate(element) != null)
				{
					iconDescriptor.setMainIcon(AllIcons.Nodes.Field);
				}
				else
				{
					iconDescriptor.setMainIcon(AllIcons.Nodes.Variable);
				}
				break;
		}
	}
}
