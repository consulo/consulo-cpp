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

import consulo.cpp.lang.version.CLanguageVersion;
import consulo.cpp.lang.version.CLanguageVersions;
import org.jdom.Element;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * @author VISTALL
 * @since 2026-10-10
 */
public final class CCompileSettings {
    private static final String ATTR_C_STANDARD = "c-standard";
    private static final String ATTR_CPP_STANDARD = "cpp-standard";
    private static final String ATTR_TARGET = "target";

    private Map<String, String> myDefines = new TreeMap<>();
    private List<String> myIncludePaths = new ArrayList<>();
    private List<String> myForcedIncludes = new ArrayList<>();
    // version ids rather than versions, so a standard contributed by a plugin which is not loaded right now survives a save
    private String myCStandardId = "";
    private String myCppStandardId = "";
    private String myTarget = "";

    public Map<String, String> getDefines() {
        return Map.copyOf(myDefines);
    }

    public void setDefines(Map<String, String> defines) {
        myDefines = new TreeMap<>(defines);
    }

    public List<String> getIncludePaths() {
        return List.copyOf(myIncludePaths);
    }

    public void setIncludePaths(List<String> includePaths) {
        myIncludePaths = new ArrayList<>(includePaths);
    }

    /**
     * @see CModuleExtension#getForcedIncludes()
     */
    public List<String> getForcedIncludes() {
        return List.copyOf(myForcedIncludes);
    }

    public void setForcedIncludes(List<String> forcedIncludes) {
        myForcedIncludes = new ArrayList<>(forcedIncludes);
    }

    /**
     * The C standard the module compiles with, {@code null} when not configured.
     */
    public @Nullable CLanguageVersion getCStandard() {
        return CLanguageVersions.findById(myCStandardId);
    }

    public void setCStandard(@Nullable CLanguageVersion standard) {
        myCStandardId = standard == null ? "" : standard.getId();
    }

    /**
     * The C++ standard the module compiles with, {@code null} when not configured.
     */
    public @Nullable CLanguageVersion getCppStandard() {
        return CLanguageVersions.findById(myCppStandardId);
    }

    public void setCppStandard(@Nullable CLanguageVersion standard) {
        myCppStandardId = standard == null ? "" : standard.getId();
    }

    /**
     * The target the module compiles for, as a target triple - {@code x86_64-pc-linux-gnu}, {@code arm-none-eabi} -
     * {@code null} when not configured: then the code runs where the IDE does.
     */
    public @Nullable String getTarget() {
        return myTarget.isEmpty() ? null : myTarget;
    }

    public void setTarget(@Nullable String target) {
        myTarget = target == null ? "" : target.trim();
    }

    public void copyFrom(CCompileSettings settings) {
        myDefines = new TreeMap<>(settings.myDefines);
        myIncludePaths = new ArrayList<>(settings.myIncludePaths);
        myForcedIncludes = new ArrayList<>(settings.myForcedIncludes);
        myCStandardId = settings.myCStandardId;
        myCppStandardId = settings.myCppStandardId;
        myTarget = settings.myTarget;
    }

    public void write(Element element) {
        if (!myCStandardId.isEmpty()) {
            element.setAttribute(ATTR_C_STANDARD, myCStandardId);
        }
        if (!myCppStandardId.isEmpty()) {
            element.setAttribute(ATTR_CPP_STANDARD, myCppStandardId);
        }
        if (!myTarget.isEmpty()) {
            element.setAttribute(ATTR_TARGET, myTarget);
        }
        CModuleExtension.writeDefines(element, myDefines);
        CModuleExtension.writeIncludePaths(element, myIncludePaths);
        CModuleExtension.writeForcedIncludes(element, myForcedIncludes);
    }

    public void read(Element element) {
        myCStandardId = element.getAttributeValue(ATTR_C_STANDARD, "");
        myCppStandardId = element.getAttributeValue(ATTR_CPP_STANDARD, "");
        myTarget = element.getAttributeValue(ATTR_TARGET, "");
        myDefines = new TreeMap<>(CModuleExtension.readDefines(element));
        myIncludePaths = new ArrayList<>(CModuleExtension.readIncludePaths(element));
        myForcedIncludes = new ArrayList<>(CModuleExtension.readForcedIncludes(element));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CCompileSettings that)) {
            return false;
        }
        return myDefines.equals(that.myDefines)
            && myIncludePaths.equals(that.myIncludePaths)
            && myForcedIncludes.equals(that.myForcedIncludes)
            && myCStandardId.equals(that.myCStandardId)
            && myCppStandardId.equals(that.myCppStandardId)
            && myTarget.equals(that.myTarget);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myDefines, myIncludePaths, myForcedIncludes, myCStandardId, myCppStandardId, myTarget);
    }
}
