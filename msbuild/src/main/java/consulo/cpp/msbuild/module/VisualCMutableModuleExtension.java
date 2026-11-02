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
package consulo.cpp.msbuild.module;

import consulo.cpp.lang.version.CLanguageVersion;
import consulo.cpp.module.extension.CModuleExtension;
import consulo.cpp.module.extension.CMutableModuleExtension;
import consulo.cpp.msbuild.localize.VisualCLocalize;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.annotation.RequiredUIAccess;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2026-10-10
 */
public class VisualCMutableModuleExtension extends VisualCModuleExtension implements CMutableModuleExtension<VisualCModuleExtension> {
    public VisualCMutableModuleExtension(String id, ModuleRootLayer rootLayer) {
        super(id, rootLayer);
    }

    public void setConfigurationType(@Nullable String configurationType) {
        myConfigurationType = configurationType;
    }

    public void setDebuggerCommand(@Nullable String debuggerCommand) {
        myDebuggerCommand = debuggerCommand;
    }

    public void setDebuggerArguments(@Nullable String debuggerArguments) {
        myDebuggerArguments = debuggerArguments;
    }

    public void setDebuggerWorkingDirectory(@Nullable String debuggerWorkingDirectory) {
        myDebuggerWorkingDirectory = debuggerWorkingDirectory;
    }

    public void setDebuggerEnvironment(Map<String, String> debuggerEnvironment) {
        myDebuggerEnvironment = new LinkedHashMap<>(debuggerEnvironment);
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

    public void setCStandard(@Nullable CLanguageVersion standard) {
        myCompileSettings.setCStandard(standard);
    }

    public void setCppStandard(@Nullable CLanguageVersion standard) {
        myCompileSettings.setCppStandard(standard);
    }

    @Override
    public void setEnabled(boolean enabled) {
        myIsEnabled = enabled;
    }

    @Override
    public boolean isModified(VisualCModuleExtension originalExtension) {
        return isModifiedImpl(originalExtension);
    }

    @RequiredUIAccess
    @Override
    public @Nullable Component createConfigurationComponent(Disposable uiDisposable, Runnable updateOnCheck) {
        return createProjectFormBuilder()
            .addLabeled(VisualCLocalize.extensionConfigurationTypeLabel(), Label.create(LocalizeValue.of(valueOrEmpty(myConfigurationType))))
            .addLabeled(VisualCLocalize.extensionDebuggerCommandLabel(), Label.create(LocalizeValue.of(valueOrEmpty(myDebuggerCommand))))
            .addLabeled(VisualCLocalize.extensionDefinesLabel(), Label.create(LocalizeValue.of(CModuleExtension.formatDefines(getDefines()))))
            .addLabeled(VisualCLocalize.extensionIncludePathsLabel(), Label.create(LocalizeValue.of(CModuleExtension.formatIncludePaths(getIncludePaths()))))
            .build();
    }
}
