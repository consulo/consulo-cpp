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
package consulo.cpp.impl.moduleAware;

import consulo.annotation.component.ExtensionImpl;
import consulo.cpp.module.extension.CModuleExtension;
import consulo.cpp.moduleAware.CLanguageVersionOption;
import consulo.cpp.moduleAware.CLanguageVersionOptionExternalizer;
import consulo.language.psi.stub.IndexOption;
import consulo.language.psi.stub.ModuleAwareIndexOptionProvider;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import jakarta.inject.Inject;

import java.util.Set;

/**
 * Tells the module-aware index that a C/C++ file is parsed with the standard its module configures. Kept apart from
 * {@link CModuleAwareIndexOptionProvider}: the version of every file is resolved while files are scanned, and this
 * option is a lookup on the module, while the preprocessor options walk the file's includes.
 */
@ExtensionImpl
public class CLanguageVersionIndexOptionProvider implements ModuleAwareIndexOptionProvider {
    private final Set<FileType> myFileTypes;

    @Inject
    public CLanguageVersionIndexOptionProvider(FileTypeRegistry fileTypeRegistry) {
        myFileTypes = CModuleAwareIndexOptionProvider.fileTypesOf(fileTypeRegistry);
    }

    @Override
    public String getId() {
        return CLanguageVersionOption.PROVIDER_ID;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Set<FileType> getInputFileTypes() {
        return myFileTypes;
    }

    @Override
    public IndexOption getOptions(Module module, VirtualFile file) {
        CModuleExtension<?> extension = module.getExtension(CModuleExtension.class);
        if (extension == null) {
            return IndexOption.fullySharable();
        }

        CLanguageVersionOption option = CLanguageVersionOption.of(extension, file);
        if (option.isEmpty()) {
            return IndexOption.fullySharable();
        }
        return IndexOption.sharablePerOption(option, CLanguageVersionOptionExternalizer.INSTANCE, LocalizeValue.of(option.languageVersionId()));
    }
}
