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

import consulo.annotation.access.RequiredReadAction;
import consulo.cpp.lang.version.CLanguageVersion;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.content.layer.extension.ModuleExtensionBase;
import org.jdom.Element;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Per-module preprocessor defines. These are the defines every C/C++ file of the module is indexed and parsed
 * under, and they are the value the module-aware index keys its variants on.
 */
public class CDefaultModuleExtension extends ModuleExtensionBase<CDefaultModuleExtension> implements CModuleExtension<CDefaultModuleExtension> {
    public static final String ID = "cpp";

    protected final CCompileSettings myCompileSettings = new CCompileSettings();

    public CDefaultModuleExtension(String id, ModuleRootLayer moduleRootLayer) {
        super(id, moduleRootLayer);
    }

    @Override
    public Map<String, String> getDefines() {
        return myCompileSettings.getDefines();
    }

    @Override
    public List<String> getIncludePaths() {
        return myCompileSettings.getIncludePaths();
    }

    @Override
    public List<String> getForcedIncludes() {
        return myCompileSettings.getForcedIncludes();
    }

    @Override
    public @Nullable CLanguageVersion getCStandard() {
        return myCompileSettings.getCStandard();
    }

    @Override
    public @Nullable CLanguageVersion getCppStandard() {
        return myCompileSettings.getCppStandard();
    }

    @Override
    public @Nullable String getTarget() {
        return myCompileSettings.getTarget();
    }

    @RequiredReadAction
    @Override
    public void commit(CDefaultModuleExtension mutableModuleExtension) {
        super.commit(mutableModuleExtension);
        myCompileSettings.copyFrom(mutableModuleExtension.myCompileSettings);
    }

    @Override
    protected void getStateImpl(Element element) {
        super.getStateImpl(element);
        myCompileSettings.write(element);
    }

    @RequiredReadAction
    @Override
    protected void loadStateImpl(Element element) {
        super.loadStateImpl(element);
        myCompileSettings.read(element);
    }
}
