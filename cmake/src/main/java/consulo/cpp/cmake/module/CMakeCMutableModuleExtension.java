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
package consulo.cpp.cmake.module;

import consulo.cpp.cmake.localize.CMakeCLocalize;
import consulo.cpp.module.extension.CCompileSettings;
import consulo.cpp.lang.version.CLanguageVersion;
import consulo.cpp.module.extension.CModuleExtension;
import consulo.cpp.module.extension.CMutableModuleExtension;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.util.FormBuilder;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2026-10-10
 */
public class CMakeCMutableModuleExtension extends CMakeCModuleExtension implements CMutableModuleExtension<CMakeCModuleExtension> {
    public CMakeCMutableModuleExtension(String id, ModuleRootLayer rootLayer) {
        super(id, rootLayer);
    }

    @Override
    public void setDefines(Map<String, String> defines) {
        myCompileSettings.setDefines(defines);
    }

    @Override
    public void setIncludePaths(List<String> includePaths) {
        myCompileSettings.setIncludePaths(includePaths);
    }

    @Override
    public void setForcedIncludes(List<String> forcedIncludes) {
        myCompileSettings.setForcedIncludes(forcedIncludes);
    }

    @Override
    public void setTarget(@Nullable String target) {
        myCompileSettings.setTarget(target);
    }

    public void setCompileSettings(CCompileSettings settings) {
        myCompileSettings.copyFrom(settings);
    }

    @Override
    public void setEnabled(boolean enabled) {
        myIsEnabled = enabled;
    }

    @Override
    public boolean isModified(CMakeCModuleExtension originalExtension) {
        return myIsEnabled != originalExtension.isEnabled() || !myCompileSettings.equals(originalExtension.myCompileSettings);
    }

    @RequiredUIAccess
    @Override
    public @Nullable Component createConfigurationComponent(Disposable uiDisposable, Runnable updateOnCheck) {
        return FormBuilder.create()
            .addLabeled(CMakeCLocalize.extensionDefinesLabel(), Label.create(LocalizeValue.of(CModuleExtension.formatDefines(getDefines()))))
            .addLabeled(CMakeCLocalize.extensionIncludePathsLabel(), Label.create(LocalizeValue.of(CModuleExtension.formatIncludePaths(getIncludePaths()))))
            .addLabeled(CMakeCLocalize.extensionCStandardLabel(), Label.create(LocalizeValue.of(standardName(getCStandard()))))
            .addLabeled(CMakeCLocalize.extensionCppStandardLabel(), Label.create(LocalizeValue.of(standardName(getCppStandard()))))
            .build();
    }

    private static String standardName(@Nullable CLanguageVersion standard) {
        return standard == null ? "" : standard.getPresentableName();
    }
}
