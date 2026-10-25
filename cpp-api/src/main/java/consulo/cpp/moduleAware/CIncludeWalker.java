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
package consulo.cpp.moduleAware;

import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.util.VirtualFileUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collects the files a C/C++ file pulls in transitively, and reduces them to a fingerprint.
 * <p>
 * The fingerprint is what makes a header edit reach the files that include it: it is part of the option the
 * module-aware index records, so when a header changes the includer's option changes and it is re-indexed.
 * <p>
 * The walk reads text rather than PSI or stubs on purpose - it runs while the index is being built, where
 * loading PSI for every header would be both slow and re-entrant.
 */
public final class CIncludeWalker {
    private static final Pattern INCLUDE = Pattern.compile("^\\s*#\\s*include\\s*[\"<]([^\">]+)[\">]");

    /** A guard against pathological include graphs; the fingerprint stays stable, it just stops widening. */
    private static final int MAX_FILES = 256;

    private record DirectIncludes(long stamp, List<String> names) {
    }

    private static final Key<DirectIncludes> DIRECT_INCLUDES = Key.create("CIncludeWalker.directIncludes");

    private record ResolvedIncludes(long stamp, long structureStamp, List<VirtualFile> roots, List<VirtualFile> files) {
    }

    private static final Key<ResolvedIncludes> RESOLVED_INCLUDES = Key.create("CIncludeWalker.resolvedIncludes");

    private CIncludeWalker() {
    }

    /**
     * @return the files {@code start} includes transitively, not including {@code start} itself
     */
    public static Set<VirtualFile> walk(VirtualFile start, List<VirtualFile> includeRoots) {
        Set<VirtualFile> visited = new LinkedHashSet<>();
        collect(start, includeRoots, visited);
        visited.remove(start);
        return visited;
    }

    public static String fingerprintFor(VirtualFile start, List<VirtualFile> includeRoots) {
        return fingerprint(walk(start, includeRoots));
    }

    /**
     * Folds the identity and current state of every included file into one value, so any edit to any of them
     * produces a different fingerprint.
     */
    public static String fingerprint(Collection<VirtualFile> files) {
        if (files.isEmpty()) {
            return "";
        }

        List<String> parts = new ArrayList<>(files.size());
        for (VirtualFile file : files) {
            parts.add(file.getPath() + ':' + file.getTimeStamp() + ':' + file.getLength());
        }
        Collections.sort(parts);

        long hash = 0xcbf29ce484222325L;
        for (String part : parts) {
            for (int i = 0; i < part.length(); i++) {
                hash ^= part.charAt(i);
                hash *= 0x100000001b3L;
            }
            hash ^= '\n';
            hash *= 0x100000001b3L;
        }
        return Long.toHexString(hash);
    }

    private static void collect(VirtualFile file, List<VirtualFile> includeRoots, Set<VirtualFile> visited) {
        if (file == null || file.isDirectory() || visited.size() >= MAX_FILES || !visited.add(file)) {
            return;
        }

        for (VirtualFile included : resolvedIncludesOf(file, includeRoots)) {
            collect(included, includeRoots, visited);
        }
    }

    /**
     * The files a file includes directly, resolved once per modification of the file, of the file system's structure
     * and of the include directories: resolving a name probes every include directory, and boost has more than a
     * hundred of them.
     */
    private static List<VirtualFile> resolvedIncludesOf(VirtualFile file, List<VirtualFile> includeRoots) {
        long stamp = file.getModificationStamp();
        long structureStamp = VirtualFileManager.getInstance().getStructureModificationCount();
        ResolvedIncludes cached = file.getUserData(RESOLVED_INCLUDES);
        if (cached != null && cached.stamp() == stamp && cached.structureStamp() == structureStamp && cached.roots().equals(includeRoots)) {
            return cached.files();
        }

        List<VirtualFile> files = new ArrayList<>();
        for (String includeName : directIncludesOf(file)) {
            VirtualFile included = resolve(includeName, file, includeRoots);
            if (included != null) {
                files.add(included);
            }
        }
        List<VirtualFile> result = List.copyOf(files);
        file.putUserData(RESOLVED_INCLUDES, new ResolvedIncludes(stamp, structureStamp, List.copyOf(includeRoots), result));
        return result;
    }

    /**
     * The names a file includes directly, read once per modification of the file: every file including a header walks
     * through it, and a header of a project like boost is reached from thousands of files.
     */
    private static List<String> directIncludesOf(VirtualFile file) {
        long stamp = file.getModificationStamp();
        DirectIncludes cached = file.getUserData(DIRECT_INCLUDES);
        if (cached != null && cached.stamp() == stamp) {
            return cached.names();
        }

        String text;
        try {
            text = VirtualFileUtil.loadText(file);
        }
        catch (IOException | RuntimeException unreadable) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        for (String line : text.split("\n")) {
            Matcher matcher = INCLUDE.matcher(line);
            if (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        List<String> result = List.copyOf(names);
        file.putUserData(DIRECT_INCLUDES, new DirectIncludes(stamp, result));
        return result;
    }

    private static VirtualFile resolve(String includeName, VirtualFile from, List<VirtualFile> includeRoots) {
        VirtualFile parent = from.getParent();
        if (parent != null) {
            VirtualFile found = parent.findFileByRelativePath(includeName);
            if (found != null && !found.isDirectory()) {
                return found;
            }
        }
        for (VirtualFile root : includeRoots) {
            VirtualFile found = root.findFileByRelativePath(includeName);
            if (found != null && !found.isDirectory()) {
                return found;
            }
        }
        return null;
    }
}
