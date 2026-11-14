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
import consulo.cpp.moduleAware.CFileOptions;
import consulo.cpp.moduleAware.CFileOptionsContributor;
import consulo.cpp.moduleAware.CIncludeWalker;
import consulo.cpp.moduleAware.CPreprocessorOptions;
import consulo.cpp.moduleAware.CPreprocessorOptionsExternalizer;
import consulo.language.file.LanguageFileType;
import consulo.language.psi.stub.IndexOption;
import consulo.language.psi.stub.ModuleAwareIndexOptionProvider;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
        // 4: the forced includes
        // 5: the options of CFileOptionsContributor
        // 6: the shared part of the options stored as one blob
        return 6;
    }

    @Override
    public Set<FileType> getInputFileTypes() {
        return myFileTypes;
    }

    private static List<VirtualFile> resolveIncludeRoots(List<String> includePaths) {
        List<VirtualFile> roots = new ArrayList<>();
        for (String path : includePaths) {
            VirtualFile root = CIncludeWalker.findFile(path);
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
    private static String includeFingerprint(Module module, List<String> forcedIncludes, List<String> includePaths, VirtualFile file) {
        if (ProjectFileIndex.getInstance(module.getProject()).getModuleForFile(file) == null) {
            return "";
        }
        return CIncludeWalker.fingerprintFor(file, forcedIncludes, resolveIncludeRoots(includePaths));
    }

    @Override
    public IndexOption getOptions(Module module, VirtualFile file) {
        CModuleExtension<?> extension = module.getExtension(CModuleExtension.class);
        List<CFileOptions> contributed = CFileOptionsContributor.optionsOf(module, file);
        if (extension == null && contributed.isEmpty()) {
            return IndexOption.fullySharable();
        }

        CPreprocessorOptions merged = CPreprocessorOptions.of(extension, contributed, "");
        String fingerprint = includeFingerprint(module, merged.forcedIncludes(), merged.includePaths(), file);
        CPreprocessorOptions options = fingerprint.isEmpty() ? merged
            : new CPreprocessorOptions(merged.defines(), merged.includePaths(), merged.forcedIncludes(), fingerprint);
        if (options.isEmpty()) {
            return IndexOption.fullySharable();
        }
        // the headers of a library have the same options: one object, and one option shown with its defines formatted once
        CPreprocessorOptions interned = options.intern();
        synchronized (OPTIONS) {
            IndexOption known = OPTIONS.get(interned);
            if (known != null) {
                return known;
            }
        }
        IndexOption option = IndexOption.sharablePerOption(
            interned,
            CPreprocessorOptionsExternalizer.INSTANCE,
            LocalizeValue.of(CModuleExtension.formatDefines(interned.defines()))
        );
        synchronized (OPTIONS) {
            OPTIONS.put(interned, option);
        }
        return option;
    }

    private static final Map<CPreprocessorOptions, IndexOption> OPTIONS = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CPreprocessorOptions, IndexOption> eldest) {
            return size() > 64;
        }
    };
}
