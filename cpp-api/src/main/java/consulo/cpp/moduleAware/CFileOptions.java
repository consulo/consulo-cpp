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

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Options a {@link CFileOptionsContributor} adds to a file: its {@code defines} override those of the module, its
 * {@code includePaths} are searched after the module's, and its {@code forcedIncludes} come before the module's - nvcc
 * includes {@code cuda_runtime.h} before anything a user forces.
 *
 * @param cppStandard the id of the C++ language version the file is parsed with when its module configures none
 * @author VISTALL
 * @since 2026-11-13
 */
public record CFileOptions(Map<String, String> defines,
                           List<String> includePaths,
                           List<String> forcedIncludes,
                           @Nullable String cppStandard) {
    public CFileOptions {
        defines = Map.copyOf(defines);
        includePaths = List.copyOf(includePaths);
        forcedIncludes = List.copyOf(forcedIncludes);
    }
}
