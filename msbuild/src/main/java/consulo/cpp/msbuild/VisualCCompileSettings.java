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
package consulo.cpp.msbuild;

import consulo.cpp.lang.version.C11LanguageVersion;
import consulo.cpp.lang.version.C17LanguageVersion;
import consulo.cpp.lang.version.C23LanguageVersion;
import consulo.cpp.lang.version.CLanguageVersion;
import consulo.cpp.lang.version.CLanguageVersions;
import consulo.cpp.lang.version.Cpp14LanguageVersion;
import consulo.cpp.lang.version.Cpp17LanguageVersion;
import consulo.cpp.lang.version.Cpp20LanguageVersion;
import consulo.cpp.lang.version.Cpp23LanguageVersion;
import consulo.msbuild.MSBuildEvaluatedItem;
import consulo.msbuild.MSBuildImportUtil;
import consulo.util.io.FileUtil;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @author VISTALL
 * @since 2026-10-10
 */
public record VisualCCompileSettings(List<String> includeDirectories,
                                     List<String> forcedIncludes,
                                     Map<String, String> defines,
                                     List<Path> sourceDirectories,
                                     boolean hasCpp,
                                     boolean hasC,
                                     @Nullable CLanguageVersion cppStandard,
                                     @Nullable CLanguageVersion cStandard,
                                     String runtimeLibrary,
                                     boolean conformingCplusplus) {
    private static final String COMPILE_AS_C = "CompileAsC";
    private static final String COMPILE_AS_CPP = "CompileAsCpp";
    private static final String CONFORMING_CPLUSPLUS_OPTION = "/Zc:__cplusplus";

    /**
     * {@code LanguageStandard} values to language version ids.
     */
    private static final Map<String, String> CPP_STANDARDS = Map.of(
        "stdcpp14", Cpp14LanguageVersion.ID,
        "stdcpp17", Cpp17LanguageVersion.ID,
        "stdcpp20", Cpp20LanguageVersion.ID,
        "stdcpp23", Cpp23LanguageVersion.ID,
        "stdcpplatest", Cpp23LanguageVersion.ID
    );

    /**
     * {@code LanguageStandard_C} values to language version ids.
     */
    private static final Map<String, String> C_STANDARDS = Map.of(
        "stdc11", C11LanguageVersion.ID,
        "stdc17", C17LanguageVersion.ID,
        "stdc23", C23LanguageVersion.ID,
        "stdclatest", C23LanguageVersion.ID
    );

    public static VisualCCompileSettings of(Path projectDir, List<? extends MSBuildEvaluatedItem> items) {
        Set<String> includeDirectories = new LinkedHashSet<>();
        Set<String> forcedIncludes = new LinkedHashSet<>();
        Map<String, String> defines = new LinkedHashMap<>();
        Set<Path> sourceDirectories = new LinkedHashSet<>();
        boolean hasCpp = false;
        boolean hasC = false;
        String languageStandard = "";
        String languageStandardC = "";
        String runtimeLibrary = "";
        boolean conformingCplusplus = false;

        for (MSBuildEvaluatedItem item : items) {
            if (!VisualCProjectFile.CL_COMPILE_ITEM.equals(item.getName())) {
                continue;
            }
            Map<String, String> metadata = item.getMetadata();
            if ("true".equalsIgnoreCase(metadata(metadata, "ExcludedFromBuild"))) {
                continue;
            }

            Path source = sourcePath(projectDir, item);
            Path sourceDirectory = source != null ? source.getParent() : null;
            if (sourceDirectory != null) {
                sourceDirectories.add(sourceDirectory);
            }

            boolean c = isC(metadata(metadata, "CompileAs"), source);
            if (c) {
                hasC = true;
                if (languageStandardC.isEmpty()) {
                    languageStandardC = metadata(metadata, "LanguageStandard_C");
                }
            }
            else {
                hasCpp = true;
                if (languageStandard.isEmpty()) {
                    languageStandard = metadata(metadata, "LanguageStandard");
                }
                if (isConformingCplusplus(metadata(metadata, "AdditionalOptions"))) {
                    conformingCplusplus = true;
                }
            }

            if (runtimeLibrary.isEmpty()) {
                runtimeLibrary = metadata(metadata, "RuntimeLibrary");
            }

            for (String entry : MSBuildImportUtil.splitList(metadata(metadata, "AdditionalIncludeDirectories"))) {
                if (isUnexpanded(entry)) {
                    continue;
                }
                Path directory = MSBuildImportUtil.resolvePath(projectDir, entry);
                if (directory != null) {
                    includeDirectories.add(FileUtil.toSystemIndependentName(directory.toString()));
                }
            }

            // /FI: a header resolved as an #include "..." of the source would be - kept as written when it is relative
            for (String entry : MSBuildImportUtil.splitList(metadata(metadata, "ForcedIncludeFiles"))) {
                if (!isUnexpanded(entry)) {
                    forcedIncludes.add(FileUtil.toSystemIndependentName(entry));
                }
            }

            Map<String, String> itemDefines = parseDefines(metadata(metadata, "PreprocessorDefinitions"));
            for (String name : MSBuildImportUtil.splitList(metadata(metadata, "UndefinePreprocessorDefinitions"))) {
                itemDefines.remove(name);
            }
            for (Map.Entry<String, String> entry : itemDefines.entrySet()) {
                defines.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        return new VisualCCompileSettings(
            new ArrayList<>(includeDirectories),
            new ArrayList<>(forcedIncludes),
            defines,
            new ArrayList<>(sourceDirectories),
            hasCpp,
            hasC,
            hasCpp ? CLanguageVersions.findById(CPP_STANDARDS.getOrDefault(languageStandard.toLowerCase(Locale.ROOT), Cpp14LanguageVersion.ID)) : null,
            hasC ? CLanguageVersions.findById(C_STANDARDS.get(languageStandardC.toLowerCase(Locale.ROOT))) : null,
            runtimeLibrary,
            conformingCplusplus
        );
    }

    public static Map<String, String> parseDefines(String text) {
        Map<String, String> defines = new LinkedHashMap<>();
        for (String entry : MSBuildImportUtil.splitList(text)) {
            if (isUnexpanded(entry)) {
                continue;
            }
            int separator = indexOfValueSeparator(entry);
            String name = separator < 0 ? entry : entry.substring(0, separator).trim();
            String value = separator < 0 ? "1" : entry.substring(separator + 1).trim();
            if (isIdentifier(name)) {
                defines.put(name, value);
            }
        }
        return defines;
    }

    private static @Nullable Path sourcePath(Path projectDir, MSBuildEvaluatedItem item) {
        String fullPath = metadata(item.getMetadata(), "FullPath");
        return MSBuildImportUtil.resolvePath(projectDir, fullPath.isEmpty() ? item.getItemSpec() : fullPath);
    }

    private static boolean isC(String compileAs, @Nullable Path source) {
        if (COMPILE_AS_C.equalsIgnoreCase(compileAs)) {
            return true;
        }
        if (compileAs.regionMatches(true, 0, COMPILE_AS_CPP, 0, COMPILE_AS_CPP.length())) {
            return false;
        }
        Path fileName = source != null ? source.getFileName() : null;
        return fileName != null && fileName.toString().toLowerCase(Locale.ROOT).endsWith(".c");
    }

    private static boolean isConformingCplusplus(String additionalOptions) {
        for (String option : additionalOptions.split("\\s+")) {
            if (CONFORMING_CPLUSPLUS_OPTION.equalsIgnoreCase(option) || "-Zc:__cplusplus".equalsIgnoreCase(option)) {
                return true;
            }
        }
        return false;
    }

    private static int indexOfValueSeparator(String entry) {
        int equals = entry.indexOf('=');
        int hash = entry.indexOf('#');
        if (equals < 0) {
            return hash;
        }
        return hash < 0 ? equals : Math.min(equals, hash);
    }

    private static boolean isUnexpanded(String value) {
        return value.contains("%(") || value.contains("$(");
    }

    private static boolean isIdentifier(String name) {
        if (name.isEmpty() || !(Character.isLetter(name.charAt(0)) || name.charAt(0) == '_')) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                return false;
            }
        }
        return true;
    }

    private static String metadata(Map<String, String> metadata, String name) {
        String value = metadata.get(name);
        return value == null ? "" : value.trim();
    }
}
