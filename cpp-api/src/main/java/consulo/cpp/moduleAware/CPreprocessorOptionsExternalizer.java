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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CPreprocessorOptionsExternalizer implements DataExternalizer<CPreprocessorOptions> {
    public static final CPreprocessorOptionsExternalizer INSTANCE = new CPreprocessorOptionsExternalizer();

    @Override
    public void save(DataOutput out, CPreprocessorOptions value) throws IOException {
        Map<String, String> defines = value.defines();
        DataInputOutputUtil.writeINT(out, defines.size());
        for (Map.Entry<String, String> entry : defines.entrySet()) {
            IOUtil.writeUTF(out, entry.getKey());
            IOUtil.writeUTF(out, entry.getValue());
        }

        List<String> includePaths = value.includePaths();
        DataInputOutputUtil.writeINT(out, includePaths.size());
        for (String path : includePaths) {
            IOUtil.writeUTF(out, path);
        }

        IOUtil.writeUTF(out, value.includeFingerprint());
    }

    @Override
    public CPreprocessorOptions read(DataInput in) throws IOException {
        int defineCount = DataInputOutputUtil.readINT(in);
        Map<String, String> defines = new TreeMap<>();
        for (int i = 0; i < defineCount; i++) {
            String name = IOUtil.readUTF(in);
            defines.put(name, IOUtil.readUTF(in));
        }

        int pathCount = DataInputOutputUtil.readINT(in);
        List<String> includePaths = new ArrayList<>(pathCount);
        for (int i = 0; i < pathCount; i++) {
            includePaths.add(IOUtil.readUTF(in));
        }
        return new CPreprocessorOptions(defines, includePaths, IOUtil.readUTF(in));
    }
}
