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
import consulo.cpp.lang.CHeaderFileType;
import consulo.cpp.lang.CLanguage;
import consulo.cpp.lang.CSourceFileType;
import consulo.language.psi.PsiElement;
import consulo.language.version.LanguageVersion;
import consulo.language.version.LanguageVersionUtil;
import consulo.virtualFileSystem.fileType.FileType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Lookup over the registered {@link CLanguageVersion}s, including those other plugins contribute.
 *
 * @author VISTALL
 * @since 2026-10-19
 */
public final class CLanguageVersions {
    private CLanguageVersions() {
    }

    public static List<CLanguageVersion> getVersions() {
        List<CLanguageVersion> versions = new ArrayList<>();
        for (LanguageVersion version : CLanguage.INSTANCE.getVersions()) {
            if (version instanceof CLanguageVersion cVersion) {
                versions.add(cVersion);
            }
        }
        return versions;
    }

    public static @Nullable CLanguageVersion findById(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (CLanguageVersion version : getVersions()) {
            if (version.getId().equals(id)) {
                return version;
            }
        }
        return null;
    }

    /**
     * C used when nothing is configured - the default of current Clang.
     */
    public static CLanguageVersion getDefaultC() {
        return require(C17LanguageVersion.class);
    }

    /**
     * C++ used when nothing is configured - the default of current Clang and GCC.
     */
    public static CLanguageVersion getDefaultCpp() {
        return require(Cpp17LanguageVersion.class);
    }

    private static CLanguageVersion require(Class<? extends CLanguageVersion> versionClass) {
        CLanguageVersion version = CLanguage.INSTANCE.findVersionByClass(versionClass);
        if (version == null) {
            throw new IllegalStateException(versionClass.getName() + " is not registered");
        }
        return version;
    }

    /**
     * The version a C/C++ element is parsed with.
     */
    @RequiredReadAction
    public static CLanguageVersion of(PsiElement element) {
        LanguageVersion version = LanguageVersionUtil.findLanguageVersion(CLanguage.INSTANCE, element);
        return version instanceof CLanguageVersion cVersion ? cVersion : getDefaultCpp();
    }

    /**
     * Picks the version a file is parsed with from the C and C++ standards its module declares (either may be
     * {@code null}).
     * <p>
     * {@code .c} files are C and {@code .cpp}/{@code .hpp} files are C++. A {@code .h} header can be either, so it is
     * C only when the module declares a C standard and no C++ one: most C headers parse fine as C++, while a C++
     * header parsed as C loses every class and namespace.
     */
    public static CLanguageVersion select(@Nullable FileType fileType,
                                          @Nullable CLanguageVersion cVersion,
                                          @Nullable CLanguageVersion cppVersion) {
        boolean cpp;
        if (fileType == CSourceFileType.INSTANCE) {
            cpp = false;
        }
        else if (fileType == CHeaderFileType.INSTANCE) {
            cpp = cppVersion != null || cVersion == null;
        }
        else {
            cpp = true;
        }

        CLanguageVersion standard;
        if (cpp) {
            standard = cppVersion != null && cppVersion.isCpp() ? cppVersion : getDefaultCpp();
        }
        else {
            standard = cVersion != null && !cVersion.isCpp() ? cVersion : getDefaultC();
        }
        return fileType == null ? standard : dialectOf(fileType, standard);
    }

    /**
     * The dialect files of {@code fileType} are of, built on {@code standard}: the one built on that very standard, else
     * the newest built on an older one, else the oldest - a module on C++23 gets CUDA C++20 when CUDA goes no further.
     * {@code standard} itself when no dialect claims the type, or when it is a dialect already.
     */
    private static CLanguageVersion dialectOf(FileType fileType, CLanguageVersion standard) {
        if (standard.getBaseVersion() != null) {
            return standard;
        }
        CLanguageVersion exact = null;
        CLanguageVersion older = null;
        int olderWeight = Integer.MIN_VALUE;
        CLanguageVersion oldest = null;
        int oldestWeight = Integer.MAX_VALUE;
        for (CLanguageVersion version : getVersions()) {
            CLanguageVersion base = version.getBaseVersion();
            if (base == null || base.isCpp() != standard.isCpp() || !version.isDialectFileType(fileType)) {
                continue;
            }
            int weight = base.getWeight();
            if (base.getId().equals(standard.getId())) {
                exact = version;
            }
            else if (weight < standard.getWeight() && weight > olderWeight) {
                older = version;
                olderWeight = weight;
            }
            if (weight < oldestWeight) {
                oldest = version;
                oldestWeight = weight;
            }
        }
        if (exact != null) {
            return exact;
        }
        if (older != null) {
            return older;
        }
        return oldest != null ? oldest : standard;
    }

    /**
     * The newer of two versions of the same language, by {@link CLanguageVersion#getWeight() weight}.
     */
    public static @Nullable CLanguageVersion newer(@Nullable CLanguageVersion current, @Nullable CLanguageVersion candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        return candidate.getWeight() > current.getWeight() ? candidate : current;
    }
}
