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
package consulo.cpp.module.extension;

import consulo.disposer.Disposable;
import consulo.cpp.localize.CLocalize;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class CDefaultMutableModuleExtension extends CDefaultModuleExtension implements CMutableModuleExtension<CDefaultModuleExtension> {
    public CDefaultMutableModuleExtension(String id, ModuleRootLayer moduleRootLayer) {
        super(id, moduleRootLayer);
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

    @Override
    public void setEnabled(boolean val) {
        myIsEnabled = val;
    }

    @Override
    public boolean isModified(CDefaultModuleExtension originalExtension) {
        return myIsEnabled != originalExtension.isEnabled()
            || !myCompileSettings.equals(originalExtension.myCompileSettings);
    }

    @RequiredUIAccess
    @Nullable
    @Override
    public Component createConfigurationComponent(Disposable uiDisposable, Runnable updateOnCheck) {
        VerticalLayout layout = VerticalLayout.create();
        layout.add(Label.create(CLocalize.extensionDefinesLabel()));

        TextBox definesBox = TextBox.create(CModuleExtension.formatDefines(getDefines()));
        definesBox.addValueListener(event -> {
            setDefines(CModuleExtension.parseDefines(event.getValue()));
            updateOnCheck.run();
        });
        layout.add(definesBox);

        layout.add(Label.create(CLocalize.extensionIncludePathsLabel()));
        TextBox includeBox = TextBox.create(CModuleExtension.formatIncludePaths(getIncludePaths()));
        includeBox.addValueListener(event -> {
            setIncludePaths(CModuleExtension.parseIncludePaths(event.getValue()));
            updateOnCheck.run();
        });
        layout.add(includeBox);

        layout.add(Label.create(CLocalize.extensionForcedIncludesLabel()));
        TextBox forcedIncludeBox = TextBox.create(CModuleExtension.formatIncludePaths(getForcedIncludes()));
        forcedIncludeBox.addValueListener(event -> {
            setForcedIncludes(CModuleExtension.parseIncludePaths(event.getValue()));
            updateOnCheck.run();
        });
        layout.add(forcedIncludeBox);

        layout.add(Label.create(CLocalize.extensionTargetLabel()));
        String target = getTarget();
        TextBox targetBox = TextBox.create(target == null ? "" : target);
        targetBox.addValueListener(event -> {
            setTarget(event.getValue());
            updateOnCheck.run();
        });
        layout.add(targetBox);
        return layout;
    }
}
