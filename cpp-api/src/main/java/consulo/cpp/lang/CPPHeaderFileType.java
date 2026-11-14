package consulo.cpp.lang;

import consulo.cpp.api.icon.CppApiIconGroup;
import consulo.language.file.LanguageFileType;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileTypeIdentifiableByVirtualFile;
import org.jetbrains.annotations.NotNull;

import jakarta.annotation.Nonnull;
import java.util.Set;

/**
 * @author VISTALL
 * @since 07/12/2021
 */
public class CPPHeaderFileType extends LanguageFileType implements FileTypeIdentifiableByVirtualFile
{
	/**
	 * How many directories above an extensionless header its {@code include} directory may be:
	 * {@code include/c++/16/vector}, {@code include/qt6/QtCore/QString}.
	 */
	private static final int MAX_INCLUDE_DEPTH = 4;

	/**
	 * Extensionless files of an include directory which are not headers.
	 */
	private static final Set<String> NOT_HEADERS = Set.of("Makefile", "GNUmakefile", "Doxyfile", "Dockerfile", "Kconfig", "Jenkinsfile", "Vagrantfile");

	public static final CPPHeaderFileType INSTANCE = new CPPHeaderFileType();

	public CPPHeaderFileType()
	{
		super(CLanguage.INSTANCE);
	}

	@NotNull
	@Override
	public String getId()
	{
		return "CPP_HEADER";
	}

	@NotNull
	@Override
	public LocalizeValue getDescription()
	{
		return LocalizeValue.localizeTODO("C++ header file");
	}

	@NotNull
	@Override
	public String getDefaultExtension()
	{
		return "hpp";
	}

	/**
	 * The headers of the C++ standard library have no extension - {@code <vector>}, {@code <optional>}, {@code <iostream>}
	 * - nor have Qt's ({@code <QString>}): an extensionless file in an {@code include} directory is a C++ header. Asked of
	 * every file before its name is matched, so it looks at nothing but names.
	 */
	@Override
	public boolean isMyFileType(@NotNull VirtualFile file)
	{
		if(file.isDirectory())
		{
			return false;
		}
		CharSequence name = file.getNameSequence();
		if(name.length() == 0 || StringUtil.indexOf(name, '.') >= 0 || !hasLowerCase(name) || NOT_HEADERS.contains(name.toString()))
		{
			return false;
		}
		VirtualFile parent = file.getParent();
		for(int depth = 0; parent != null && depth < MAX_INCLUDE_DEPTH; depth++)
		{
			if(StringUtil.equals(parent.getNameSequence(), "include"))
			{
				return true;
			}
			parent = parent.getParent();
		}
		return false;
	}

	/**
	 * README, LICENSE, COPYING: all capitals is not a header's name.
	 */
	private static boolean hasLowerCase(CharSequence name)
	{
		for(int i = 0; i < name.length(); i++)
		{
			if(Character.isLowerCase(name.charAt(i)))
			{
				return true;
			}
		}
		return false;
	}

	@Nonnull
	@Override
	public Image getIcon()
	{
		return CppApiIconGroup.filetypeHpp();
	}
}
