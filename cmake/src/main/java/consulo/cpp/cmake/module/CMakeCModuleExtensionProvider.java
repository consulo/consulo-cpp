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

import consulo.annotation.component.ExtensionImpl;
import consulo.cmake.icon.CMakeIconGroup;
import consulo.cpp.cmake.localize.CMakeCLocalize;
import consulo.cpp.module.extension.CDefaultModuleExtension;
import consulo.localize.LocalizeValue;
import consulo.module.content.layer.ModuleExtensionProvider;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.extension.ModuleExtension;
import consulo.module.extension.MutableModuleExtension;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 2026-10-10
 */
@ExtensionImpl
public class CMakeCModuleExtensionProvider implements ModuleExtensionProvider<CMakeCModuleExtension> {
    @Override
    public String getId() {
        return CMakeCModuleExtension.ID;
    }

    @Override
    public @Nullable String getParentId() {
        return CMakeCModuleExtension.PARENT_ID;
    }

    @Override
    public boolean isSystemOnly() {
        return true;
    }

    @Override
    public LocalizeValue getName() {
        return CMakeCLocalize.extensionName();
    }

    @Override
    public Image getIcon() {
        return CMakeIconGroup.cmake();
    }

    @Override
    public ModuleExtension<CMakeCModuleExtension> createImmutableExtension(ModuleRootLayer layer) {
        return new CMakeCModuleExtension(getId(), layer);
    }

    @Override
    public MutableModuleExtension<CMakeCModuleExtension> createMutableExtension(ModuleRootLayer layer) {
        return new CMakeCMutableModuleExtension(getId(), layer);
    }
}
