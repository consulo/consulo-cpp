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
        // a map already sorted and unmodifiable - of an interned options, or decoded once for many files - is kept
        defines = defines.getClass() == SORTED_DEFINES_CLASS ? defines : Collections.unmodifiableSortedMap(new TreeMap<>(defines));
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
        if (extension != null && contributed.isEmpty() && includeFingerprint.isEmpty()) {
            // a file nothing contributes to has its module's options: merged once while they are the same snapshots
            Map<String, String> moduleDefines = extension.getDefines();
            List<String> moduleIncludePaths = extension.getIncludePaths();
            List<String> moduleForcedIncludes = extension.getForcedIncludes();
            ModuleMerge last = ourLastModuleMerge;
            if (last != null && last.defines() == moduleDefines && last.includePaths() == moduleIncludePaths
                && last.forcedIncludes() == moduleForcedIncludes) {
                return last.options();
            }
            CPreprocessorOptions merged = merge(extension, contributed, includeFingerprint);
            ourLastModuleMerge = new ModuleMerge(moduleDefines, moduleIncludePaths, moduleForcedIncludes, merged);
            return merged;
        }
        return merge(extension, contributed, includeFingerprint);
    }

    /**
     * The last options merged from a module alone, by the identity of the module's settings it was merged from.
     */
    private record ModuleMerge(Map<String, String> defines, List<String> includePaths, List<String> forcedIncludes,
                               CPreprocessorOptions options) {
    }

    private static volatile @Nullable ModuleMerge ourLastModuleMerge;

    private static CPreprocessorOptions merge(@Nullable CModuleExtension<?> extension, List<CFileOptions> contributed, String includeFingerprint) {
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

    private static final Class<?> SORTED_DEFINES_CLASS = Collections.unmodifiableSortedMap(new TreeMap<>()).getClass();

    /**
     * The options interned: thousands of files - the headers of a library - have the same, which are then one object,
     * serialized and shown once.
     */
    private static final Map<CPreprocessorOptions, CPreprocessorOptions> INTERNED = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CPreprocessorOptions, CPreprocessorOptions> eldest) {
            return size() > 64;
        }
    };

    /**
     * The one instance of options equal to these.
     */
    public CPreprocessorOptions intern() {
        synchronized (INTERNED) {
            CPreprocessorOptions known = INTERNED.get(this);
            if (known != null) {
                return known;
            }
            INTERNED.put(this, this);
            return this;
        }
    }

    public boolean isEmpty() {
        return defines.isEmpty() && includePaths.isEmpty() && forcedIncludes.isEmpty() && includeFingerprint.isEmpty();
    }
}
