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

import consulo.index.io.data.DataExternalizer;
import consulo.index.io.data.DataInputOutputUtil;
import consulo.index.io.data.IOUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The options of a file as the module-aware index stores them: the part files share - the defines, the include
 * directories, the forced includes - as one blob, then the file's own include fingerprint. A library's headers, thousands
 * of them, have the same blob, which is written from one array and decoded once.
 */
public class CPreprocessorOptionsExternalizer implements DataExternalizer<CPreprocessorOptions> {
    public static final CPreprocessorOptionsExternalizer INSTANCE = new CPreprocessorOptionsExternalizer();

    private static final int CACHE_SIZE = 64;

    /**
     * The blobs of the shared parts written, by the interned options they are of.
     */
    private final Map<SharedPart, byte[]> myWritten = lruMap();

    /**
     * The shared parts read, by their blob.
     */
    private final Map<ByteBuffer, SharedPart> myRead = lruMap();

    /**
     * What files with the same options share - the defines kept as the sorted map the options hold.
     */
    private record SharedPart(Map<String, String> defines, List<String> includePaths, List<String> forcedIncludes) {
    }

    @Override
    public void save(DataOutput out, CPreprocessorOptions value) throws IOException {
        SharedPart shared = new SharedPart(value.defines(), value.includePaths(), value.forcedIncludes());
        byte[] blob;
        synchronized (myWritten) {
            blob = myWritten.get(shared);
        }
        if (blob == null) {
            blob = encode(shared);
            synchronized (myWritten) {
                myWritten.put(shared, blob);
            }
        }
        DataInputOutputUtil.writeINT(out, blob.length);
        out.write(blob);
        IOUtil.writeUTF(out, value.includeFingerprint());
    }

    @Override
    public CPreprocessorOptions read(DataInput in) throws IOException {
        byte[] blob = new byte[DataInputOutputUtil.readINT(in)];
        in.readFully(blob);
        ByteBuffer key = ByteBuffer.wrap(blob);
        SharedPart shared;
        synchronized (myRead) {
            shared = myRead.get(key);
        }
        if (shared == null) {
            CPreprocessorOptions decoded = decode(blob);
            shared = new SharedPart(decoded.defines(), decoded.includePaths(), decoded.forcedIncludes());
            synchronized (myRead) {
                myRead.put(key, shared);
            }
        }
        return new CPreprocessorOptions(shared.defines(), shared.includePaths(), shared.forcedIncludes(), IOUtil.readUTF(in));
    }

    private static byte[] encode(SharedPart shared) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            DataInputOutputUtil.writeINT(out, shared.defines().size());
            for (Map.Entry<String, String> entry : shared.defines().entrySet()) {
                IOUtil.writeUTF(out, entry.getKey());
                IOUtil.writeUTF(out, entry.getValue());
            }
            writePaths(out, shared.includePaths());
            writePaths(out, shared.forcedIncludes());
        }
        return bytes.toByteArray();
    }

    private static CPreprocessorOptions decode(byte[] blob) throws IOException {
        DataInput in = new DataInputStream(new ByteArrayInputStream(blob));
        int defineCount = DataInputOutputUtil.readINT(in);
        Map<String, String> defines = new TreeMap<>();
        for (int i = 0; i < defineCount; i++) {
            String name = IOUtil.readUTF(in);
            defines.put(name, IOUtil.readUTF(in));
        }
        List<String> includePaths = readPaths(in);
        List<String> forcedIncludes = readPaths(in);
        return new CPreprocessorOptions(defines, includePaths, forcedIncludes, "");
    }

    private static <K, V> Map<K, V> lruMap() {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > CACHE_SIZE;
            }
        };
    }

    private static void writePaths(DataOutput out, List<String> paths) throws IOException {
        DataInputOutputUtil.writeINT(out, paths.size());
        for (String path : paths) {
            IOUtil.writeUTF(out, path);
        }
    }

    private static List<String> readPaths(DataInput in) throws IOException {
        int count = DataInputOutputUtil.readINT(in);
        List<String> paths = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            paths.add(IOUtil.readUTF(in));
        }
        return paths;
    }
}
