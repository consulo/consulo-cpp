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
package consulo.cpp.asm;

import consulo.asm.gas.GasAarch64LanguageVersion;
import consulo.asm.gas.GasArmLanguageVersion;
import consulo.asm.gas.GasAttLanguageVersion;
import consulo.asm.gas.GasIntelLanguageVersion;
import consulo.asm.gas.GasMipsLanguageVersion;
import consulo.asm.gas.GasRiscVLanguageVersion;
import consulo.asm.language.AsmLanguage;
import consulo.asm.nasm.MasmLanguageVersion;
import consulo.cpp.lang.psi.CPsiAsmCode;
import consulo.cpp.module.extension.CModuleExtension;
import consulo.language.psi.PsiFile;
import consulo.language.version.LanguageVersion;
import consulo.language.version.LanguageVersionUtil;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.platform.Platform;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The assembler dialect of inline assembly. GCC and Clang pass {@code asm("...")} to GNU as for the target
 * architecture, AT&amp;T syntax on x86 unless the template switches with {@code .intel_syntax}; MSVC blocks are Intel
 * syntax, as MASM writes it.
 * <p>
 * The target is the one the module compiles for - a header of a library, the one of the first module reaching it -
 * else the machine the IDE runs on, which is what a native build compiles for.
 *
 * @author VISTALL
 * @since 2026-10-20
 */
final class CAsmDialects {
    private CAsmDialects() {
    }

    static LanguageVersion dialectOf(CPsiAsmCode code) {
        if (code.isBlock()) {
            return version(MasmLanguageVersion.class);
        }
        Class<? extends LanguageVersion> gas = gasDialect(architectureOf(code));
        if (gas == GasAttLanguageVersion.class && code.getText().contains(".intel_syntax")) {
            return version(GasIntelLanguageVersion.class);
        }
        return version(gas);
    }

    /**
     * @return the architecture part of the module's target triple - {@code x86_64}, {@code arm} - or the name of the
     * IDE's own architecture
     */
    private static String architectureOf(CPsiAsmCode code) {
        String target = targetOf(code);
        if (target != null) {
            int dash = target.indexOf('-');
            return dash < 0 ? target : target.substring(0, dash);
        }
        // the code is assembled for the machine - its system's architecture, not the JVM's
        return Platform.current().os().arch().name();
    }

    private static @Nullable String targetOf(CPsiAsmCode code) {
        PsiFile psiFile = code.getContainingFile();
        VirtualFile file = psiFile == null ? null : psiFile.getOriginalFile().getVirtualFile();
        if (file == null) {
            return null;
        }
        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(code.getProject());
        Module module = fileIndex.getModuleForFile(file);
        if (module != null) {
            return targetOf(module);
        }
        for (OrderEntry entry : fileIndex.getOrderEntriesForFile(file)) {
            String target = targetOf(entry.getOwnerModule());
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    private static @Nullable String targetOf(Module module) {
        CModuleExtension<?> extension = module.getExtension(CModuleExtension.class);
        return extension == null ? null : extension.getTarget();
    }

    private static Class<? extends LanguageVersion> gasDialect(String architecture) {
        String arch = architecture.toLowerCase(Locale.ROOT);
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return GasAarch64LanguageVersion.class;
        }
        if (arch.startsWith("arm") || arch.startsWith("thumb")) {
            return GasArmLanguageVersion.class;
        }
        if (arch.startsWith("riscv")) {
            return GasRiscVLanguageVersion.class;
        }
        if (arch.startsWith("mips")) {
            return GasMipsLanguageVersion.class;
        }
        // x86_64, i686, and what GNU as has no dialect for here
        return GasAttLanguageVersion.class;
    }

    private static LanguageVersion version(Class<? extends LanguageVersion> versionClass) {
        LanguageVersion version = AsmLanguage.INSTANCE.findVersionByClass(versionClass);
        return version != null ? version : LanguageVersionUtil.findDefaultVersion(AsmLanguage.INSTANCE);
    }
}
