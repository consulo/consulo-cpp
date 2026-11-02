package consulo.cpp.debugger;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.nativeDev.debugger.NativeDebuggerLanguageSupport;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;
import consulo.cpp.lang.CLanguage;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
@ExtensionImpl
public class CNativeDebuggerLanguageSupport implements NativeDebuggerLanguageSupport {
    @Override
    public Language getLanguage() {
        return CLanguage.INSTANCE;
    }

    @Override
    public PsiFile createExpressionCodeFragment(Project project, String text, @Nullable PsiElement context, boolean isPhysical) {
        return PsiFileFactory.getInstance(project).createFileFromText("fragment.cpp", CLanguage.INSTANCE, text, isPhysical, false);
    }
}
