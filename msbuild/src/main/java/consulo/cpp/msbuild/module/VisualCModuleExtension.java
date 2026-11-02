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

import consulo.annotation.access.RequiredReadAction;
import consulo.cpp.module.extension.CCompileSettings;
import consulo.cpp.lang.version.CLanguageVersion;
import consulo.cpp.module.extension.CModuleExtension;
import consulo.cpp.msbuild.VisualCProjectModel;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.msbuild.module.extension.MSBuildModuleExtensionBase;
import org.jdom.Element;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author VISTALL
 * @since 2026-10-10
 */
public class VisualCModuleExtension extends MSBuildModuleExtensionBase<VisualCModuleExtension> implements CModuleExtension<VisualCModuleExtension> {
    public static final String ID = "visualc-msbuild";

    private static final String ATTR_CONFIGURATION_TYPE = "configuration-type";
    private static final String ATTR_DEBUGGER_COMMAND = "debugger-command";
    private static final String ATTR_DEBUGGER_ARGUMENTS = "debugger-arguments";
    private static final String ATTR_DEBUGGER_WORKING_DIRECTORY = "debugger-working-directory";
    private static final String ELEMENT_ENV = "env";
    private static final String ATTR_ENV_NAME = "name";
    private static final String ATTR_ENV_VALUE = "value";

    protected @Nullable String myConfigurationType;
    protected @Nullable String myDebuggerCommand;
    protected @Nullable String myDebuggerArguments;
    protected @Nullable String myDebuggerWorkingDirectory;
    protected Map<String, String> myDebuggerEnvironment = new LinkedHashMap<>();
    protected final CCompileSettings myCompileSettings = new CCompileSettings();

    public VisualCModuleExtension(String id, ModuleRootLayer rootLayer) {
        super(id, rootLayer);
    }

    public @Nullable String getConfigurationType() {
        return myConfigurationType;
    }

    public @Nullable String getDebuggerCommand() {
        return myDebuggerCommand;
    }

    public @Nullable String getDebuggerArguments() {
        return myDebuggerArguments;
    }

    public @Nullable String getDebuggerWorkingDirectory() {
        return myDebuggerWorkingDirectory;
    }

    public Map<String, String> getDebuggerEnvironment() {
        return Map.copyOf(myDebuggerEnvironment);
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

    public boolean isRunnable() {
        String debuggerCommand = myDebuggerCommand;
        return debuggerCommand != null
            && (VisualCProjectModel.APPLICATION.equals(myConfigurationType) || !debuggerCommand.equals(myExecutablePath));
    }

    @RequiredReadAction
    @Override
    public void commit(VisualCModuleExtension mutableModuleExtension) {
        super.commit(mutableModuleExtension);
        myConfigurationType = mutableModuleExtension.getConfigurationType();
        myDebuggerCommand = mutableModuleExtension.getDebuggerCommand();
        myDebuggerArguments = mutableModuleExtension.getDebuggerArguments();
        myDebuggerWorkingDirectory = mutableModuleExtension.getDebuggerWorkingDirectory();
        myDebuggerEnvironment = new LinkedHashMap<>(mutableModuleExtension.myDebuggerEnvironment);
        myCompileSettings.copyFrom(mutableModuleExtension.myCompileSettings);
    }

    @Override
    protected boolean isModifiedImpl(VisualCModuleExtension originExtension) {
        return super.isModifiedImpl(originExtension)
            || !Objects.equals(myConfigurationType, originExtension.getConfigurationType())
            || !Objects.equals(myDebuggerCommand, originExtension.getDebuggerCommand())
            || !Objects.equals(myDebuggerArguments, originExtension.getDebuggerArguments())
            || !Objects.equals(myDebuggerWorkingDirectory, originExtension.getDebuggerWorkingDirectory())
            || !myDebuggerEnvironment.equals(originExtension.myDebuggerEnvironment)
            || !myCompileSettings.equals(originExtension.myCompileSettings);
    }

    @Override
    protected void getStateImpl(Element element) {
        super.getStateImpl(element);
        setAttribute(element, ATTR_CONFIGURATION_TYPE, myConfigurationType);
        setPathAttribute(element, ATTR_DEBUGGER_COMMAND, myDebuggerCommand);
        setAttribute(element, ATTR_DEBUGGER_ARGUMENTS, myDebuggerArguments);
        setPathAttribute(element, ATTR_DEBUGGER_WORKING_DIRECTORY, myDebuggerWorkingDirectory);
        for (Map.Entry<String, String> entry : myDebuggerEnvironment.entrySet()) {
            element.addContent(new Element(ELEMENT_ENV).setAttribute(ATTR_ENV_NAME, entry.getKey()).setAttribute(ATTR_ENV_VALUE, entry.getValue()));
        }
        myCompileSettings.write(element);
    }

    @RequiredReadAction
    @Override
    protected void loadStateImpl(Element element) {
        super.loadStateImpl(element);
        myConfigurationType = element.getAttributeValue(ATTR_CONFIGURATION_TYPE);
        myDebuggerCommand = getPathAttribute(element, ATTR_DEBUGGER_COMMAND);
        myDebuggerArguments = element.getAttributeValue(ATTR_DEBUGGER_ARGUMENTS);
        myDebuggerWorkingDirectory = getPathAttribute(element, ATTR_DEBUGGER_WORKING_DIRECTORY);
        myDebuggerEnvironment = new LinkedHashMap<>();
        for (Element env : element.getChildren(ELEMENT_ENV)) {
            String name = env.getAttributeValue(ATTR_ENV_NAME);
            if (name != null) {
                String value = env.getAttributeValue(ATTR_ENV_VALUE);
                myDebuggerEnvironment.put(name, value != null ? value : "");
            }
        }
        myCompileSettings.read(element);
    }
}
