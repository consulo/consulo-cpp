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
package consulo.cpp.cmake;

import consulo.annotation.component.ExtensionImpl;
import consulo.cmake.externalSystem.model.CMakeCompileData;
import consulo.cpp.cmake.module.CMakeCModuleExtension;
import consulo.cpp.cmake.module.CMakeCMutableModuleExtension;
import consulo.cpp.module.extension.CDefaultModuleExtension;
import consulo.module.extension.MutableModuleExtension;
import consulo.cpp.module.extension.CCompileSettings;
import consulo.externalSystem.model.DataNode;
import consulo.externalSystem.model.Key;
import consulo.externalSystem.model.ProjectKeys;
import consulo.externalSystem.model.project.ModuleData;
import consulo.externalSystem.service.project.manage.ProjectDataService;
import consulo.externalSystem.util.DisposeAwareProjectChange;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.project.Project;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2026-10-10
 */
@ExtensionImpl
public class CMakeCompileDataService implements ProjectDataService<CMakeCompileData, Module> {
    @Override
    public Key<CMakeCompileData> getTargetDataKey() {
        return CMakeCompileData.KEY;
    }

    @Override
    public void importData(Collection<DataNode<CMakeCompileData>> toImport, Project project, boolean synchronous) {
        if (toImport.isEmpty()) {
            return;
        }
        Map<String, CMakeCompileSettingsCollector> collectors = new LinkedHashMap<>();
        for (DataNode<CMakeCompileData> node : toImport) {
            ModuleData moduleData = node.getData(ProjectKeys.MODULE);
            if (moduleData != null) {
                collectors.computeIfAbsent(moduleData.getInternalName(), name -> new CMakeCompileSettingsCollector()).add(node.getData());
            }
        }
        ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(project) {
            @Override
            public void execute() {
                ModuleManager moduleManager = ModuleManager.getInstance(project);
                for (Map.Entry<String, CMakeCompileSettingsCollector> entry : collectors.entrySet()) {
                    Module module = moduleManager.findModuleByName(entry.getKey());
                    if (module != null && !module.isDisposed()) {
                        apply(module, entry.getValue().build());
                    }
                }
            }
        });
    }

    /**
     * A module CMake imports has one framework, CMake, and C/C++ as its language: the CMake extension is enabled with the
     * C/C++ one under it, and the C/C++ set up by hand - another framework - is not.
     */
    private static void apply(Module module, CCompileSettings settings) {
        ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
        CMakeCMutableModuleExtension extension = rootModel.getExtensionWithoutCheck(CMakeCMutableModuleExtension.class);
        if (extension == null) {
            rootModel.dispose();
            return;
        }
        if (rootModel.getExtensionWithoutCheck(CMakeCModuleExtension.PARENT_ID) instanceof MutableModuleExtension<?> cmake) {
            cmake.setEnabled(true);
        }
        if (rootModel.getExtensionWithoutCheck(CDefaultModuleExtension.ID) instanceof MutableModuleExtension<?> manual) {
            manual.setEnabled(false);
        }
        extension.setEnabled(true);
        extension.setCompileSettings(settings);
        rootModel.commit();
    }

    @Override
    public void removeData(Collection<? extends Module> toRemove, Project project, boolean synchronous) {
    }
}
