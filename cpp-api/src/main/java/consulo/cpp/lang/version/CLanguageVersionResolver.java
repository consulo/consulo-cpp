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
package consulo.cpp.lang.version;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.cpp.lang.CLanguage;
import consulo.cpp.moduleAware.CDefineEnv;
import consulo.cpp.moduleAware.CLanguageVersionOption;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.version.LanguageVersion;
import consulo.language.version.LanguageVersionResolver;
import consulo.language.version.LanguageVersionWithDefinition;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the version of a C/C++ file. A registered version which {@link LanguageVersionWithDefinition claims}
 * the file wins; otherwise the version comes from the module-aware index - its own option, cheap to compute, so a
 * header indexed for one module is parsed with that module's standard - and falls back to
 * {@link CLanguageVersions#select} by file type.
 *
 * @author VISTALL
 * @since 2026-10-19
 */
@ExtensionImpl
public class CLanguageVersionResolver implements LanguageVersionResolver {
    @RequiredReadAction
    @Override
    public LanguageVersion getLanguageVersion(Language language, @Nullable PsiElement element) {
        if (element == null) {
            return CLanguageVersions.getDefaultCpp();
        }

        for (CLanguageVersion version : CLanguageVersions.getVersions()) {
            if (version instanceof LanguageVersionWithDefinition definition && definition.isMyElement(element)) {
                return version;
            }
        }

        PsiFile file = element.getContainingFile();
        return resolve(file == null ? null : file.getFileType(), CDefineEnv.languageVersionFor(element));
    }

    @RequiredReadAction
    @Override
    public LanguageVersion getLanguageVersion(Language language, @Nullable Project project, @Nullable VirtualFile virtualFile) {
        if (virtualFile == null) {
            return CLanguageVersions.getDefaultCpp();
        }

        for (CLanguageVersion version : CLanguageVersions.getVersions()) {
            if (version instanceof LanguageVersionWithDefinition definition && definition.isMyFile(project, virtualFile)) {
                return version;
            }
        }

        CLanguageVersionOption option = project == null ? CLanguageVersionOption.EMPTY : CDefineEnv.languageVersionFor(project, virtualFile);
        return resolve(virtualFile.getFileType(), option);
    }

    private static CLanguageVersion resolve(@Nullable FileType fileType, CLanguageVersionOption option) {
        CLanguageVersion configured = CLanguageVersions.findById(option.languageVersionId());
        return configured != null ? configured : CLanguageVersions.select(fileType, null, null);
    }

    @Override
    public Language getLanguage() {
        return CLanguage.INSTANCE;
    }
}
