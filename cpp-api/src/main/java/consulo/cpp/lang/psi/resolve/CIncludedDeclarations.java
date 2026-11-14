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
package consulo.cpp.lang.psi.resolve;

import consulo.application.progress.ProgressManager;
import consulo.cpp.lang.CLanguage;
import consulo.cpp.moduleAware.CDefineEnv;
import consulo.cpp.moduleAware.CIncludeWalker;
import consulo.language.cacheBuilder.CacheManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.UsageSearchContext;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.module.content.ProjectFileIndex;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.UserDataHolderEx;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.virtualFileSystem.light.LightVirtualFileBase;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The declarations a file sees through its {@code #include}s - what a compiler sees of a translation unit beyond the
 * file itself: the top-level declarations of the headers it includes, transitively, the forced ones first.
 * <p>
 * Headers have no stubs of their declarations - building them would mean preprocessing every header of a system
 * include directory - so this is the one place a resolve loads the tree of another file. It loads few: the word index
 * tells which of the included headers contain the name at all - or, for a header outside the project and its libraries,
 * the words of its text - and only those are parsed; their trees stay cached until they change. While indexing,
 * nothing is looked up in headers.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public final class CIncludedDeclarations {
    private CIncludedDeclarations() {
    }

    /**
     * The headers {@code file} pulls in, in the order they are included.
     */
    public static List<VirtualFile> includedFiles(PsiFile file) {
        return LanguageCachedValueUtil.getProjectPsiDependentCache(file, CIncludedDeclarations::computeIncludedFiles);
    }

    /**
     * How many headers a file is followed into: a C++ file including the standard library reaches a thousand.
     */
    private static final int MAX_INCLUDED = 4096;

    /**
     * Breadth first: the forced includes and the file's own includes come before what they include in turn, so a
     * header the file names is never left out because one included before it pulls in the whole standard library.
     */
    private static List<VirtualFile> computeIncludedFiles(PsiFile file) {
        VirtualFile source = sourceFile(file);
        if (source == null) {
            return List.of();
        }
        List<VirtualFile> roots = CDefineEnv.includeRootsFor(file);
        Set<VirtualFile> included = new LinkedHashSet<>();
        ArrayDeque<VirtualFile> queue = new ArrayDeque<>();
        for (String forcedInclude : CDefineEnv.forcedIncludesFor(file)) {
            VirtualFile forced = CIncludeWalker.resolveForcedInclude(forcedInclude, source, roots);
            if (forced != null && included.add(forced)) {
                queue.add(forced);
            }
        }
        for (VirtualFile direct : CIncludeWalker.resolvedIncludesOf(source, roots)) {
            if (included.add(direct)) {
                queue.add(direct);
            }
        }
        while (!queue.isEmpty() && included.size() < MAX_INCLUDED) {
            ProgressManager.checkCanceled();
            for (VirtualFile next : CIncludeWalker.resolvedIncludesOf(queue.poll(), roots)) {
                if (included.size() >= MAX_INCLUDED) {
                    break;
                }
                if (included.add(next)) {
                    queue.add(next);
                }
            }
        }
        included.remove(source);
        return List.copyOf(included);
    }

    /**
     * The C trees of the included headers which may declare {@code name}, in include order.
     */
    public static List<PsiFile> headersMentioning(PsiFile file, String name) {
        List<VirtualFile> included = includedFiles(file);
        if (included.isEmpty()) {
            return List.of();
        }
        Project project = file.getProject();
        if (DumbService.isDumb(project)) {
            return List.of();
        }
        Set<VirtualFile> candidates = candidatesMentioning(project, included, name);
        if (candidates.isEmpty()) {
            return List.of();
        }
        PsiManager manager = PsiManager.getInstance(project);
        List<PsiFile> headers = new ArrayList<>();
        for (VirtualFile header : included) {
            ProgressManager.checkCanceled();
            if (!candidates.contains(header)) {
                continue;
            }
            PsiFile cRoot = cTreeOf(manager, header);
            if (cRoot != null) {
                headers.add(cRoot);
            }
        }
        return headers;
    }

    /**
     * The C trees of the included headers which are part of the project - those completion offers the declarations of.
     */
    public static List<PsiFile> projectHeaders(PsiFile file) {
        List<VirtualFile> included = includedFiles(file);
        if (included.isEmpty()) {
            return List.of();
        }
        Project project = file.getProject();
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
        PsiManager manager = PsiManager.getInstance(project);
        List<PsiFile> headers = new ArrayList<>();
        for (VirtualFile header : included) {
            ProgressManager.checkCanceled();
            if (!projectScope.contains(header)) {
                continue;
            }
            PsiFile cRoot = cTreeOf(manager, header);
            if (cRoot != null) {
                headers.add(cRoot);
            }
        }
        return headers;
    }

    /**
     * The C trees of the included headers with a {@code using namespace} directive: what it brings in is visible to the
     * file including them, as it is to the compiler.
     */
    public static List<PsiFile> headersWithDirectives(PsiFile file) {
        List<VirtualFile> included = includedFiles(file);
        if (included.isEmpty() || DumbService.isDumb(file.getProject())) {
            return List.of();
        }
        PsiManager manager = PsiManager.getInstance(file.getProject());
        List<PsiFile> headers = new ArrayList<>();
        for (VirtualFile header : included) {
            ProgressManager.checkCanceled();
            if (!hasDirectives(header)) {
                continue;
            }
            PsiFile cRoot = cTreeOf(manager, header);
            if (cRoot != null) {
                headers.add(cRoot);
            }
        }
        return headers;
    }

    private static final Key<Map<VirtualFile, PsiFile>> LOADED_HEADERS = Key.create("CIncludedDeclarations.loadedHeaders");

    /**
     * The C tree of a header, kept for the project: the platform holds the files outside the project's content only
     * weakly, so without it the trees of a board's core - and the member maps cached on them - went with the next
     * collection, and every navigation parsed and preprocessed the headers over again. Kept until the header changes,
     * which makes its file invalid.
     */
    private static @Nullable PsiFile cTreeOf(PsiManager manager, VirtualFile header) {
        Project project = manager.getProject();
        Map<VirtualFile, PsiFile> loaded = project.getUserData(LOADED_HEADERS);
        if (loaded == null) {
            loaded = ((UserDataHolderEx) project).putUserDataIfAbsent(LOADED_HEADERS, new ConcurrentHashMap<>());
        }
        PsiFile cRoot = loaded.get(header);
        if (cRoot != null && cRoot.isValid()) {
            return cRoot;
        }
        PsiFile psiFile = manager.findFile(header);
        cRoot = psiFile == null ? null : psiFile.getViewProvider().getPsi(CLanguage.INSTANCE);
        if (cRoot != null) {
            loaded.put(header, cRoot);
        }
        else {
            loaded.remove(header);
        }
        return cRoot;
    }

    /**
     * The included files containing {@code name} as a word: the word index answers for those of the project's content
     * and of the sources of its libraries; any other header - one of the binary roots of a library, as a board's core
     * is attached, or one found through an include directory attached to nothing - is read and its words kept until it
     * changes.
     */
    private static Set<VirtualFile> candidatesMentioning(Project project, List<VirtualFile> included, String name) {
        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
        List<VirtualFile> inIndex = new ArrayList<>();
        Set<VirtualFile> candidates = new HashSet<>();
        for (VirtualFile file : included) {
            if (fileIndex.isInContent(file) || fileIndex.isInLibrarySource(file)) {
                inIndex.add(file);
            }
            else if (wordsOf(file).contains(name)) {
                candidates.add(file);
            }
        }
        if (!inIndex.isEmpty()) {
            candidates.addAll(Arrays.asList(project.getInstance(CacheManager.class)
                .getVirtualFilesWithWord(name, UsageSearchContext.ANY, GlobalSearchScope.filesScope(project, inIndex), true)));
        }
        return candidates;
    }

    private record Words(long stamp, Set<String> words) {
    }

    private record Directives(long stamp, boolean present) {
    }

    private static final Key<Words> WORDS = Key.create("CIncludedDeclarations.words");

    private static final Key<Directives> DIRECTIVES = Key.create("CIncludedDeclarations.directives");

    private static final Pattern WORD = Pattern.compile("[A-Za-z_][A-Za-z_0-9]*");

    private static final Pattern DIRECTIVE = Pattern.compile("\\busing\\s+namespace\\b");

    private static final Pattern INLINE_NAMESPACE = Pattern.compile("\\binline\\s+namespace\\s+([A-Za-z_][A-Za-z_0-9]*)(?=\\s*(?:\\{|__attribute__|\\[\\[))");

    private record InlineNamespaces(long stamp, Set<String> names) {
    }

    private static final Key<InlineNamespaces> INLINE_NAMESPACES = Key.create("CIncludedDeclarations.inlineNamespaces");

    /**
     * The words of a header the word index does not cover, read once per change of it.
     */
    private static Set<String> wordsOf(VirtualFile file) {
        long stamp = file.getModificationStamp();
        Words cached = file.getUserData(WORDS);
        if (cached != null && cached.stamp() == stamp) {
            return cached.words();
        }
        Set<String> words = new HashSet<>();
        CharSequence text = textOf(file);
        if (text != null) {
            Matcher matcher = WORD.matcher(text);
            while (matcher.find()) {
                words.add(matcher.group());
            }
        }
        Set<String> result = Set.copyOf(words);
        file.putUserData(WORDS, new Words(stamp, result));
        return result;
    }

    /**
     * The names of the namespaces declared {@code inline} in {@code file} or in a header it includes. A namespace is
     * inline if its first declaration says so - libstdc++ says {@code inline namespace __cxx11} once, in
     * {@code c++config.h}, and reopens it as plain {@code namespace __cxx11} everywhere else. Known by name: the
     * declaration is read from the text, not resolved.
     */
    public static Set<String> inlineNamespaceNames(PsiFile file) {
        return LanguageCachedValueUtil.getProjectPsiDependentCache(file, CIncludedDeclarations::computeInlineNamespaceNames);
    }

    private static Set<String> computeInlineNamespaceNames(PsiFile file) {
        Set<String> names = new HashSet<>();
        VirtualFile source = sourceFile(file);
        if (source != null) {
            names.addAll(inlineNamespacesOf(source));
        }
        for (VirtualFile header : includedFiles(file)) {
            ProgressManager.checkCanceled();
            names.addAll(inlineNamespacesOf(header));
        }
        return names.isEmpty() ? Set.of() : Set.copyOf(names);
    }

    /**
     * The names {@code inline namespace} declares in the text of a file, read once per change of it.
     */
    private static Set<String> inlineNamespacesOf(VirtualFile file) {
        long stamp = file.getModificationStamp();
        InlineNamespaces cached = file.getUserData(INLINE_NAMESPACES);
        if (cached != null && cached.stamp() == stamp) {
            return cached.names();
        }
        Set<String> names = new HashSet<>();
        CharSequence text = textOf(file);
        if (text != null) {
            Matcher matcher = INLINE_NAMESPACE.matcher(text);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        Set<String> result = names.isEmpty() ? Set.of() : Set.copyOf(names);
        file.putUserData(INLINE_NAMESPACES, new InlineNamespaces(stamp, result));
        return result;
    }

    /**
     * Whether the text of a header has a {@code using namespace} directive, read once per change of it.
     */
    private static boolean hasDirectives(VirtualFile file) {
        long stamp = file.getModificationStamp();
        Directives cached = file.getUserData(DIRECTIVES);
        if (cached != null && cached.stamp() == stamp) {
            return cached.present();
        }
        CharSequence text = textOf(file);
        boolean present = text != null && DIRECTIVE.matcher(text).find();
        file.putUserData(DIRECTIVES, new Directives(stamp, present));
        return present;
    }

    private static @Nullable CharSequence textOf(VirtualFile file) {
        try {
            return VirtualFileUtil.loadText(file);
        }
        catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * The file the includes are resolved from: the file itself, or the one a light copy - completion's, a test's - was
     * made of.
     */
    private static @Nullable VirtualFile sourceFile(PsiFile file) {
        VirtualFile virtualFile = file.getOriginalFile().getViewProvider().getVirtualFile();
        if (virtualFile instanceof LightVirtualFileBase light && light.getOriginalFile() != null) {
            return light.getOriginalFile();
        }
        return virtualFile.isInLocalFileSystem() ? virtualFile : null;
    }
}
