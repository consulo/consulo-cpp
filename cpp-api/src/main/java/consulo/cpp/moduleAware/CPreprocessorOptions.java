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

import consulo.cpp.module.extension.CModuleExtension;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What decides how a C/C++ file preprocesses: the defines it is compiled with, the directories its includes resolve
 * against, the headers forced in before its first line, and a fingerprint of the files it pulls in. Two files sharing these share their indexed data, so this is
 * the option the module-aware index keys its variants on; the fingerprint is what makes an edit to an included
 * header re-index the files that include it.
 * <p>
 * The language version is not here - it is {@link CLanguageVersionOption}, which costs nothing to compute.
 */
public record CPreprocessorOptions(Map<String, String> defines,
                                   List<String> includePaths,
                                   List<String> forcedIncludes,
                                   String includeFingerprint) {
    public static final CPreprocessorOptions EMPTY = new CPreprocessorOptions(Map.of(), List.of(), List.of(), "");

    public CPreprocessorOptions {
        // not Map.copyOf - its iteration order changes from one JVM run to the next, and the externalizer writes the
        // defines in iteration order, so the stored payload would differ after every restart
        defines = Collections.unmodifiableMap(new TreeMap<>(defines));
        includePaths = List.copyOf(includePaths);
        forcedIncludes = List.copyOf(forcedIncludes);
        includeFingerprint = includeFingerprint == null ? "" : includeFingerprint;
    }

    /**
     * The options a file is preprocessed with in a module configured by {@code extension}.
     *
     * @param includeFingerprint the fingerprint of the files the file includes - only computed when indexing
     */
    public static CPreprocessorOptions of(CModuleExtension<?> extension, String includeFingerprint) {
        return new CPreprocessorOptions(extension.getDefines(), extension.getIncludePaths(), extension.getForcedIncludes(), includeFingerprint);
    }

    /**
     * The options a file is preprocessed with: those of its module's {@code extension}, if it has one, merged with what
     * the {@link CFileOptionsContributor contributors} add for the file - their defines override the module's, their
     * include directories are searched after the module's, and their forced includes come first.
     */
    public static CPreprocessorOptions of(@Nullable CModuleExtension<?> extension, List<CFileOptions> contributed, String includeFingerprint) {
        Map<String, String> defines = new LinkedHashMap<>();
        List<String> includePaths = new ArrayList<>();
        List<String> forcedIncludes = new ArrayList<>();
        for (CFileOptions options : contributed) {
            forcedIncludes.addAll(options.forcedIncludes());
        }
        if (extension != null) {
            defines.putAll(extension.getDefines());
            includePaths.addAll(extension.getIncludePaths());
            forcedIncludes.addAll(extension.getForcedIncludes());
        }
        for (CFileOptions options : contributed) {
            defines.putAll(options.defines());
            includePaths.addAll(options.includePaths());
        }
        return new CPreprocessorOptions(defines, distinct(includePaths), distinct(forcedIncludes), includeFingerprint);
    }

    private static List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    public boolean isEmpty() {
        return defines.isEmpty() && includePaths.isEmpty() && forcedIncludes.isEmpty() && includeFingerprint.isEmpty();
    }
}
