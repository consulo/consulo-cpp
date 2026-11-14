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
import consulo.cpp.module.extension.CDefaultMutableModuleExtension;
import consulo.it.HeadlessApplicationExtension;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * How much of everyday C and C++ files resolves - the standard library, classes, templates, lambdas - in a module set up as
 * an import sets one up: the macros and include directories of g++ or gcc, the include directories as a library. Reports the
 * references which resolve to nothing, and the names no reference covers, to show where resolve falls short; there
 * should be none.
 *
 * @author VISTALL
 * @since 2026-11-14
 */
@ExtendWith(HeadlessApplicationExtension.class)
public class ResolveCoverageTest {
    private static final long TIMEOUT_SECONDS = 180;

    /**
     * Identifiers which are words of the language where they are, not names.
     */
    private static final java.util.Set<String> CONTEXTUAL_KEYWORDS = java.util.Set.of("override", "final");

    @Test
    public void modernCpp(Application application, ProjectManager projectManager) throws Exception {
        check(application, projectManager, "/usr/bin/g++", List.of("-x", "c++", "-std=c++20"), List.of("modern.cpp", "advanced.cpp", "library.cpp", "idioms.cpp"));
    }

    @Test
    public void plainC(Application application, ProjectManager projectManager) throws Exception {
        check(application, projectManager, "/usr/bin/gcc", List.of("-x", "c", "-std=gnu17"), List.of("plain.c"));
    }

    /**
     * Opens the samples in a module with the macros and include directories {@code compiler} has with
     * {@code options}, and asserts that each of their references resolves.
     */
    private static void check(Application application, ProjectManager projectManager, String compiler, List<String> options,
                              List<String> samples) throws Exception {
        assumeTrue(Files.isExecutable(Path.of(compiler)), compiler + " is not installed");

        Path directory = Files.createTempDirectory("consulo-cpp-it-coverage");
        for (String sample : samples) {
            try (InputStream stream = ResolveCoverageTest.class.getResourceAsStream("/coverage/" + sample)) {
                assertNotNull(stream, sample);
                Files.write(directory.resolve(sample), stream.readAllBytes());
            }
        }

        Map<String, String> defines = new LinkedHashMap<>();
        List<String> includePaths = new ArrayList<>();
        probe(compiler, options, defines, includePaths);
        Project project = openProject(application, projectManager, directory, defines, includePaths);
        List<String> failures = new ArrayList<>();
        for (String sample : samples) {
            failures.addAll(unresolvedIn(project, directory.resolve(sample)));
        }
        assertTrue(failures.isEmpty(), "references resolving to nothing: " + failures);
    }

    /**
     * The references of a file which resolve to nothing, as {@code file: name at lines}; reports the coverage.
     */
    private static List<String> unresolvedIn(Project project, Path source) {
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source);
        assertNotNull(file);

        Map<String, List<Integer>> unresolved = new TreeMap<>();
        int[] total = new int[1];
        ReadAction.run(() -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            assertNotNull(psiFile);
            String text = psiFile.getText();
            for (PsiFile root : psiFile.getViewProvider().getAllFiles()) {
                List<PsiElement> elements = new ArrayList<>(PsiTreeUtil.findChildrenOfType(root, PsiElement.class));
                // the file has references too: the defaults of its template heads
                elements.add(root);
                for (PsiElement element : elements) {
                    for (PsiReference reference : element.getReferences()) {
                        total[0]++;
                        if (!isResolved(reference)) {
                            int offset = element.getTextRange().getStartOffset() + reference.getRangeInElement().getStartOffset();
                            int line = (int) text.substring(0, Math.min(offset, text.length())).chars().filter(c -> c == '\n').count() + 1;
                            unresolved.computeIfAbsent(reference.getCanonicalText(), key -> new ArrayList<>()).add(line);
                        }
                    }
                }
            }
        });
        Map<String, List<Integer>> unreferenced = new TreeMap<>();
        ReadAction.run(() -> {
            PsiFile cRoot = PsiManager.getInstance(project).findFile(file).getViewProvider().getPsi(consulo.cpp.lang.CLanguage.INSTANCE);
            String text = cRoot.getText();
            for (PsiElement leaf = PsiTreeUtil.firstChild(cRoot); leaf != null; leaf = PsiTreeUtil.nextLeaf(leaf)) {
                if (leaf.getNode().getElementType() != consulo.cpp.lang.psi.CPsiTokens.IDENTIFIER || leaf.getTextLength() == 0) {
                    continue;
                }
                if (isNamed(leaf) || isReferenced(leaf)) {
                    continue;
                }
                int line = (int) text.substring(0, leaf.getTextRange().getStartOffset()).chars().filter(c -> c == '\n').count() + 1;
                unreferenced.computeIfAbsent(leaf.getText(), key -> new ArrayList<>()).add(line);
            }
        });

        int missing = unresolved.values().stream().mapToInt(List::size).sum();
        System.out.println("COVERAGE " + file.getName() + ": " + (total[0] - missing) + " of " + total[0] + " references resolve");
        assertTrue(total[0] > 0, file.getName() + " has references");
        List<String> failures = new ArrayList<>();
        unresolved.forEach((name, lines) -> failures.add(file.getName() + ": " + name + " at lines " + lines));
        unreferenced.forEach((name, lines) -> failures.add(file.getName() + ": " + name + " at lines " + lines + " has no reference"));
        failures.forEach(failure -> System.out.println("COVERAGE unresolved " + failure));
        return failures;
    }

    /**
     * Whether an identifier names the declaration around it.
     */
    private static boolean isNamed(PsiElement identifier) {
        if (CONTEXTUAL_KEYWORDS.contains(identifier.getText()) || consulo.cpp.lang.psi.resolve.CDeclarations.nameIdentifier(identifier) == identifier) {
            // override, final; a template parameter, its own name
            return true;
        }
        for (PsiElement parent = identifier.getParent(); parent != null && !(parent instanceof PsiFile); parent = parent.getParent()) {
            if (consulo.cpp.lang.psi.resolve.CDeclarations.nameIdentifier(parent) == identifier
                || consulo.cpp.lang.psi.resolve.CDeclarations.bindingNames(parent).contains(identifier)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a reference of an element around an identifier covers it.
     */
    private static boolean isReferenced(PsiElement identifier) {
        int offset = identifier.getTextRange().getStartOffset();
        // as a lookup of the reference at an offset does: the elements around it, and the file
        for (PsiElement parent = identifier.getParent(); parent != null; parent = parent instanceof PsiFile ? null : parent.getParent()) {
            for (PsiReference reference : parent.getReferences()) {
                if (reference.getRangeInElement().shiftRight(parent.getTextRange().getStartOffset()).containsOffset(offset)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isResolved(PsiReference reference) {
        if (reference instanceof PsiPolyVariantReference poly) {
            for (ResolveResult result : poly.multiResolve(false)) {
                if (result.getElement() != null) {
                    return true;
                }
            }
            return false;
        }
        return reference.resolve() != null;
    }

    /**
     * What the compiler predefines with the options and where it searches - what an import of a project built with it records.
     */
    private static void probe(String compiler, List<String> options, Map<String, String> defines, List<String> includePaths) throws Exception {
        Path input = Files.createTempFile("consulo-cpp-it-probe", ".txt");
        List<String> command = new ArrayList<>();
        command.add(compiler);
        command.addAll(options);
        command.addAll(List.of("-dM", "-E", "-v", input.toString()));
        Process process = new ProcessBuilder(command).start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor(30, TimeUnit.SECONDS);
        for (String line : stdout.split("\n")) {
            if (line.startsWith("#define ")) {
                String rest = line.substring("#define ".length());
                int space = rest.indexOf(' ');
                String name = space < 0 ? rest : rest.substring(0, space);
                if (name.indexOf('(') < 0) {
                    defines.put(name, space < 0 ? "" : rest.substring(space + 1).trim());
                }
            }
        }
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
    }

    private static Project openProject(Application application,
                                       ProjectManager projectManager,
                                       Path directory,
                                       Map<String, String> defines,
                                       List<String> includePaths) throws Exception {
        Project project = projectManager.openProjectAsync(directory, application.getLastUIAccess(), new ProjectOpenContext())
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(project);
        VirtualFile directoryFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory);
        assertNotNull(directoryFile);
        WriteAction.run(() -> {
            ModifiableModuleModel moduleModel = ModuleManager.getInstance(project).getModifiableModel();
            Module module = moduleModel.newModule("coverage", directory.toString());
            moduleModel.commit();
            ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
            rootModel.addContentEntry(directoryFile);
            CDefaultMutableModuleExtension extension = rootModel.getExtensionWithoutCheck(CDefaultMutableModuleExtension.class);
            assertNotNull(extension);
            extension.setEnabled(true);
            extension.setDefines(defines);
            extension.setIncludePaths(includePaths);

            // as an import attaches them: the headers get the module's options
            Library library = rootModel.getModuleLibraryTable().createLibrary("Header Search Paths");
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
