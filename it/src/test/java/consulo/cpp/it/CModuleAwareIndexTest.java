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
import consulo.application.WriteAction;
import consulo.cpp.lang.psi.CPsiField;
import consulo.cpp.lang.CLanguage;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiFile;
import consulo.application.ReadAction;
import consulo.cpp.module.extension.CDefaultMutableModuleExtension;
import consulo.cpp.moduleAware.CDefineEnv;
import consulo.cpp.moduleAware.CPreprocessorOptions;
import consulo.cpp.preprocessor.CPreprocessorLanguage;
import consulo.cpp.preprocessor.psi.CPsiSharpInclude;
import consulo.it.AllowLogError;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.search.ReferencesSearch;
import consulo.undoRedo.CommandProcessor;
import consulo.it.HeadlessApplicationExtension;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the module-aware index against a real project: a module carries C/C++ defines, and the index has to
 * record them for the files of that module so parsing and highlighting can read back the options a file was
 * indexed under. The lighter tests in {@code cpp-api} cover the pieces; this one covers them wired together.
 */
@ExtendWith(HeadlessApplicationExtension.class)
@AllowLogError({
    "consulo.virtualFileSystem.internal.BaseVirtualFileManager",
    "consulo.application.impl.internal.BaseApplication",
    "consulo.ui.ex.impl.internal.action.ActionManagerImpl"
})
public class CModuleAwareIndexTest {
    private static final long TIMEOUT_SECONDS = 60;

    @Test
    public void moduleDefinesAreRecordedForItsFiles(Application application, ProjectManager projectManager) throws Exception {
        Path directory = Files.createTempDirectory("consulo-cpp-it-defines");
        Path source = directory.resolve("main.c");
        Files.writeString(source, "#ifdef FEATURE\nint enabled;\n#else\nint disabled;\n#endif\n");

        Project project = openProjectWithModule(application, projectManager, directory, Map.of("FEATURE", "1"));

        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source);
        assertNotNull(file, "the source file must be visible in the VFS");

        waitFor("the module defines must reach the index", () -> {
            CPreprocessorOptions options = CDefineEnv.optionsFor(project, file);
            return options.defines().containsKey("FEATURE");
        });

        CPreprocessorOptions options = CDefineEnv.optionsFor(project, file);
        assertEquals("1", options.defines().get("FEATURE"));
    }

    /**
     * A module with nothing configured must not force a private copy of the indexed data, otherwise every plain
     * C file in a project would stop sharing its index entries.
     */
    @Test
    public void moduleWithoutDefinesRecordsNothing(Application application, ProjectManager projectManager) throws Exception {
        Path directory = Files.createTempDirectory("consulo-cpp-it-plain");
        Path source = directory.resolve("plain.c");
        Files.writeString(source, "int value;\n");

        Project project = openProjectWithModule(application, projectManager, directory, Map.of());

        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source);
        assertNotNull(file);

        awaitSmart(project);
        assertTrue(CDefineEnv.optionsFor(project, file).defines().isEmpty());
    }

    /**
     * The point of the whole module-aware chain: the defines configured on the module decide which branch of a
     * conditional is actually compiled, so a file in a module that defines {@code FEATURE} parses the guarded
     * declaration and not the {@code #else} one.
     */
    @Test
    public void definesDecideWhichBranchIsParsed(Application application, ProjectManager projectManager) throws Exception {
        Path directory = Files.createTempDirectory("consulo-cpp-it-branch-on");
        Path source = directory.resolve("main.c");
        Files.writeString(source, "#ifdef FEATURE\nint enabled;\n#else\nint disabled;\n#endif\n");

        Project project = openProjectWithModule(application, projectManager, directory, Map.of("FEATURE", ""));
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source);
        assertNotNull(file);

        waitFor("the guarded declaration must be the one parsed", () -> declarationsOf(project, file).contains("enabled"));
        assertFalse(declarationsOf(project, file).contains("disabled"), "the #else declaration must not be parsed");
    }

    /**
     * The same file in a module without that define compiles the other branch instead.
     */
    @Test
    public void withoutTheDefineTheElseBranchIsParsed(Application application, ProjectManager projectManager) throws Exception {
        Path directory = Files.createTempDirectory("consulo-cpp-it-branch-off");
        Path source = directory.resolve("main.c");
        Files.writeString(source, "#ifdef FEATURE\nint enabled;\n#else\nint disabled;\n#endif\n");

        Project project = openProjectWithModule(application, projectManager, directory, Map.of());
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source);
        assertNotNull(file);

        waitFor("the #else declaration must be the one parsed", () -> declarationsOf(project, file).contains("disabled"));
        assertFalse(declarationsOf(project, file).contains("enabled"), "the guarded declaration must not be parsed");
    }

    /**
     * What a CMake import sets up: the module searches a directory outside its content - as the compiler searches
     * {@code /usr/include} - so {@code #include <...>} resolves there, and the defines of the header it finds decide
     * which branch is parsed.
     */
    @Test
    public void includePathOutsideTheProjectIsSearched(Application application, ProjectManager projectManager) throws Exception {
        Path includeDirectory = Files.createTempDirectory("consulo-cpp-it-system-include");
        Path header = includeDirectory.resolve("config.h");
        Files.writeString(header, "#define FEATURE 1\n");

        Path directory = Files.createTempDirectory("consulo-cpp-it-include");
        Path source = directory.resolve("main.c");
        Files.writeString(source, "#include <config.h>\n#ifdef FEATURE\nint enabled;\n#else\nint disabled;\n#endif\n");

        Project project = openProjectWithModule(application, projectManager, directory, Map.of(), List.of(includeDirectory.toString()));
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source);
        VirtualFile headerFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(header);
        assertNotNull(file);
        assertNotNull(headerFile);

        waitFor("the include must resolve through the module include path", () -> headerFile.equals(includeTargetOf(project, file)));
        waitFor("the define of the included header must decide the branch", () -> declarationsOf(project, file).contains("enabled"));
        assertFalse(declarationsOf(project, file).contains("disabled"), "the #else declaration must not be parsed");
    }

    /**
     * A forced include - {@code -include Definitions.h} of an Unreal module - is preprocessed above the first line of
     * every file of the module: its defines decide a branch of a file which never includes it.
     */
    @Test
    public void forcedIncludeDecidesTheBranch(Application application, ProjectManager projectManager) throws Exception {
        Path includeDirectory = Files.createTempDirectory("consulo-cpp-it-forced-header");
        Path header = includeDirectory.resolve("Definitions.h");
        Files.writeString(header, "#define FEATURE 1\n");

        Path directory = Files.createTempDirectory("consulo-cpp-it-forced");
        Path source = directory.resolve("main.c");
        Files.writeString(source, "#ifdef FEATURE\nint enabled;\n#else\nint disabled;\n#endif\n");

        Project project = openProjectWithModule(application, projectManager, directory, Map.of(), List.of(), List.of(header.toString()));
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source);
        assertNotNull(file);
        assertEquals(List.of(header.toString()), CDefineEnv.optionsFor(project, file).forcedIncludes());

        waitFor("the define of the forced include must decide the branch", () -> declarationsOf(project, file).contains("enabled"));
        assertFalse(declarationsOf(project, file).contains("disabled"), "the #else declaration must not be parsed");
    }

    /**
     * A forced include given by name is found as an {@code #include "name"} would be - here in an include directory.
     */
    @Test
    public void relativeForcedIncludeIsFoundInTheIncludeDirectories(Application application, ProjectManager projectManager) throws Exception {
        Path includeDirectory = Files.createTempDirectory("consulo-cpp-it-forced-search");
        Files.writeString(includeDirectory.resolve("Prefix.h"), "#define FEATURE 1\n");

        Path directory = Files.createTempDirectory("consulo-cpp-it-forced-relative");
        Path source = directory.resolve("main.c");
        Files.writeString(source, "#ifdef FEATURE\nint enabled;\n#else\nint disabled;\n#endif\n");

        Project project = openProjectWithModule(application, projectManager, directory, Map.of(), List.of(includeDirectory.toString()),
            List.of("Prefix.h"));
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source);
        assertNotNull(file);

        waitFor("the forced include must be found in the include directories", () -> declarationsOf(project, file).contains("enabled"));
        assertFalse(declarationsOf(project, file).contains("disabled"), "the #else declaration must not be parsed");
    }

    /**
     * Names declared in an included header resolve into it: a type through its namespace, a function, a member through
     * the type of a parameter, and a namespace reopened in the source.
     */
    @Test
    public void namesResolveIntoIncludedHeaders(Application application, ProjectManager projectManager) throws Exception {
        Path directory = Files.createTempDirectory("consulo-cpp-it-include-resolve");
        Files.writeString(directory.resolve("geo.h"), "namespace geo { struct Point { int x; }; int area(Point p); }\n");
        Path source = directory.resolve("main.cpp");
        Files.writeString(source, "#include \"geo.h\"\nnamespace geo { int perimeter(Point p) { return p.x; } }\n"
            + "int use(geo::Point p) { return geo::area(p) + p.x; }\n");

        Project project = openProjectWithModule(application, projectManager, directory, Map.of());
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source);
        assertNotNull(file);

        waitFor("geo::Point must resolve into the header", () -> "geo.h".equals(targetFileOf(project, file, "Point", 1)));
        assertEquals("geo.h", targetFileOf(project, file, "Point", 0), "Point in the namespace reopened in the file");
        assertEquals("geo.h", targetFileOf(project, file, "area", 0), "the function of the header");
        assertEquals("geo.h", targetFileOf(project, file, "x", 0), "the member through the type of a parameter");
        assertEquals("geo.h", targetFileOf(project, file, "x", 1), "the member through the qualified type of a parameter");
        assertEquals("main.cpp", targetFileOf(project, file, "geo", 0), "the namespace reopened in the file itself");
    }

    /**
     * A namespace declared only in an included header - {@code std::} of the standard headers, {@code metal::} of
     * Metal's: its members resolve qualified, and unqualified through a {@code using namespace} of the file.
     */
    @Test
    public void namespaceOfAHeaderResolves(Application application, ProjectManager projectManager) throws Exception {
        Path directory = Files.createTempDirectory("consulo-cpp-it-header-namespace");
        Files.writeString(directory.resolve("lib.h"), "namespace lib { enum class flags { none, all }; void barrier(flags f); }\n");
        Path source = directory.resolve("main.cpp");
        Files.writeString(source, "#include \"lib.h\"\nvoid qualified() { lib::barrier(lib::flags::none); }\n"
            + "using namespace lib;\nvoid unqualified() { barrier(flags::all); }\n");

        Project project = openProjectWithModule(application, projectManager, directory, Map.of());
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source);
        assertNotNull(file);

        waitFor("lib must resolve into the header", () -> "lib.h".equals(targetFileOf(project, file, "lib", 0)));
        assertEquals("lib.h", targetFileOf(project, file, "barrier", 0), "the qualified function");
        assertEquals("lib.h", targetFileOf(project, file, "flags", 0), "the qualified enum");
        assertEquals("lib.h", targetFileOf(project, file, "none", 0), "the qualified enumerator");
        assertEquals("lib.h", targetFileOf(project, file, "barrier", 1), "the function through the directive");
        assertEquals("lib.h", targetFileOf(project, file, "flags", 1), "the enum through the directive");
        assertEquals("lib.h", targetFileOf(project, file, "all", 0), "the enumerator through the directive");
    }

    /**
     * A namespace reopened in two headers - as {@code std} is in hundreds: the qualifier and the directive resolve to
     * the block of one of them, the members of the other still resolve.
     */
    @Test
    public void namespaceAcrossHeadersResolves(Application application, ProjectManager projectManager) throws Exception {
        Path directory = Files.createTempDirectory("consulo-cpp-it-namespace-headers");
        Files.writeString(directory.resolve("a.h"), "namespace lib { struct A {}; }\n");
        Files.writeString(directory.resolve("b.h"), "namespace lib { void barrier(); }\n");
        Path source = directory.resolve("main.cpp");
        Files.writeString(source, "#include \"a.h\"\n#include \"b.h\"\nvoid f() { lib::barrier(); }\n"
            + "using namespace lib;\nvoid g() { barrier(); A a; }\n");

        Project project = openProjectWithModule(application, projectManager, directory, Map.of());
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source);
        assertNotNull(file);

        waitFor("lib::barrier must resolve into b.h", () -> "b.h".equals(targetFileOf(project, file, "barrier", 0)));
        assertEquals("b.h", targetFileOf(project, file, "barrier", 1), "the function through the directive");
        assertEquals("a.h", targetFileOf(project, file, "A", 0), "the struct of the other header through the directive");
    }

    /**
     * A {@code using namespace} written in a header applies to the files including it, as it does in a compiler.
     */
    @Test
    public void usingDirectiveOfAHeaderApplies(Application application, ProjectManager projectManager) throws Exception {
        Path directory = Files.createTempDirectory("consulo-cpp-it-header-directive");
        Files.writeString(directory.resolve("geo.h"), "namespace geo { struct Point { int x; }; }\nusing namespace geo;\n");
        Path source = directory.resolve("main.cpp");
        Files.writeString(source, "#include \"geo.h\"\nint use(Point p) { return p.x; }\n");

        Project project = openProjectWithModule(application, projectManager, directory, Map.of());
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source);
        assertNotNull(file);

        waitFor("Point must resolve through the directive of the header", () -> "geo.h".equals(targetFileOf(project, file, "Point", 0)));
        assertEquals("geo.h", targetFileOf(project, file, "x", 0), "the member of the type brought in");
    }

    /**
     * Find usages of a function finds its calls, across a header and the file including it; renaming a local renames
     * its declaration and every use, and nothing else.
     */
    @Test
    public void findUsagesAndRename(Application application, ProjectManager projectManager) throws Exception {
        Path directory = Files.createTempDirectory("consulo-cpp-it-usages");
        Files.writeString(directory.resolve("util.h"), "int helper(int value);\n");
        Path source = directory.resolve("main.cpp");
        Files.writeString(source, "#include \"util.h\"\nint use() { int count = helper(1); return helper(count) + count; }\n"
            + "int other() { int count = 2; return count; }\n");

        Project project = openProjectWithModule(application, projectManager, directory, Map.of());
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source);
        VirtualFile header = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory.resolve("util.h"));
        assertNotNull(file);
        assertNotNull(header);

        waitFor("helper must resolve into the header", () -> "util.h".equals(targetFileOf(project, file, "helper", 0)));
        int usages = ReadAction.compute(() -> {
            PsiElement helper = targetOf(project, file, "helper", 0);
            return helper == null ? -1 : ReferencesSearch.search(helper).findAll().size();
        });
        assertEquals(2, usages, "the two calls of helper");

        PsiElement count = targetOf(project, file, "count", 0);
        assertNotNull(count);
        List<PsiReference> references = ReadAction.compute(() -> List.copyOf(ReferencesSearch.search(count).findAll()));
        assertEquals(2, references.size(), "the two uses of the first count, not those of the other function");
        // as a rename does: in a command, on the UI thread, which a document of a physical file is changed on
        application.getLastUIAccess().giveAndWait(() -> CommandProcessor.getInstance().executeCommand(project, () -> WriteAction.run(() -> {
            for (PsiReference reference : references) {
                reference.handleElementRename("total");
            }
            ((PsiNamedElement) count).setName("total");
        }), "rename", null));
        String renamed = ReadAction.compute(() -> PsiManager.getInstance(project).findFile(file).getText());
        assertEquals("#include \"util.h\"\nint use() { int total = helper(1); return helper(total) + total; }\n"
            + "int other() { int count = 2; return count; }\n", renamed);
    }

    private static @Nullable PsiElement targetOf(Project project, VirtualFile file, String name, int index) {
        return ReadAction.compute(() -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            PsiFile cRoot = psiFile == null ? null : psiFile.getViewProvider().getPsi(CLanguage.INSTANCE);
            if (cRoot == null) {
                return null;
            }
            int seen = 0;
            for (PsiElement element : PsiTreeUtil.findChildrenOfType(cRoot, PsiElement.class)) {
                PsiReference reference = element.getReference();
                if (reference != null && name.equals(reference.getCanonicalText()) && seen++ == index) {
                    return reference.resolve();
                }
            }
            return null;
        });
    }

    /**
     * A header the file includes after one pulling in hundreds - gtest.h, the standard library - is still seen, and the
     * names an argument of a macro call passes resolve into it: {@code calc::add} of {@code EXPECT_EQ(calc::add(2, 3), 5)}.
     */
    @Test
    public void macroArgumentsResolveBehindLargeIncludes(Application application, ProjectManager projectManager) throws Exception {
        Path directory = Files.createTempDirectory("consulo-cpp-it-large-includes");
        int chain = 300;
        for (int i = 0; i < chain; i++) {
            Files.writeString(directory.resolve("h" + i + ".h"), i + 1 < chain ? "#include \"h" + (i + 1) + ".h\"\n" : "\n");
        }
        Files.writeString(directory.resolve("big.h"), "#include \"h0.h\"\n#define EXPECT_EQ(a, b) check((a) == (b))\nbool check(bool value);\n");
        Files.writeString(directory.resolve("calc.hpp"), "namespace calc { int add(int a, int b); }\n");
        Path source = directory.resolve("main.cpp");
        Files.writeString(source, "#include \"big.h\"\n#include \"calc.hpp\"\nvoid test() { EXPECT_EQ(calc::add(2, 3), 5); }\n");

        Project project = openProjectWithModule(application, projectManager, directory, Map.of());
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source);
        assertNotNull(file);

        waitFor("add in the macro call must resolve into calc.hpp", () -> "calc.hpp".equals(ReadAction.compute(() -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            PsiFile cRoot = psiFile == null ? null : psiFile.getViewProvider().getPsi(CLanguage.INSTANCE);
            int offset = psiFile == null ? -1 : psiFile.getText().indexOf("add(2");
            PsiReference reference = cRoot == null || offset < 0 ? null : cRoot.findReferenceAt(offset);
            PsiElement target = reference == null ? null : reference.resolve();
            return target == null ? null : target.getContainingFile().getName();
        })));
    }

    /**
     * A forced include is seen as an included header: its declarations resolve in a file which never includes it.
     */
    @Test
    public void namesResolveIntoForcedIncludes(Application application, ProjectManager projectManager) throws Exception {
        Path includeDirectory = Files.createTempDirectory("consulo-cpp-it-forced-resolve-header");
        Path header = includeDirectory.resolve("Definitions.h");
        Files.writeString(header, "struct Forced { int value; };\n");

        Path directory = Files.createTempDirectory("consulo-cpp-it-forced-resolve");
        Path source = directory.resolve("main.cpp");
        Files.writeString(source, "int use(Forced forced) { return forced.value; }\n");

        Project project = openProjectWithModule(application, projectManager, directory, Map.of(), List.of(), List.of(header.toString()));
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source);
        assertNotNull(file);

        waitFor("the type must resolve into the forced include", () -> "Definitions.h".equals(targetFileOf(project, file, "Forced", 0)));
        assertEquals("Definitions.h", targetFileOf(project, file, "value", 0), "the member through the forced type");
    }

    /**
     * @return the name of the file the {@code index}-th reference named {@code name} in {@code file} resolves into
     */
    private static @Nullable String targetFileOf(Project project, VirtualFile file, String name, int index) {
        return ReadAction.compute(() -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            PsiFile cRoot = psiFile == null ? null : psiFile.getViewProvider().getPsi(CLanguage.INSTANCE);
            if (cRoot == null) {
                return null;
            }
            int seen = 0;
            for (PsiElement element : PsiTreeUtil.findChildrenOfType(cRoot, PsiElement.class)) {
                PsiReference reference = element.getReference();
                if (reference == null || !name.equals(reference.getCanonicalText())) {
                    continue;
                }
                if (seen++ == index) {
                    PsiElement target = reference.resolve();
                    return target == null ? null : target.getContainingFile().getName();
                }
            }
            return null;
        });
    }

    /**
     * @return the file the first {@code #include} of {@code file} resolves to
     */
    private static @Nullable VirtualFile includeTargetOf(Project project, VirtualFile file) {
        return ReadAction.compute(() -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            PsiFile preprocessorRoot = psiFile == null ? null : psiFile.getViewProvider().getPsi(CPreprocessorLanguage.INSTANCE);
            CPsiSharpInclude include = preprocessorRoot == null ? null : PsiTreeUtil.findChildOfType(preprocessorRoot, CPsiSharpInclude.class);
            PsiReference reference = include == null ? null : include.getReference();
            PsiElement target = reference == null ? null : reference.resolve();
            return target instanceof PsiFile targetFile ? targetFile.getVirtualFile() : null;
        });
    }

    /**
     * @return the text of every declaration the C parse actually produced, which is how the active branch is
     * observed - the inactive branch survives only as dropped text around it, never as a declaration
     */
    private static String declarationsOf(Project project, VirtualFile file) {
        return ReadAction.compute(() -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile == null) {
                return "";
            }
            PsiFile cRoot = (PsiFile)psiFile.getViewProvider().getPsi(CLanguage.INSTANCE);
            if (cRoot == null) {
                return "";
            }
            StringBuilder declarations = new StringBuilder();
            for (CPsiField field : PsiTreeUtil.findChildrenOfType(cRoot, CPsiField.class)) {
                declarations.append(field.getText()).append('\n');
            }
            return declarations.toString();
        });
    }

    private static Project openProjectWithModule(Application application,
                                                 ProjectManager projectManager,
                                                 Path directory,
                                                 Map<String, String> defines) throws Exception {
        return openProjectWithModule(application, projectManager, directory, defines, List.of());
    }

    private static Project openProjectWithModule(Application application,
                                                 ProjectManager projectManager,
                                                 Path directory,
                                                 Map<String, String> defines,
                                                 List<String> includePaths) throws Exception {
        return openProjectWithModule(application, projectManager, directory, defines, includePaths, List.of());
    }

    private static Project openProjectWithModule(Application application,
                                                 ProjectManager projectManager,
                                                 Path directory,
                                                 Map<String, String> defines,
                                                 List<String> includePaths,
                                                 List<String> forcedIncludes) throws Exception {
        Project project = projectManager
            .openProjectAsync(directory, application.getLastUIAccess(), new ProjectOpenContext())
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(project);

        VirtualFile directoryFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory);
        assertNotNull(directoryFile);

        ModuleManager moduleManager = ModuleManager.getInstance(project);
        WriteAction.run(() -> {
            ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();
            Module module = moduleModel.newModule("main", directory.toString());
            moduleModel.commit();

            ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
            rootModel.addContentEntry(directoryFile);

            CDefaultMutableModuleExtension extension = rootModel.getExtensionWithoutCheck(CDefaultMutableModuleExtension.class);
            assertNotNull(extension, "the C/C++ module extension must be registered");
            extension.setEnabled(true);
            extension.setDefines(defines);
            extension.setIncludePaths(includePaths);
            extension.setForcedIncludes(forcedIncludes);

            rootModel.commit();
        });

        awaitSmart(project);
        return project;
    }

    private static void awaitSmart(Project project) throws Exception {
        CountDownLatch smart = new CountDownLatch(1);
        DumbService.getInstance(project).runWhenSmart(smart::countDown);
        assertTrue(smart.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "project must reach smart mode");
    }

    private static void waitFor(String description, BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        assertTrue(condition.getAsBoolean(), "timed out: " + description);
    }
}
