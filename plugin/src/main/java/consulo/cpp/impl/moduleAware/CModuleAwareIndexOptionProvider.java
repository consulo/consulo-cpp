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
import consulo.cpp.lang.CLanguage;
import consulo.cpp.module.extension.CModuleExtension;
import consulo.cpp.moduleAware.CDefineEnv;
import consulo.cpp.moduleAware.CIncludeWalker;
import consulo.cpp.moduleAware.CPreprocessorOptions;
import consulo.cpp.moduleAware.CPreprocessorOptionsExternalizer;
import consulo.language.file.LanguageFileType;
import consulo.language.psi.stub.IndexOption;
import consulo.language.psi.stub.ModuleAwareIndexOptionProvider;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tells the module-aware index that a C/C++ file's indexed data depends on the defines of its module: modules
 * with the same defines share one copy, modules with different defines get their own.
 */
@ExtensionImpl
public class CModuleAwareIndexOptionProvider implements ModuleAwareIndexOptionProvider {
    private final Set<FileType> myFileTypes;

    /**
     * Claims every file type of the C language, so the files of a dialect plugin - {@code .m}, {@code .ino} - get
     * their module's defines too.
     */
    @Inject
    public CModuleAwareIndexOptionProvider(FileTypeRegistry fileTypeRegistry) {
        myFileTypes = fileTypesOf(fileTypeRegistry);
    }

    static Set<FileType> fileTypesOf(FileTypeRegistry fileTypeRegistry) {
        Set<FileType> fileTypes = new LinkedHashSet<>();
        for (FileType fileType : fileTypeRegistry.getRegisteredFileTypes()) {
            if (fileType instanceof LanguageFileType languageFileType && languageFileType.getLanguage() == CLanguage.INSTANCE) {
                fileTypes.add(fileType);
            }
        }
        return Collections.unmodifiableSet(fileTypes);
    }

    @Override
    public String getId() {
        return CDefineEnv.PROVIDER_ID;
    }

    @Override
    public int getVersion() {
        // 3: the language version id moved to CLanguageVersionIndexOptionProvider
        return 3;
    }

    @Override
    public Set<FileType> getInputFileTypes() {
        return myFileTypes;
    }

    private static List<VirtualFile> resolveIncludeRoots(List<String> includePaths) {
        List<VirtualFile> roots = new ArrayList<>();
        for (String path : includePaths) {
            VirtualFile root = LocalFileSystem.getInstance().findFileByPath(path);
            if (root != null && root.isDirectory()) {
                roots.add(root);
            }
        }
        return roots;
    }

    /**
     * What makes an edit to an included header re-index the files including it. A file of a library - a system header
     * reached through the module's header library - is not edited in the project, and walking the includes of every
     * one of the thousands of headers there would cost more than the whole index, so it has none.
     */
    private static String includeFingerprint(Module module, CModuleExtension<?> extension, VirtualFile file) {
        if (ProjectFileIndex.getInstance(module.getProject()).getModuleForFile(file) == null) {
            return "";
        }
        return CIncludeWalker.fingerprintFor(file, resolveIncludeRoots(extension.getIncludePaths()));
    }

    @Override
    public IndexOption getOptions(Module module, VirtualFile file) {
        CModuleExtension<?> extension = module.getExtension(CModuleExtension.class);
        if (extension == null) {
            return IndexOption.fullySharable();
        }

        CPreprocessorOptions options = CPreprocessorOptions.of(extension, includeFingerprint(module, extension, file));
        if (options.isEmpty()) {
            return IndexOption.fullySharable();
        }
        return IndexOption.sharablePerOption(
            options,
            CPreprocessorOptionsExternalizer.INSTANCE,
            LocalizeValue.of(CModuleExtension.formatDefines(options.defines()))
        );
    }
}
