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

import consulo.cpp.lang.version.CLanguageVersion;
import consulo.cpp.lang.version.CLanguageVersions;
import consulo.cpp.module.extension.CModuleExtension;
import consulo.virtualFileSystem.VirtualFile;

/**
 * The id of the language version a C/C++ file is parsed with, empty when its module configures no standard.
 * <p>
 * It is an option of its own, apart from {@link CPreprocessorOptions}: resolving a file's version is asked for while
 * files are scanned - before indexing, for every file - and must not pay for what the preprocessor options compute,
 * the include fingerprint above all. This option depends on the module alone.
 */
public record CLanguageVersionOption(String languageVersionId) {
    public static final String PROVIDER_ID = "cpp-language-version";

    public static final CLanguageVersionOption EMPTY = new CLanguageVersionOption("");

    public CLanguageVersionOption {
        languageVersionId = languageVersionId == null ? "" : languageVersionId;
    }

    /**
     * The version {@code file} is parsed with in a module configured by {@code extension}; empty when the module
     * configures no standard, so modules which configure nothing keep sharing their index.
     */
    public static CLanguageVersionOption of(CModuleExtension<?> extension, VirtualFile file) {
        CLanguageVersion cStandard = extension.getCStandard();
        CLanguageVersion cppStandard = extension.getCppStandard();
        if (cStandard == null && cppStandard == null) {
            return EMPTY;
        }
        return new CLanguageVersionOption(CLanguageVersions.select(file.getFileType(), cStandard, cppStandard).getId());
    }

    public boolean isEmpty() {
        return languageVersionId.isEmpty();
    }
}
