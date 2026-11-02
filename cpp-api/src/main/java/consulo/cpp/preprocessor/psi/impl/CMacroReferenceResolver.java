/*
 * Copyright 2013-2026 consulo.io
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
package consulo.cpp.preprocessor.psi.impl;

import consulo.annotation.access.RequiredReadAction;
import consulo.cpp.lang.version.CLanguageVersions;
import consulo.cpp.moduleAware.CDefineEnv;
import consulo.cpp.preprocessor.CPreprocessorLanguage;
import consulo.cpp.preprocessor.expand.ExpandedMacro;
import consulo.cpp.preprocessor.expand.PreprocessorExpander;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * Finds the macro a reference names, as the preprocessing of the reference's file defines it.
 *
 * @author VISTALL
 * @since 2026-10-19
 */
public final class CMacroReferenceResolver {
    private CMacroReferenceResolver() {
    }

    @RequiredReadAction
    static @Nullable ExpandedMacro findMacro(PsiElement reference, String name) {
        PsiFile preprocessorFile = reference.getContainingFile().getViewProvider().getPsi(CPreprocessorLanguage.INSTANCE);
        if (preprocessorFile == null) {
            return null;
        }
        PreprocessorExpander expander = new PreprocessorExpander(preprocessorFile,
            CLanguageVersions.of(reference),
            CDefineEnv.includeRootsFor(reference),
            CDefineEnv.seedFor(reference),
            CDefineEnv.forcedIncludesFor(reference));
        return expander.getMacro(name);
    }

    /**
     * The names of the macros a file defines - itself, its includes, its module - kept until the next change of PSI: what
     * the highlighting asks of every name of every directive.
     */
    @RequiredReadAction
    public static java.util.Set<String> macroNames(PsiElement context) {
        PsiFile preprocessorFile = context.getContainingFile().getViewProvider().getPsi(CPreprocessorLanguage.INSTANCE);
        if (preprocessorFile == null) {
            return java.util.Set.of();
        }
        return consulo.language.psi.util.LanguageCachedValueUtil.getProjectPsiDependentCache(preprocessorFile, CMacroReferenceResolver::computeMacroNames);
    }

    private static java.util.Set<String> computeMacroNames(PsiFile preprocessorFile) {
        PreprocessorExpander expander = new PreprocessorExpander(preprocessorFile,
            CLanguageVersions.of(preprocessorFile),
            CDefineEnv.includeRootsFor(preprocessorFile),
            CDefineEnv.seedFor(preprocessorFile),
            CDefineEnv.forcedIncludesFor(preprocessorFile));
        return java.util.Set.copyOf(expander.getMacroNames());
    }
}
