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
import consulo.cpp.lang.psi.*;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.cpp.lang.CLanguage;
import consulo.language.psi.resolve.ResolveState;
import consulo.util.dataholder.Key;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The scoping rules of C and C++, one per construct, for the {@code processDeclarations} of the elements introducing a
 * scope. A walk up from a name meets them in the order C++ looks names up: the earlier statements of the enclosing
 * blocks, the variables of an enclosing {@code for} or {@code if}, the parameters of the function, the members of its
 * class - for {@code void Pawn::Tick()} too - and of the enclosing namespaces, then the file.
 * <p>
 * Every scope offers one level at a time and stops the walk once a level declares the name, see
 * {@link CResolveProcessor}. Members are looked up by name in maps cached on their owner; a namespace answers for every
 * block of it in the file.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public final class CScopes {
    private static final int MAX_BASE_DEPTH = 16;

    private CScopes() {
    }

    /**
     * A block: the declarations of the statements before the one the walk came from, nearest first - declare before
     * use, and the latest declaration shadows. From outside it offers nothing: its locals are its own.
     */
    public static boolean processBlock(PsiElement block, PsiScopeProcessor processor, ResolveState state, @Nullable PsiElement lastParent) {
        if (lastParent == null || lastParent.getParent() != block) {
            return true;
        }
        if (previousSignificant(lastParent) instanceof CPsiParameterList catchParameters && !offerParameters(catchParameters, processor, state)) {
            // catch (const Error& error) { ... }: the walk came from the handler
            return false;
        }
        List<PsiElement> introduced = new ArrayList<>();
        for (PsiElement child = lastParent.getPrevSibling(); child != null; child = child.getPrevSibling()) {
            ProgressManager.checkCanceled();
            if (CDeclarations.usingOf(child) == CDeclarations.Using.DIRECTIVE) {
                if (!offerDirectives(List.of(child), processor, state) || !isLevelOpen(processor)) {
                    return false;
                }
                continue;
            }
            introduced.clear();
            CDeclarations.collectIntroduced(child, introduced);
            if (!introduced.isEmpty() && !offerLevel(introduced, processor, state)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A lambda, {@code [&](int arg) { ... }}: its parameters, to its body.
     */
    public static boolean processLambda(PsiElement lambda, PsiScopeProcessor processor, ResolveState state, @Nullable PsiElement lastParent) {
        if (!(lastParent instanceof CPsiCodeBlock) || lastParent.getParent() != lambda) {
            return true;
        }
        CPsiParameterList parameterList = CDeclarations.childOfType(lambda, CPsiParameterList.class);
        return parameterList == null || offerParameters(parameterList, processor, state);
    }

    /**
     * A declaration without a scope of its own - a prototype, a field, an alias: the parameters of its template, to the
     * declaration.
     */
    public static boolean processDeclaration(PsiElement declaration, PsiScopeProcessor processor, ResolveState state, @Nullable PsiElement lastParent) {
        if (declaration instanceof CPsiDeclarationMethod) {
            return processFunction(declaration, processor, state, lastParent);
        }
        return lastParent == null || processTemplateParameters(declaration, processor, state);
    }

    /**
     * The parameters of the template heads before a declaration: to all of it - its return type and its qualified name
     * too.
     */
    private static boolean processTemplateParameters(PsiElement declaration, PsiScopeProcessor processor, ResolveState state) {
        List<CTemplates.Parameter> parameters = CTemplates.parametersOf(declaration);
        if (parameters.isEmpty()) {
            return true;
        }
        List<PsiElement> names = new ArrayList<>(parameters.size());
        for (CTemplates.Parameter parameter : parameters) {
            names.add(parameter.name());
        }
        return offerLevel(names, processor, state);
    }

    /**
     * {@code for}, {@code if}, {@code while}, {@code switch}: the variables their head declares, to the parts after
     * them. The variable of a range-based {@code for} is not visible in the range it iterates.
     */
    public static boolean processStatementVariables(PsiElement statement,
                                                    PsiScopeProcessor processor,
                                                    ResolveState state,
                                                    @Nullable PsiElement lastParent) {
        if (lastParent == null || lastParent.getParent() != statement) {
            return true;
        }
        List<PsiElement> variables = new ArrayList<>();
        boolean headClosed = false;
        for (PsiElement child = lastParent.getPrevSibling(); child != null; child = child.getPrevSibling()) {
            IElementType type = child.getNode().getElementType();
            if (type == CPsiTokens.RPARENTH) {
                headClosed = true;
            }
            else if (type == CPsiTokens.COLON && !headClosed) {
                // for (T x : range) - the walk came from the range
                return true;
            }
            else if (child instanceof CPsiLocalVariable) {
                variables.add(child);
            }
        }
        return variables.isEmpty() || offerLevel(variables, processor, state);
    }

    /**
     * A function: its parameters, to its body - and to whatever follows them, a constructor's member initializers or a
     * trailing return type - then the members of the class its qualified name names, {@code void Pawn::Tick()}. Its
     * return type and its name see neither.
     */
    public static boolean processFunction(PsiElement function, PsiScopeProcessor processor, ResolveState state, @Nullable PsiElement lastParent) {
        if (lastParent == null) {
            return true;
        }
        if (lastParent instanceof CPsiTypeRef || lastParent instanceof CPsiDoubleColonTypeRef) {
            return processTemplateParameters(function, processor, state);
        }
        CPsiParameterList parameterList = CDeclarations.childOfType(function, CPsiParameterList.class);
        if (parameterList != null && lastParent != parameterList && !offerParameters(parameterList, processor, state)) {
            return false;
        }
        if (!processTemplateParameters(function, processor, state)) {
            return false;
        }
        CPsiDoubleColonTypeRef qualifier = CDeclarations.childOfType(function, CPsiDoubleColonTypeRef.class);
        PsiElement owner = qualifier == null ? null : CNameResolver.resolveQualifiedOwner(qualifier);
        if (owner == null) {
            return true;
        }
        // void ns::Outer::Inner::f() at file scope sees Inner, Outer and ns, as if it were defined in Inner
        for (PsiElement scope = owner; scope != null && !(scope instanceof PsiFile); scope = scope.getParent()) {
            if ((scope == owner || scope instanceof CPsiNamespaceDeclaration || CDeclarations.isAggregate(scope))
                && !processMembers(scope, processor, state)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A variable, to its own initializer: in C++ a name is declared once its declarator ends, so the {@code x} of
     * {@code int x = x;} is the variable itself - which the highlighting may report.
     */
    public static boolean processOwnInitializer(PsiElement variable, PsiScopeProcessor processor, ResolveState state, @Nullable PsiElement lastParent) {
        if (lastParent == null || lastParent.getParent() != variable) {
            return true;
        }
        PsiElement name = CDeclarations.nameIdentifier(variable);
        if (name == null || name.getTextRange().getStartOffset() >= lastParent.getTextRange().getStartOffset()) {
            return true;
        }
        return offerLevel(List.of(variable), processor, state);
    }

    /**
     * A class, struct, union or namespace, asked from inside - an inline member function, a member initializer: all of
     * its members, wherever declared. The base list of a class does not see the members of the class.
     */
    public static boolean processOwnerFromInside(PsiElement owner, PsiScopeProcessor processor, ResolveState state, @Nullable PsiElement lastParent) {
        if (lastParent == null) {
            return true;
        }
        if (lastParent instanceof CPsiSuperClassList) {
            return processTemplateParameters(owner, processor, state);
        }
        if (owner instanceof CPsiNamespaceDeclaration) {
            // the members of the namespace and those its using-directives bring in are one level
            if (!offerAll(namespaceCandidates(owner, processor, state), processor, state)
                || !offerDirectives(directivesAmong(owner), processor, state)) {
                return false;
            }
            return isLevelOpen(processor);
        }
        // the parameters of a class template hide the names of its bases, _T2 of pair<_T1, _T2> : __pair_base<_T1, _T2>
        return processMembers(owner, processor, state, new HashSet<>(), 0, owner);
    }

    /**
     * The file: its top-level declarations, as one level, wherever they are - a name used above its declaration still
     * means it, the highlighting may say it should not.
     */
    public static boolean processFile(PsiFile file, PsiScopeProcessor processor, ResolveState state) {
        if (!offerAll(memberCandidates(ownMemberMap(file), processor), processor, state)
            || !offerDirectives(topLevelDirectives(file), processor, state)) {
            return false;
        }
        if (!isLevelOpen(processor)) {
            return false;
        }
        // what the file does not declare, the headers it includes may - or bring in with a using-directive
        String name = processor.getHint(CResolveProcessor.NAME_HINT);
        if (name != null) {
            for (PsiFile header : CIncludedDeclarations.headersMentioning(file, name)) {
                if (!offerAll(memberCandidates(ownMemberMap(header), processor), processor, state)) {
                    return false;
                }
            }
            if (!isLevelOpen(processor)) {
                return false;
            }
            for (PsiFile header : CIncludedDeclarations.headersWithDirectives(file)) {
                if (!offerDirectives(topLevelDirectives(header), processor, state)) {
                    return false;
                }
            }
        }
        return isLevelOpen(processor);
    }

    /**
     * The members of the namespaces {@code using namespace} directives name - unless the walk looks for the namespace of
     * a directive itself.
     */
    private static boolean offerDirectives(List<PsiElement> directives, PsiScopeProcessor processor, ResolveState state) {
        if (directives.isEmpty() || Boolean.TRUE.equals(state.get(CNameResolver.WITHOUT_USING_DIRECTIVES))) {
            return true;
        }
        for (PsiElement directive : directives) {
            PsiElement namespace = CNameResolver.resolveFirst(directive);
            if (namespace instanceof CPsiNamespaceDeclaration && !offerAll(namespaceCandidates(namespace, processor, state), processor, state)) {
                return false;
            }
        }
        return true;
    }

    private static List<PsiElement> topLevelDirectives(PsiFile file) {
        return LanguageCachedValueUtil.getProjectPsiDependentCache(file, CScopes::directivesAmong);
    }

    private static List<PsiElement> directivesAmong(PsiElement scope) {
        List<PsiElement> directives = new ArrayList<>();
        for (PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (CDeclarations.usingOf(child) == CDeclarations.Using.DIRECTIVE) {
                directives.add(child);
            }
        }
        return directives;
    }

    private static boolean offerParameters(CPsiParameterList parameterList, PsiScopeProcessor processor, ResolveState state) {
        List<PsiElement> parameters = new ArrayList<>();
        for (PsiElement child = parameterList.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof CPsiParameter && CDeclarations.nameIdentifier(child) != null) {
                parameters.add(child);
            }
        }
        return parameters.isEmpty() || offerLevel(parameters, processor, state);
    }

    private static @Nullable PsiElement previousSignificant(PsiElement element) {
        PsiElement previous = element.getPrevSibling();
        while (previous instanceof PsiWhiteSpace || previous instanceof PsiComment) {
            previous = previous.getPrevSibling();
        }
        return previous;
    }

    /**
     * The members of a class, struct, union, enum or namespace, as a qualifier names them: a class answers with its own
     * members and then with those of its bases, a namespace with the members of all of its blocks in the file, a typedef
     * with those of the type it names.
     */
    public static boolean processMembers(PsiElement owner, PsiScopeProcessor processor, ResolveState state) {
        return processMembers(owner, processor, state, new HashSet<>(), 0, null);
    }

    /**
     * The same, with the template parameters of {@code templateOwner} between the class's own members and those of its
     * bases.
     */
    private static boolean processMembers(PsiElement owner,
                                          PsiScopeProcessor processor,
                                          ResolveState state,
                                          Set<PsiElement> visited,
                                          int depth,
                                          @Nullable PsiElement templateOwner) {
        ProgressManager.checkCanceled();
        PsiElement scope = CNameResolver.scopeOf(owner);
        if (scope != null && !(scope instanceof CPsiNamespaceDeclaration) && !CDeclarations.hasBody(scope)) {
            scope = definitionOf(scope, state.get(ORIGIN_FILE));
        }
        if (scope == null || depth > MAX_BASE_DEPTH || !visited.add(scope)) {
            return templateOwner == null || processTemplateParameters(templateOwner, processor, state);
        }
        if (scope instanceof CPsiNamespaceDeclaration) {
            return offerLevel(namespaceCandidates(scope, processor, state), processor, state);
        }
        if (!offerByName(ownMemberMap(scope), processor, state)) {
            return false;
        }
        if (templateOwner != null && !processTemplateParameters(templateOwner, processor, state)) {
            return false;
        }
        CPsiSuperClassList superClassList = CDeclarations.childOfType(scope, CPsiSuperClassList.class);
        if (superClassList != null) {
            for (CPsiSuperClass superClass : superClassList.getSuperClasses()) {
                PsiElement base = CNameResolver.resolveFirst(superClass);
                if (base != null && !processMembers(base, processor, state, visited, depth + 1, null)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Offers the declarations of one level; whether the walk goes on.
     */
    public static boolean offerLevel(Collection<PsiElement> declarations, PsiScopeProcessor processor, ResolveState state) {
        return offerAll(declarations, processor, state) && isLevelOpen(processor);
    }

    /**
     * Offers declarations without ending the level; whether the processor wants more.
     */
    private static boolean offerAll(Collection<PsiElement> declarations, PsiScopeProcessor processor, ResolveState state) {
        for (PsiElement declaration : declarations) {
            if (!processor.execute(declaration, state)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the walk goes on after a level: not once it declared the name.
     */
    private static boolean isLevelOpen(PsiScopeProcessor processor) {
        return !(processor instanceof CResolveProcessor resolveProcessor && resolveProcessor.isLevelResolved());
    }

    private static boolean offerByName(Map<String, List<PsiElement>> members, PsiScopeProcessor processor, ResolveState state) {
        return offerLevel(memberCandidates(members, processor), processor, state);
    }

    /**
     * The members a processor asks for: those of its name, or all when it names none.
     */
    private static List<PsiElement> memberCandidates(Map<String, List<PsiElement>> members, PsiScopeProcessor processor) {
        String name = processor.getHint(CResolveProcessor.NAME_HINT);
        if (name != null) {
            return members.getOrDefault(name, List.of());
        }
        List<PsiElement> all = new ArrayList<>();
        for (List<PsiElement> named : members.values()) {
            all.addAll(named);
        }
        return all;
    }

    /**
     * The declarations of a class, struct, union, enum or file by name, kept until the next change of PSI.
     */
    private static Map<String, List<PsiElement>> ownMemberMap(PsiElement owner) {
        return LanguageCachedValueUtil.getProjectPsiDependentCache(owner, CScopes::buildOwnMemberMap);
    }

    private static Map<String, List<PsiElement>> buildOwnMemberMap(PsiElement owner) {
        return byName(CDeclarations.ownMembersOf(owner));
    }

    /**
     * The file a lookup started in: a namespace's members are those of its blocks in that file and in the headers it
     * includes, whichever block the namespace's name resolved to - {@code std} is reopened in hundreds of headers.
     */
    public static final Key<PsiFile> ORIGIN_FILE = Key.create("c.resolve.origin.file");

    /**
     * {@code state} knowing the file of {@code place} as the one the lookup started in, unless it knows one already.
     */
    public static ResolveState withOrigin(ResolveState state, PsiElement place) {
        if (state.get(ORIGIN_FILE) != null) {
            return state;
        }
        PsiFile file = place.getContainingFile();
        return file == null ? state : state.put(ORIGIN_FILE, file);
    }

    /**
     * The members of a namespace: of its blocks in its own file, and - by name only - of its blocks in the file the
     * lookup started in and in the headers either includes. The block the name resolved to may be in a header which
     * declares only part of the namespace.
     */
    private static List<PsiElement> namespaceCandidates(PsiElement namespace, PsiScopeProcessor processor, ResolveState state) {
        List<PsiElement> candidates = new ArrayList<>(memberCandidates(namespaceMemberMap(namespace), processor));
        String name = processor.getHint(CResolveProcessor.NAME_HINT);
        PsiFile file = namespace.getContainingFile();
        if (name != null && file != null) {
            List<String> path = namespacePath(namespace);
            Set<PsiFile> files = new LinkedHashSet<>();
            PsiFile origin = state.get(ORIGIN_FILE);
            if (origin != null) {
                PsiFile originRoot = origin.getViewProvider().getPsi(CLanguage.INSTANCE);
                if (originRoot != null) {
                    files.add(originRoot);
                    files.addAll(CIncludedDeclarations.headersMentioning(originRoot, name));
                }
            }
            files.addAll(CIncludedDeclarations.headersMentioning(file, name));
            files.remove(file);
            for (PsiFile header : files) {
                Map<String, List<PsiElement>> members = namespaceMapsOf(header).get(path);
                if (members != null) {
                    for (PsiElement member : members.getOrDefault(name, List.of())) {
                        if (!candidates.contains(member)) {
                            candidates.add(member);
                        }
                    }
                }
            }
        }
        return candidates;
    }

    /**
     * The definition of a class only declared where it was found - {@code class basic_string;} of a header which does
     * not include the one defining it - looked up in the file the lookup started in and the headers it includes; the
     * declaration itself when there is none.
     */
    public static PsiElement definitionOf(PsiElement declaration, @Nullable PsiFile origin) {
        if (CDeclarations.hasBody(declaration) || !CDeclarations.isAggregate(declaration)) {
            return declaration;
        }
        String name = CDeclarations.nameOf(declaration);
        PsiFile originRoot = origin == null ? null : origin.getViewProvider().getPsi(CLanguage.INSTANCE);
        PsiElement parent = declaration.getParent();
        if (name == null || originRoot == null || !(parent instanceof PsiFile || parent instanceof CPsiNamespaceDeclaration)) {
            return declaration;
        }
        List<String> path = parent instanceof PsiFile ? List.of() : namespacePath(parent);
        Set<PsiFile> files = new LinkedHashSet<>();
        files.add(originRoot);
        files.addAll(CIncludedDeclarations.headersMentioning(originRoot, name));
        for (PsiFile file : files) {
            Map<String, List<PsiElement>> members = path.isEmpty() ? ownMemberMap(file) : namespaceMapsOf(file).get(path);
            for (PsiElement member : members == null ? List.<PsiElement>of() : members.getOrDefault(name, List.of())) {
                if (CDeclarations.isAggregate(member) && CDeclarations.hasBody(member)) {
                    return member;
                }
            }
        }
        return declaration;
    }

    /**
     * The members of each namespace of a file, by the path of its names: a header answers for the namespaces it reopens.
     */
    private static Map<List<String>, Map<String, List<PsiElement>>> namespaceMapsOf(PsiFile file) {
        return LanguageCachedValueUtil.getProjectPsiDependentCache(file, CScopes::buildNamespaceMaps);
    }

    private static Map<List<String>, Map<String, List<PsiElement>>> buildNamespaceMaps(PsiFile file) {
        Map<List<String>, List<PsiElement>> members = new LinkedHashMap<>();
        for (CPsiNamespaceDeclaration block : PsiTreeUtil.findChildrenOfType(file, CPsiNamespaceDeclaration.class)) {
            members.computeIfAbsent(namespacePath(block), key -> new ArrayList<>()).addAll(CDeclarations.ownMembersOf(block));
        }
        Map<List<String>, Map<String, List<PsiElement>>> maps = new LinkedHashMap<>();
        members.forEach((path, declarations) -> maps.put(path, byName(declarations)));
        return maps;
    }

    /**
     * The members of every block of a namespace in its file - {@code namespace a { }} reopened, or reopened inside
     * another reopened {@code namespace outer { }} - by name.
     */
    private static Map<String, List<PsiElement>> namespaceMemberMap(PsiElement namespace) {
        return LanguageCachedValueUtil.getProjectPsiDependentCache(namespace, CScopes::buildNamespaceMemberMap);
    }

    private static Map<String, List<PsiElement>> buildNamespaceMemberMap(PsiElement namespace) {
        List<String> path = namespacePath(namespace);
        List<PsiElement> members = new ArrayList<>();
        PsiFile file = namespace.getContainingFile();
        for (CPsiNamespaceDeclaration block : PsiTreeUtil.findChildrenOfType(file, CPsiNamespaceDeclaration.class)) {
            if (block == namespace || path.equals(namespacePath(block))) {
                members.addAll(CDeclarations.ownMembersOf(block));
            }
        }
        return byName(members);
    }

    /**
     * The names of a namespace and of the named namespaces around it, innermost last; an anonymous or inline one adds
     * nothing, its members belong to the enclosing namespace.
     */
    private static List<String> namespacePath(PsiElement namespace) {
        List<String> path = new ArrayList<>();
        PsiFile file = namespace.getContainingFile();
        Set<String> inlineNames = file == null ? Set.of() : CIncludedDeclarations.inlineNamespaceNames(file);
        for (PsiElement element = namespace; element != null && !(element instanceof PsiFile); element = element.getParent()) {
            if (element instanceof CPsiNamespaceDeclaration) {
                String name = CDeclarations.nameOf(element);
                // an inline namespace adds no name - written so, or reopened plainly after its first declaration said
                // so: its members are those of the namespace around it
                if (name != null && !inlineNames.contains(name) && !CDeclarations.isInlineNamespace(element)) {
                    path.add(0, name);
                }
            }
        }
        return path;
    }

    private static Map<String, List<PsiElement>> byName(List<PsiElement> declarations) {
        Map<String, List<PsiElement>> map = new LinkedHashMap<>();
        for (PsiElement declaration : declarations) {
            String name = CDeclarations.introducedName(declaration);
            if (name != null) {
                map.computeIfAbsent(name, key -> new ArrayList<>(1)).add(declaration);
            }
        }
        return map;
    }
}
