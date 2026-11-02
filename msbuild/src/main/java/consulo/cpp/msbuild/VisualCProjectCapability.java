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
package consulo.cpp.msbuild;

import consulo.annotation.component.ExtensionImpl;
import consulo.content.bundle.Sdk;
import consulo.cpp.lang.CHeaderFileType;
import consulo.cpp.lang.CPPHeaderFileType;
import consulo.cpp.module.extension.CDefaultModuleExtension;
import consulo.cpp.msbuild.module.VisualCMutableModuleExtension;
import consulo.module.extension.MutableModuleExtension;
import consulo.module.Module;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.msbuild.MSBuildEvaluatedItem;
import consulo.msbuild.MSBuildImportUtil;
import consulo.msbuild.MSBuildProcessProvider;
import consulo.msbuild.MSBuildProjectCapability;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author VISTALL
 * @since 2026-10-10
 */
@ExtensionImpl
public class VisualCProjectCapability implements MSBuildProjectCapability {
    public static final String ID = "VisualC";

    private static final Set<FileType> HEADER_FILE_TYPES = Set.of(CHeaderFileType.INSTANCE, CPPHeaderFileType.INSTANCE);

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void importModule(Module module,
                             ModifiableRootModel rootModel,
                             VirtualFile projectFile,
                             MSBuildProcessProvider buildProcessProvider,
                             Sdk msBuildSdk,
                             Map<String, String> properties,
                             List<? extends MSBuildEvaluatedItem> referencePaths,
                             Set<String> targets) {
        VisualCMutableModuleExtension extension = rootModel.getExtensionWithoutCheck(VisualCMutableModuleExtension.class);
        VirtualFile projectDir = projectFile.getParent();
        if (extension == null || projectDir == null) {
            return;
        }

        VisualCProjectModel model = VisualCProjectModel.of(Path.of(projectDir.getPath()), properties);

        // the project's framework is Visual C++ of MSBuild: the C/C++ set up by hand is another one
        if (rootModel.getExtensionWithoutCheck(CDefaultModuleExtension.ID) instanceof MutableModuleExtension<?> manual) {
            manual.setEnabled(false);
        }
        extension.setEnabled(true);
        extension.setProjectFilePath(projectFile.getPath());
        extension.setConfiguration(model.configuration());
        extension.setPlatform(model.platform());
        extension.setOutputPath(pathString(model.outDir()));
        extension.setExecutablePath(pathString(model.targetPath()));
        extension.setConfigurationType(model.configurationType());
        extension.setDebuggerCommand(pathString(model.debuggerCommand()));
        extension.setDebuggerArguments(model.debuggerArguments());
        extension.setDebuggerWorkingDirectory(model.debuggerWorkingDirectory().toString());
        extension.setDebuggerEnvironment(model.debuggerEnvironment());

        Set<String> contentUrls = MSBuildImportUtil.collectContentUrls(rootModel);
        List<Path> baseDirs = model.baseDirs();
        MSBuildImportUtil.excludeFolder(rootModel, baseDirs, model.outDir(), contentUrls);
        MSBuildImportUtil.excludeFolder(rootModel, baseDirs, model.intDir(), contentUrls);
    }

    @Override
    public void importItems(Module module,
                            ModifiableRootModel rootModel,
                            VirtualFile projectFile,
                            Map<String, String> properties,
                            List<? extends MSBuildEvaluatedItem> items) {
        VisualCMutableModuleExtension extension = rootModel.getExtensionWithoutCheck(VisualCMutableModuleExtension.class);
        VirtualFile projectDir = projectFile.getParent();
        if (extension == null || projectDir == null) {
            return;
        }

        Path projectDirPath = Path.of(projectDir.getPath());
        VisualCProjectModel model = VisualCProjectModel.of(projectDirPath, properties);
        VisualCCompileSettings settings = VisualCCompileSettings.of(projectDirPath, items);

        Map<String, String> defines = new LinkedHashMap<>(settings.defines());
        VisualCPredefinedMacros.fill(defines, model, settings);

        extension.setDefines(defines);
        extension.setIncludePaths(settings.includeDirectories());
        extension.setForcedIncludes(settings.forcedIncludes());
        extension.setCStandard(settings.cStandard());
        extension.setCppStandard(settings.cppStandard());

        Set<String> contentUrls = MSBuildImportUtil.collectContentUrls(rootModel);
        for (Path directory : settings.sourceDirectories()) {
            MSBuildImportUtil.addFiles(rootModel, directory, HEADER_FILE_TYPES, contentUrls);
        }
    }

    private static @Nullable String pathString(@Nullable Path path) {
        return path != null ? path.toString() : null;
    }
}
