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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CPreprocessorOptions} is the payload the module-aware index stores for a C/C++ file, so it has to
 * round-trip exactly - two modules are treated as sharing indexed data only when these values match.
 */
public class CPreprocessorOptionsExternalizerTest {
    @Test
    public void definesAndIncludePathsRoundTrip() throws IOException {
        Map<String, String> defines = new LinkedHashMap<>();
        defines.put("DEBUG", "1");
        defines.put("GUARD", "");
        defines.put("NAME", "value with spaces");
        List<String> includePaths = List.of("/usr/include", "/opt/sdk/include");

        CPreprocessorOptions restored = roundTrip(new CPreprocessorOptions(defines, includePaths, "abc123"));
        assertEquals(defines, restored.defines());
        assertEquals(includePaths, restored.includePaths());
        assertEquals("abc123", restored.includeFingerprint());
    }

    @Test
    public void emptyOptionsRoundTrip() throws IOException {
        CPreprocessorOptions restored = roundTrip(CPreprocessorOptions.EMPTY);
        assertEquals(Map.of(), restored.defines());
        assertEquals(List.of(), restored.includePaths());
        assertTrue(restored.isEmpty());
    }

    /**
     * Define order is normalised, so two modules declaring the same defines in a different order share one copy
     * of the indexed data.
     */
    @Test
    public void defineOrderDoesNotChangeTheStoredForm() throws IOException {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("A", "1");
        first.put("B", "2");

        Map<String, String> second = new LinkedHashMap<>();
        second.put("B", "2");
        second.put("A", "1");

        assertEquals(new CPreprocessorOptions(first, List.of(), ""), new CPreprocessorOptions(second, List.of(), ""));
        assertArrayEquals(save(new CPreprocessorOptions(first, List.of(), "")), save(new CPreprocessorOptions(second, List.of(), "")));
    }

    /**
     * The stored payload must not depend on the JVM run: the module-aware index compares it byte for byte with what
     * the previous session stored, and a difference re-indexes the file. Defines are kept sorted for that.
     */
    @Test
    public void definesAreStoredSorted() throws IOException {
        Map<String, String> defines = new LinkedHashMap<>();
        for (int i = 20; i > 0; i--) {
            defines.put("DEFINE_" + (char) ('A' + i), Integer.toString(i));
        }

        CPreprocessorOptions options = new CPreprocessorOptions(defines, List.of(), "");
        List<String> sorted = defines.keySet().stream().sorted().toList();
        assertEquals(sorted, List.copyOf(options.defines().keySet()));
        assertEquals(sorted, List.copyOf(roundTrip(options).defines().keySet()));
    }

    /**
     * Include order, unlike define order, is meaningful - it decides which directory wins - so it is preserved
     * and two modules searching the same directories in a different order do not share indexed data.
     */
    @Test
    public void includeOrderIsPreserved() throws IOException {
        CPreprocessorOptions first = new CPreprocessorOptions(Map.of(), List.of("/a", "/b"), "");
        CPreprocessorOptions second = new CPreprocessorOptions(Map.of(), List.of("/b", "/a"), "");

        assertFalse(first.equals(second));
        assertEquals(List.of("/a", "/b"), roundTrip(first).includePaths());
    }

    /**
     * The whole point of the fingerprint: an edit to an included header changes it, which changes the option and
     * so re-indexes every file that includes the header. Options differing only by fingerprint must not be equal.
     */
    @Test
    public void fingerprintSeparatesOptions() throws IOException {
        CPreprocessorOptions before = new CPreprocessorOptions(Map.of(), List.of(), "aaaa");
        CPreprocessorOptions after = new CPreprocessorOptions(Map.of(), List.of(), "bbbb");

        assertFalse(before.equals(after));
        assertEquals("aaaa", roundTrip(before).includeFingerprint());
        assertFalse(before.isEmpty());
    }

    /**
     * A file that includes nothing and configures nothing stays fully sharable, so the fingerprint does not cost
     * sharing where there is no dependency to track.
     */
    @Test
    public void noIncludesMeansEmptyOptions() {
        assertTrue(new CPreprocessorOptions(Map.of(), List.of(), "").isEmpty());
    }

    private static byte[] save(CPreprocessorOptions options) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream stream = new DataOutputStream(out)) {
            CPreprocessorOptionsExternalizer.INSTANCE.save(stream, options);
        }
        return out.toByteArray();
    }

    private static CPreprocessorOptions roundTrip(CPreprocessorOptions options) throws IOException {
        try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(save(options)))) {
            return CPreprocessorOptionsExternalizer.INSTANCE.read(stream);
        }
    }
}
