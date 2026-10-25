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
package consulo.cpp.moduleAware;

import consulo.annotation.access.RequiredReadAction;
import consulo.cpp.lang.version.CLanguageVersions;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.ModuleAwareIndexOptions;
import consulo.project.Project;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads back the preprocessor options the module-aware index recorded for a file, so parsing and highlighting
 * see exactly what the file was indexed under.
 */
public final class CDefineEnv {
    public static final String PROVIDER_ID = "cpp-module-aware";

    private CDefineEnv() {
    }

    /**
     * The defines a file starts preprocessing with: the macros its language version predefines, then the module's
     * defines - which win, as {@code -D} does over a builtin.
     */
    @RequiredReadAction
    public static Map<String, String> seedFor(PsiElement context) {
        Map<String, String> seed = new LinkedHashMap<>(CLanguageVersions.of(context).getPredefinedMacros());
        seed.putAll(optionsFor(context).defines());
        return seed;
    }

    public static Map<String, String> seedFor(Project project, VirtualFile file) {
        return optionsFor(project, file).defines();
    }

    /**
     * The options a file is preprocessed with. For a header of a library - the system headers a CMake import adds -
     * the platform answers as the module reaching it through its order entry compiles it.
     */
    public static CPreprocessorOptions optionsFor(PsiElement context) {
        return Objects.requireNonNullElse(
            ModuleAwareIndexOptions.getOptions(context, PROVIDER_ID, CPreprocessorOptionsExternalizer.INSTANCE),
            CPreprocessorOptions.EMPTY
        );
    }

    public static CPreprocessorOptions optionsFor(Project project, VirtualFile file) {
        return Objects.requireNonNullElse(
            ModuleAwareIndexOptions.getOptions(project, file, PROVIDER_ID, CPreprocessorOptionsExternalizer.INSTANCE),
            CPreprocessorOptions.EMPTY
        );
    }

    /**
     * The language version a file is parsed with - asked of its own provider only, so resolving a version never
     * computes the preprocessor options.
     */
    public static CLanguageVersionOption languageVersionFor(PsiElement context) {
        return Objects.requireNonNullElse(
            ModuleAwareIndexOptions.getOptions(context, CLanguageVersionOption.PROVIDER_ID, CLanguageVersionOptionExternalizer.INSTANCE),
            CLanguageVersionOption.EMPTY
        );
    }

    public static CLanguageVersionOption languageVersionFor(Project project, VirtualFile file) {
        return Objects.requireNonNullElse(
            ModuleAwareIndexOptions.getOptions(project, file, CLanguageVersionOption.PROVIDER_ID, CLanguageVersionOptionExternalizer.INSTANCE),
            CLanguageVersionOption.EMPTY
        );
    }

    /**
     * Resolves the recorded include directories; entries which no longer exist are skipped rather than failing
     * the parse.
     */
    public static List<VirtualFile> includeRootsFor(PsiElement context) {
        List<VirtualFile> roots = new ArrayList<>();
        for (String path : optionsFor(context).includePaths()) {
            VirtualFile root = LocalFileSystem.getInstance().findFileByPath(path);
            if (root != null && root.isDirectory()) {
                roots.add(root);
            }
        }
        return roots;
    }
}
