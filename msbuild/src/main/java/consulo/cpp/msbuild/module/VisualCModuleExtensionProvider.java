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

import consulo.annotation.component.ExtensionImpl;
import consulo.cpp.msbuild.localize.VisualCLocalize;
import consulo.localize.LocalizeValue;
import consulo.module.content.layer.ModuleExtensionProvider;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.extension.ModuleExtension;
import consulo.module.extension.MutableModuleExtension;
import consulo.msbuild.icon.MSBuildIconGroup;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 2026-10-10
 */
@ExtensionImpl
public class VisualCModuleExtensionProvider implements ModuleExtensionProvider<VisualCModuleExtension> {
    @Override
    public String getId() {
        return VisualCModuleExtension.ID;
    }

    @Override
    public boolean isSystemOnly() {
        return true;
    }

    @Override
    public LocalizeValue getName() {
        return VisualCLocalize.extensionName();
    }

    @Override
    public Image getIcon() {
        return MSBuildIconGroup.visualstudio();
    }

    @Override
    public ModuleExtension<VisualCModuleExtension> createImmutableExtension(ModuleRootLayer layer) {
        return new VisualCModuleExtension(getId(), layer);
    }

    @Override
    public MutableModuleExtension<VisualCModuleExtension> createMutableExtension(ModuleRootLayer layer) {
        return new VisualCMutableModuleExtension(getId(), layer);
    }
}
