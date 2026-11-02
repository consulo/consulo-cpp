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
package consulo.cpp.it;

import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.application.WriteAction;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.library.Library;
import consulo.cpp.lang.CLanguage;
import consulo.cpp.lang.editor.highlight.CCallChecker;
import consulo.cpp.module.extension.CDefaultMutableModuleExtension;
import consulo.it.AllowLogError;
import consulo.it.HeadlessApplicationExtension;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.module.ModifiableModuleModel;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.project.ProjectOpenContext;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A sketch for an Arduino Uno, set up as the Arduino import sets up its module: the core's and the variant's
 * directories and the compiler's own, what the compiler predefines for the chip and the {@code -D} of arduino-cli,
 * {@code Arduino.h} forced above the first line. Needs the Arduino AVR core installed - it is skipped without.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
@ExtendWith(HeadlessApplicationExtension.class)
@AllowLogError({".*"})
public class ArduinoSketchTest {
    private static final Path PACKAGES = Path.of(System.getProperty("user.home"), ".arduino15", "packages", "arduino");
    private static final Path CORE = PACKAGES.resolve("hardware/avr/1.8.7");
    private static final Path COMPILER = PACKAGES.resolve("tools/avr-gcc/7.3.0-atmel3.6.1-arduino7/bin/avr-g++");
    private static final long TIMEOUT_SECONDS = 120;

    private static final String SKETCH = """
        // Blink, and what a sketch uses besides
        struct Blinker {
            int pin;
            unsigned long last;
        };

        Blinker blinker = {LED_BUILTIN, 0};

        void setup() {
            pinMode(LED_BUILTIN, OUTPUT);
            Serial.begin(9600);
        }

        void loop() {
            if (millis() - blinker.last > 1000) {
                blinker.last = millis();
                blinkTimes(3);
            }
            String message = String("pin ") + blinker.pin;
            Serial.println(message);
            Serial.println(analogRead(A0));
            delay(10);
        }

        void blinkTimes(int times) {
            for (int i = 0; i < times; i++) {
                digitalWrite(LED_BUILTIN, HIGH);
                delay(100);
                digitalWrite(LED_BUILTIN, LOW);
            }
        }
        """;

    @Test
    public void sketchResolvesIntoTheCore(Application application, ProjectManager projectManager) throws Exception {
        assumeTrue(Files.isRegularFile(CORE.resolve("cores/arduino/Arduino.h")) && Files.isExecutable(COMPILER),
            "the Arduino AVR core 1.8.7 is not installed");

        Path directory = Files.createTempDirectory("consulo-cpp-it-arduino");
        Path source = directory.resolve("Blink.cpp");
        Files.writeString(source, SKETCH);

        // as arduino-cli compiles for an Uno: what the compiler predefines for the chip, then its -D
        Probe probe = probe();
        Map<String, String> defines = new LinkedHashMap<>(probe.defines());
        defines.putAll(Map.of("F_CPU", "16000000L", "ARDUINO", "10607", "ARDUINO_AVR_UNO", "1", "ARDUINO_ARCH_AVR", "1"));
        List<String> includePaths = new ArrayList<>();
        includePaths.add(CORE.resolve("cores/arduino").toString());
        includePaths.add(CORE.resolve("variants/standard").toString());
        includePaths.addAll(probe.includePaths());

        Project project = openProject(application, projectManager, directory, defines, includePaths, List.of("Arduino.h"));
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source);
        assertNotNull(file);

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("LED_BUILTIN", "pins_arduino.h");
        expected.put("pinMode", "Arduino.h");
        expected.put("OUTPUT", "Arduino.h");
        expected.put("Serial", "HardwareSerial.h");
        expected.put("begin", "HardwareSerial.h");
        expected.put("millis", "Arduino.h");
        expected.put("blinkTimes", "Blink.cpp");
        expected.put("String", "WString.h");
        expected.put("println", "Print.h");
        expected.put("analogRead", "Arduino.h");
        expected.put("A0", "pins_arduino.h");
        expected.put("delay", "Arduino.h");
        expected.put("digitalWrite", "Arduino.h");
        expected.put("HIGH", "Arduino.h");
        expected.put("last", "Blink.cpp");

        Map<String, @Nullable String> actual = new LinkedHashMap<>();
        for (String name : expected.keySet()) {
            actual.put(name, targetFileOf(project, file, name));
        }
        List<String> errors = callErrors(project, file);
        System.out.println("ARDUINO resolved: " + actual);
        System.out.println("ARDUINO call errors: " + errors);

        assertEquals(expected, actual, "where each name of the sketch resolves");
        assertTrue(errors.isEmpty(), "no call of the sketch is an error: " + errors);
        // of the twelve println of Print, the one taking a String - not those taking pointers it may convert to
        assertEquals("size_t println(const String &s);", declarationAt(project, file, "println(message)"));
    }

    private static @Nullable String declarationAt(Project project, VirtualFile file, String call) {
        return ReadAction.compute(() -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            PsiReference reference = psiFile == null ? null : psiFile.findReferenceAt(psiFile.getText().indexOf(call));
            PsiElement target = reference == null ? null : reference.resolve();
            return target == null ? null : target.getText();
        });
    }

    private record Probe(Map<String, String> defines, List<String> includePaths) {
    }

    /**
     * What the Arduino import asks the compiler: its macros for the chip ({@code -dM -E}) and its own directories ({@code -v}).
     */
    private static Probe probe() throws IOException, InterruptedException {
        Path input = Files.createTempFile("consulo-cpp-it-probe", ".cpp");
        Process process = new ProcessBuilder(COMPILER.toString(), "-mmcu=atmega328p", "-std=gnu++11", "-x", "c++", "-dM", "-E", "-v",
            input.toString()).start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor(30, TimeUnit.SECONDS);

        Map<String, String> defines = new LinkedHashMap<>();
        for (String line : stdout.split("\n")) {
            if (line.startsWith("#define ")) {
                String rest = line.substring(8);
                int space = rest.indexOf(' ');
                String name = space < 0 ? rest : rest.substring(0, space);
                if (name.indexOf('(') < 0) {
                    defines.put(name, space < 0 ? "" : rest.substring(space + 1).trim());
                }
            }
        }
        List<String> includePaths = new ArrayList<>();
        boolean inList = false;
        for (String line : stderr.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include <...> search starts here:")) {
                inList = true;
            }
            else if (trimmed.startsWith("End of search list.")) {
                break;
            }
            else if (inList && !trimmed.isEmpty()) {
                includePaths.add(Path.of(trimmed).normalize().toString());
            }
        }
        return new Probe(defines, includePaths);
    }

    /**
     * The file the first reference named {@code name} resolves into.
     */
    private static @Nullable String targetFileOf(Project project, VirtualFile file, String name) {
        return ReadAction.compute(() -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile == null) {
                return null;
            }
            String text = psiFile.getText();
            // the first use, not a declaration: past the struct for its fields
            int offset = name.equals("last") ? text.indexOf("blinker.last") + "blinker.".length() : firstUse(text, name);
            PsiReference reference = offset < 0 ? null : psiFile.findReferenceAt(offset);
            PsiElement target = reference == null ? null : reference.resolve();
            if (target == null) {
                return null;
            }
            // where navigation puts the caret: on the name the declaration has in its file
            PsiElement navigation = target.getNavigationElement();
            String targetText = navigation.getContainingFile().getText();
            int at = navigation.getTextOffset();
            if (!targetText.startsWith(name, at)) {
                return navigation.getContainingFile().getName() + " at offset " + at + ", not at " + name;
            }
            return navigation.getContainingFile().getName();
        });
    }

    private static int firstUse(String text, String name) {
        int offset = -1;
        do {
            offset = text.indexOf(name, offset + 1);
        }
        while (offset >= 0 && (offset > 0 && Character.isJavaIdentifierPart(text.charAt(offset - 1))
            || offset + name.length() < text.length() && Character.isJavaIdentifierPart(text.charAt(offset + name.length()))
            || text.startsWith("void " + name, offset - 5)));
        return offset;
    }

    private static List<String> callErrors(Project project, VirtualFile file) {
        return ReadAction.compute(() -> {
            List<String> errors = new ArrayList<>();
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            PsiFile cRoot = psiFile == null ? null : psiFile.getViewProvider().getPsi(CLanguage.INSTANCE);
            if (cRoot != null) {
                collect(cRoot, errors);
            }
            return errors;
        });
    }

    private static void collect(PsiElement element, List<String> errors) {
        CCallChecker.check(element, (range, message) -> errors.add(message.get() + " at " + range));
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            collect(child, errors);
        }
    }

    private static Project openProject(Application application,
                                       ProjectManager projectManager,
                                       Path directory,
                                       Map<String, String> defines,
                                       List<String> includePaths,
                                       List<String> forcedIncludes) throws Exception {
        Project project = projectManager.openProjectAsync(directory, application.getLastUIAccess(), new ProjectOpenContext())
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(project);
        VirtualFile directoryFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory);
        assertNotNull(directoryFile);
        WriteAction.run(() -> {
            ModifiableModuleModel moduleModel = ModuleManager.getInstance(project).getModifiableModel();
            Module module = moduleModel.newModule("sketch", directory.toString());
            moduleModel.commit();
            ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
            rootModel.addContentEntry(directoryFile);
            CDefaultMutableModuleExtension extension = rootModel.getExtensionWithoutCheck(CDefaultMutableModuleExtension.class);
            assertNotNull(extension);
            extension.setEnabled(true);
            extension.setDefines(defines);
            extension.setIncludePaths(includePaths);
            extension.setForcedIncludes(forcedIncludes);

            // the "Arduino SDK" library of the import: its headers are parsed with the module's options too, so the
            // HardwareSerial.h which needs the registers avr/io.h defines sees them through the forced Arduino.h
            Library library = rootModel.getModuleLibraryTable().createLibrary("Arduino SDK");
            Library.ModifiableModel libraryModel = library.getModifiableModel();
            for (String path : includePaths) {
                VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
                if (root != null) {
                    libraryModel.addRoot(root, BinariesOrderRootType.ID);
                }
            }
            libraryModel.commit();
            rootModel.commit();
        });
        CountDownLatch smart = new CountDownLatch(1);
        DumbService.getInstance(project).runWhenSmart(smart::countDown);
        assertTrue(smart.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "project must reach smart mode");
        return project;
    }
}
