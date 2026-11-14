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
package consulo.cpp.preprocessor.expand;

import consulo.cpp.lang.version.CLanguageVersion;
import consulo.cpp.preprocessor.psi.CPreprocessorDefineDirective;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What including a header did to the macros, remembered on the header: the macros it read, what it made of the
 * macros it changed, the files it went through. A header is included from thousands of files and, through the
 * headers including it, again in each; when what it reads is the same, including it again does the same, without
 * walking it and its includes again.
 *
 * @author VISTALL
 * @since 2026-11-14
 */
final class IncludeEffects {
    /**
     * The expansions of a header remembered: a header included under different macros - a {@code __need_size_t} -
     * has one each.
     */
    private static final int MAX_PER_FILE = 8;

    private static final Key<List<IncludeEffects>> KEY = Key.create("IncludeEffects");

    private final Map<String, ExpandedMacro> myReads;
    private final Map<String, ExpandedMacro> myEffects;
    private final Map<VirtualFile, Long> myStamps;
    /**
     * The files the include skipped as expanded already: it does the same only where they are expanded already too.
     */
    private final Set<VirtualFile> mySkipped;
    private final List<VirtualFile> myIncludePaths;
    /**
     * The version the macros were lexed in: a header of a C file and of a C++ file has its words read apart.
     */
    private final CLanguageVersion myLanguageVersion;

    private IncludeEffects(Map<String, ExpandedMacro> reads, Map<String, ExpandedMacro> effects, Map<VirtualFile, Long> stamps,
                           Set<VirtualFile> skipped, List<VirtualFile> includePaths, CLanguageVersion languageVersion) {
        myReads = reads;
        myEffects = effects;
        myStamps = stamps;
        mySkipped = skipped;
        myIncludePaths = includePaths;
        myLanguageVersion = languageVersion;
    }

    /**
     * What a header made of the macros when it read them as {@code macros} has them now, if it was included so before.
     *
     * @param processed the files expanded already, which an include does not expand again
     */
    static @Nullable IncludeEffects find(VirtualFile file, MacroTable macros, Set<VirtualFile> processed, List<VirtualFile> includePaths,
                                         CLanguageVersion languageVersion) {
        List<IncludeEffects> known = file.getUserData(KEY);
        if (known == null) {
            return null;
        }
        for (IncludeEffects effects : known) {
            if (effects.myLanguageVersion == languageVersion && effects.appliesTo(file, macros, processed, includePaths)) {
                return effects;
            }
        }
        return null;
    }

    private boolean appliesTo(VirtualFile file, MacroTable macros, Set<VirtualFile> processed, List<VirtualFile> includePaths) {
        if (!myIncludePaths.equals(includePaths) || !processed.containsAll(mySkipped)) {
            return false;
        }
        for (Map.Entry<VirtualFile, Long> stamp : myStamps.entrySet()) {
            VirtualFile went = stamp.getKey();
            if (!went.isValid() || went.getModificationStamp() != stamp.getValue()) {
                return false;
            }
            // a file expanded already is skipped by the include now, as it was not then
            if (!went.equals(file) && processed.contains(went)) {
                return false;
            }
        }
        for (Map.Entry<String, ExpandedMacro> read : myReads.entrySet()) {
            if (!same(read.getValue(), macros.peek(read.getKey()))) {
                return false;
            }
        }
        for (ExpandedMacro macro : myEffects.values()) {
            CPreprocessorDefineDirective element = macro == null ? null : macro.getElement();
            if (element != null && !element.isValid()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Remembers what an include did, unless it read the macros as a whole.
     */
    static void remember(VirtualFile file, MacroTable.Recording recording, MacroTable macros, List<VirtualFile> includePaths,
                         CLanguageVersion languageVersion) {
        if (!recording.complete) {
            return;
        }
        Map<String, ExpandedMacro> effects = new LinkedHashMap<>();
        for (String name : recording.written) {
            effects.put(name, macros.peek(name));
        }
        Map<VirtualFile, Long> stamps = new HashMap<>();
        stamps.put(file, file.getModificationStamp());
        for (VirtualFile went : recording.files) {
            stamps.put(went, went.getModificationStamp());
        }
        IncludeEffects remembered = new IncludeEffects(new HashMap<>(recording.reads), effects, stamps, Set.copyOf(recording.skipped),
            List.copyOf(includePaths), languageVersion);
        List<IncludeEffects> known = file.getUserData(KEY);
        List<IncludeEffects> updated = new ArrayList<>(known == null ? List.of() : known);
        if (updated.size() >= MAX_PER_FILE) {
            updated.remove(0);
        }
        updated.add(remembered);
        file.putUserData(KEY, List.copyOf(updated));
    }

    /**
     * The macros the include changes, each with what it makes of it - null for one it undefines.
     */
    Map<String, ExpandedMacro> effects() {
        return myEffects;
    }

    /**
     * The macros the include reads, which an include around it reads too.
     */
    Set<String> reads() {
        return myReads.keySet();
    }

    Set<VirtualFile> files() {
        return myStamps.keySet();
    }

    Set<VirtualFile> skipped() {
        return mySkipped;
    }

    private static boolean same(@Nullable ExpandedMacro a, @Nullable ExpandedMacro b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return Objects.equals(a.getParameters(), b.getParameters()) && a.getText().toString().contentEquals(b.getText());
    }
}
