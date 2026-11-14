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

import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The macros in scope while a file is expanded, shared with the files it includes, which change it in place. It can
 * record what the expansion of an include reads and changes, for {@link IncludeEffects} to replay the include where it
 * reads the same.
 *
 * @author VISTALL
 * @since 2026-11-14
 */
final class MacroTable extends LinkedHashMap<String, ExpandedMacro> {
    /**
     * What the expansion of one include read of the macros and changed of them, and the files it went through.
     */
    static final class Recording {
        /**
         * The macros read before the include changed them, with what they were - null for one not defined.
         */
        final Map<String, ExpandedMacro> reads = new HashMap<>();
        final Set<String> written = new LinkedHashSet<>();
        final Set<VirtualFile> files = new LinkedHashSet<>();
        /**
         * The files it did not expand, having been expanded before it: where they were not, it would expand them.
         */
        final Set<VirtualFile> skipped = new LinkedHashSet<>();
        /**
         * Whether it looked at all of the macros, iterating them: no set of names tells what it depends on.
         */
        boolean complete = true;
    }

    private final List<Recording> myRecordings = new ArrayList<>();

    MacroTable(Map<String, ExpandedMacro> macros) {
        super(macros);
    }

    Recording startRecording() {
        Recording recording = new Recording();
        myRecordings.add(recording);
        return recording;
    }

    void stopRecording(Recording recording) {
        myRecordings.remove(recording);
    }

    /**
     * Tells the recordings that a file is expanded under them.
     */
    void noteFile(VirtualFile file) {
        for (Recording recording : myRecordings) {
            recording.files.add(file);
        }
    }

    /**
     * Tells the recordings that an include of a file expanded before was skipped under them.
     */
    void noteSkipped(VirtualFile file) {
        for (Recording recording : myRecordings) {
            if (!recording.files.contains(file)) {
                recording.skipped.add(file);
            }
        }
    }

    /**
     * The macro, not counted as read.
     */
    @Nullable ExpandedMacro peek(String name) {
        return super.get(name);
    }

    @Override
    public ExpandedMacro get(Object name) {
        noteRead(name);
        return super.get(name);
    }

    @Override
    public boolean containsKey(Object name) {
        noteRead(name);
        return super.containsKey(name);
    }

    @Override
    public ExpandedMacro put(String name, ExpandedMacro macro) {
        noteWrite(name);
        return super.put(name, macro);
    }

    @Override
    public ExpandedMacro remove(Object name) {
        noteWrite(name);
        return super.remove(name);
    }

    @Override
    public Set<String> keySet() {
        noteAll();
        return super.keySet();
    }

    @Override
    public Set<Map.Entry<String, ExpandedMacro>> entrySet() {
        noteAll();
        return super.entrySet();
    }

    @Override
    public Collection<ExpandedMacro> values() {
        noteAll();
        return super.values();
    }

    private void noteRead(Object name) {
        if (myRecordings.isEmpty() || !(name instanceof String key)) {
            return;
        }
        for (Recording recording : myRecordings) {
            if (!recording.written.contains(key) && !recording.reads.containsKey(key)) {
                recording.reads.put(key, super.get(key));
            }
        }
    }

    private void noteWrite(Object name) {
        if (!(name instanceof String key)) {
            return;
        }
        for (Recording recording : myRecordings) {
            recording.written.add(key);
        }
    }

    private void noteAll() {
        for (Recording recording : myRecordings) {
            recording.complete = false;
        }
    }
}
