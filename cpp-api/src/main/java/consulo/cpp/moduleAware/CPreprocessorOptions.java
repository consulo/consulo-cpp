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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What decides how a C/C++ file preprocesses: the defines it is compiled with, the directories its includes resolve
 * against, and a fingerprint of the files it pulls in. Two files sharing these share their indexed data, so this is
 * the option the module-aware index keys its variants on; the fingerprint is what makes an edit to an included
 * header re-index the files that include it.
 * <p>
 * The language version is not here - it is {@link CLanguageVersionOption}, which costs nothing to compute.
 */
public record CPreprocessorOptions(Map<String, String> defines,
                                   List<String> includePaths,
                                   String includeFingerprint) {
    public static final CPreprocessorOptions EMPTY = new CPreprocessorOptions(Map.of(), List.of(), "");

    public CPreprocessorOptions {
        // not Map.copyOf - its iteration order changes from one JVM run to the next, and the externalizer writes the
        // defines in iteration order, so the stored payload would differ after every restart
        defines = Collections.unmodifiableMap(new TreeMap<>(defines));
        includePaths = List.copyOf(includePaths);
        includeFingerprint = includeFingerprint == null ? "" : includeFingerprint;
    }

    /**
     * The options a file is preprocessed with in a module configured by {@code extension}.
     *
     * @param includeFingerprint the fingerprint of the files the file includes - only computed when indexing
     */
    public static CPreprocessorOptions of(CModuleExtension<?> extension, String includeFingerprint) {
        return new CPreprocessorOptions(extension.getDefines(), extension.getIncludePaths(), includeFingerprint);
    }

    public boolean isEmpty() {
        return defines.isEmpty() && includePaths.isEmpty() && includeFingerprint.isEmpty();
    }
}
