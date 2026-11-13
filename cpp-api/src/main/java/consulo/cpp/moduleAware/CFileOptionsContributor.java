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

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.module.Module;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What the files of a dialect are preprocessed with besides the options of their module - a CUDA {@code .cu}: the
 * headers of the toolkit, {@code cuda_runtime.h} above its first line and the macros of nvcc, which a {@code .cpp} of the
 * same module has none of. Asked for every C file of a module; the file decides, by its type.
 *
 * @author VISTALL
 * @since 2026-11-13
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface CFileOptionsContributor {
    ExtensionPointName<CFileOptionsContributor> EP_NAME = ExtensionPointName.create(CFileOptionsContributor.class);

    /**
     * What {@code file} of {@code module} is preprocessed with besides the module's options; null when nothing.
     */
    @Nullable CFileOptions getOptions(Module module, VirtualFile file);

    /**
     * What every contributor adds to {@code file}, in the order they are registered.
     */
    static List<CFileOptions> optionsOf(Module module, VirtualFile file) {
        List<CFileOptions> result = new ArrayList<>(1);
        for (CFileOptionsContributor contributor : EP_NAME.getExtensionList()) {
            CFileOptions options = contributor.getOptions(module, file);
            if (options != null) {
                result.add(options);
            }
        }
        return result;
    }
}
