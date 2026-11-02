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
package consulo.cpp.module.extension;

import consulo.cpp.lang.version.CLanguageVersion;
import consulo.module.extension.ModuleExtension;
import org.jdom.Element;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2026-10-10
 */
public interface CModuleExtension<T extends CModuleExtension<T>> extends ModuleExtension<T> {
    String ELEMENT_DEFINE = "define";
    String ELEMENT_INCLUDE_PATH = "includePath";
    String ELEMENT_FORCED_INCLUDE = "forcedInclude";

    Map<String, String> getDefines();

    List<String> getIncludePaths();

    /**
     * Headers every file of the module is preprocessed as if it included them before its first line, as {@code -include}
     * of gcc and clang and {@code /FI} of MSVC do: an absolute path, or one found in the include directories.
     */
    default List<String> getForcedIncludes() {
        return List.of();
    }

    @Nullable CLanguageVersion getCStandard();

    @Nullable CLanguageVersion getCppStandard();

    /**
     * The target the module compiles for, as a target triple - {@code x86_64-pc-linux-gnu}, {@code aarch64-none-elf},
     * {@code arm-none-eabi}; {@code null} when unknown, and then the code runs where the IDE does.
     */
    default @Nullable String getTarget() {
        return null;
    }

    static String formatIncludePaths(List<String> includePaths) {
        return String.join("; ", includePaths);
    }

    static List<String> parseIncludePaths(String text) {
        List<String> paths = new ArrayList<>();
        if (text == null) {
            return paths;
        }
        for (String part : text.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paths.add(trimmed);
            }
        }
        return paths;
    }

    static String formatDefines(Map<String, String> defines) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : defines.entrySet()) {
            if (builder.length() != 0) {
                builder.append("; ");
            }
            builder.append(entry.getKey());
            if (!entry.getValue().isEmpty()) {
                builder.append('=').append(entry.getValue());
            }
        }
        return builder.toString();
    }

    static Map<String, String> parseDefines(String text) {
        Map<String, String> defines = new LinkedHashMap<>();
        if (text == null) {
            return defines;
        }
        for (String part : text.split(";")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq < 0) {
                defines.put(trimmed, "");
            }
            else {
                defines.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
            }
        }
        return defines;
    }

    static void writeDefines(Element element, Map<String, String> defines) {
        for (Map.Entry<String, String> entry : defines.entrySet()) {
            element.addContent(new Element(ELEMENT_DEFINE).setAttribute("name", entry.getKey()).setAttribute("value", entry.getValue()));
        }
    }

    static Map<String, String> readDefines(Element element) {
        Map<String, String> defines = new LinkedHashMap<>();
        for (Element child : element.getChildren(ELEMENT_DEFINE)) {
            String name = child.getAttributeValue("name");
            if (name != null) {
                String value = child.getAttributeValue("value");
                defines.put(name, value == null ? "" : value);
            }
        }
        return defines;
    }

    static void writeIncludePaths(Element element, List<String> includePaths) {
        writePaths(element, ELEMENT_INCLUDE_PATH, includePaths);
    }

    static List<String> readIncludePaths(Element element) {
        return readPaths(element, ELEMENT_INCLUDE_PATH);
    }

    static void writeForcedIncludes(Element element, List<String> forcedIncludes) {
        writePaths(element, ELEMENT_FORCED_INCLUDE, forcedIncludes);
    }

    static List<String> readForcedIncludes(Element element) {
        return readPaths(element, ELEMENT_FORCED_INCLUDE);
    }

    private static void writePaths(Element element, String childName, List<String> paths) {
        for (String path : paths) {
            element.addContent(new Element(childName).setAttribute("path", path));
        }
    }

    private static List<String> readPaths(Element element, String childName) {
        List<String> paths = new ArrayList<>();
        for (Element child : element.getChildren(childName)) {
            String path = child.getAttributeValue("path");
            if (path != null) {
                paths.add(path);
            }
        }
        return paths;
    }
}
