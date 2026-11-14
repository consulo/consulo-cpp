package consulo.cpp.preprocessor.expand;

import consulo.cpp.lang.psi.CPsiTokens;
import consulo.cpp.lang.version.CLanguageVersion;
import consulo.cpp.moduleAware.CIncludeWalker;
import consulo.cpp.preprocessor.CPreprocessorLanguage;
import consulo.cpp.preprocessor.psi.*;
import consulo.cpp.preprocessor.psi.impl.visitor.CPreprocessorRecursiveElementVisitor;
import consulo.cpp.preprocessor.psi.stub.CPreprocessorDirectiveStub;
import consulo.cpp.preprocessor.psi.stub.CPreprocessorFileStub;
import consulo.language.psi.stub.StubElement;
import consulo.cpp.preprocessor.psi.stub.CPreprocessorIfBodyStub;
import consulo.cpp.preprocessor.psi.stub.CPreprocessorIfBlockStub;
import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.stub.ObjectStubTree;
import consulo.language.psi.stub.Stub;
import consulo.language.psi.stub.StubTreeLoader;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.light.LightVirtualFileBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author VISTALL
 * @since 17:11/2020-07-31
 */
public class PreprocessorExpander {
    public static final Key<PreprocessorExpander> EXPANDER_KEY = Key.create("PreprocessorExpander");

    private final Map<String, ExpandedMacro> myDefines = new LinkedHashMap<>();

    /**
     * A change of a macro by a directive of the file: from {@code offset} on, the macro is {@code macro} - {@code null}
     * once it is undefined. The first change of a name is preceded by one at {@code -1}, what it was before the file.
     */
    private record MacroChange(int offset, @Nullable ExpandedMacro macro) {
    }

    /**
     * The changes of the macros the file's own directives - and the headers it includes - define or undefine, in the
     * order of the file: a macro is what its last change before a use made it, not what it is at the end of the file -
     * {@code #undef __STORAGE__} after the declarations using it does not take it from them.
     */
    private final Map<String, List<MacroChange>> myChanges = new HashMap<>();

    /**
     * The offset of the directive being expanded; {@code -1} outside the file's own directives (forced includes).
     */
    private int myDirectiveOffset = -1;

    private final CTemplateDataModifications myModifications = new CTemplateDataModifications();

    private final CPreprocessorDirectiveCollector rangeCollector = new CPreprocessorDirectiveCollector();

    private final CPreprocessorExpandContext mySourceContext = new SourceContext();

    private final List<TextRange> myInactiveRanges = new ArrayList<>();

    private final PsiFile myPreprocessorFile;
    private final CLanguageVersion myLanguageVersion;
    private final Set<VirtualFile> myProcessedFiles;
    /**
     * Configured include search directories (analogous to {@code -I} flags).
     * Used as a fallback for {@code #include "..."} when the path is not found
     * relative to the current file, and as the primary source for
     * {@code #include <...>}.
     */
    private final List<VirtualFile> myIncludePaths;

    public PreprocessorExpander(PsiFile preprocessorFile, CLanguageVersion languageVersion) {
        this(preprocessorFile, languageVersion, new HashSet<>(), Collections.emptyList(), Collections.emptyMap(), List.of());
    }

    public PreprocessorExpander(PsiFile preprocessorFile,
                                CLanguageVersion languageVersion,
                                @NotNull List<VirtualFile> includePaths) {
        this(preprocessorFile, languageVersion, new HashSet<>(), includePaths, Collections.emptyMap(), List.of());
    }

    /**
     * Expands with {@code seedDefines} already in scope, as if every name had been defined above the first line.
     * This is how per-module defines and the defines of an including file reach the expansion.
     */
    public PreprocessorExpander(PsiFile preprocessorFile,
                                CLanguageVersion languageVersion,
                                @NotNull List<VirtualFile> includePaths,
                                @NotNull Map<String, String> seedDefines) {
        this(preprocessorFile, languageVersion, includePaths, seedDefines, List.of());
    }

    /**
     * Expands with {@code seedDefines} in scope and then the {@code forcedIncludes} applied, as if each were included
     * above the first line - {@code -include} of gcc and clang, {@code /FI} of MSVC.
     */
    public PreprocessorExpander(PsiFile preprocessorFile,
                                CLanguageVersion languageVersion,
                                @NotNull List<VirtualFile> includePaths,
                                @NotNull Map<String, String> seedDefines,
                                @NotNull List<String> forcedIncludes) {
        this(preprocessorFile, languageVersion, new HashSet<>(), includePaths, seedMacros(languageVersion, seedDefines), forcedIncludes);
    }

    /**
     * @param inheritedMacros the macros in scope above the first line - the macros themselves, so a function-like
     *                        macro an including file defined stays function-like here
     */
    private PreprocessorExpander(PsiFile preprocessorFile,
                                 CLanguageVersion languageVersion,
                                 Set<VirtualFile> processedFiles,
                                 List<VirtualFile> includePaths,
                                 Map<String, ExpandedMacro> inheritedMacros,
                                 List<String> forcedIncludes) {
        myPreprocessorFile = preprocessorFile;
        myLanguageVersion = languageVersion;
        myProcessedFiles = processedFiles;
        myIncludePaths = includePaths;

        myDefines.putAll(inheritedMacros);

        VirtualFile vFile = sourceFile(preprocessorFile);
        if (vFile != null) {
            myProcessedFiles.add(vFile);
        }

        for (String forcedInclude : forcedIncludes) {
            processForcedInclude(forcedInclude, vFile);
        }

        preprocessorFile.accept(new CPreprocessorRecursiveElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                if (element instanceof CPreprocessorDirective directive) {
                    myDirectiveOffset = directive.getTextRange().getStartOffset();
                    directive.expand(mySourceContext);
                }
                else {
                    super.visitElement(element);
                }
            }

            @Override
            public void visitPreprocessorIfBlock(CPreprocessorIfBlock element) {
                TextRange blockRange = element.getTextRange();
                CPsiSharpIfBody activeBody = selectActiveBody(element);

                if (activeBody == null) {
                    myModifications.addOuterRange(blockRange);
                }
                else {
                    TextRange activeRange = activeBody.getTextRange();
                    addOuterRange(blockRange.getStartOffset(), activeRange.getStartOffset());
                    activeBody.acceptChildren(this);
                    addOuterRange(activeRange.getEndOffset(), blockRange.getEndOffset());
                }

                for (PsiElement branch = element.getFirstChild(); branch != null; branch = branch.getNextSibling()) {
                    if (branch instanceof CPsiSharpIfBody && branch != activeBody) {
                        myInactiveRanges.add(branch.getTextRange());
                    }
                }
            }
        });
    }

    // -----------------------------------------------------------------------
    // Include resolution
    // -----------------------------------------------------------------------

    /**
     * The file includes are resolved against: the file itself, or - for a light copy, such as the one completion
     * parses - the file it was copied from.
     */
    private static @Nullable VirtualFile sourceFile(PsiFile file) {
        VirtualFile virtualFile = file.getOriginalFile().getVirtualFile();
        if (virtualFile instanceof LightVirtualFileBase light && light.getOriginalFile() != null) {
            return light.getOriginalFile();
        }
        return virtualFile;
    }

    /**
     * Resolve a {@code #include "name"} directive.
     * Looks in the current file's directory first; if not found there, falls
     * back to the configured {@link #myIncludePaths}.
     */
    private void processInclude(@Nullable String includeName) {
        if (includeName == null) {
            return;
        }

        VirtualFile currentVFile = sourceFile(myPreprocessorFile);
        if (currentVFile == null) {
            return;
        }

        VirtualFile parent = currentVFile.getParent();
        if (parent != null) {
            // Source 1: file-relative directory
            processIncludeFromDir(includeName, parent);
        }
        else {
            // No parent directory — fall straight through to include search paths
            processIndependInclude(includeName);
        }
    }

    private void processForcedInclude(String path, @Nullable VirtualFile currentVFile) {
        if (currentVFile == null) {
            processIndependInclude(path);
            return;
        }
        VirtualFile found = CIncludeWalker.resolveForcedInclude(path, currentVFile, myIncludePaths);
        if (found != null) {
            processResolvedFile(found);
        }
    }

    /**
     * Resolve a {@code #include <name>} directive (or a {@code #include "name"}
     * that was not found in the file-relative directory).
     * Searches only the configured {@link #myIncludePaths}.
     */
    private void processIndependInclude(@Nullable String includeName) {
        if (includeName == null) {
            return;
        }

        // Source 2: configured include search paths
        for (VirtualFile searchDir : myIncludePaths) {
            VirtualFile found = searchDir.findFileByRelativePath(includeName);
            if (found != null) {
                processResolvedFile(found);
                return;
            }
        }
    }

    /**
     * Resolve {@code includeName} relative to {@code fromDir}.
     * If the file is not found there, falls back to
     * {@link #processIndependInclude} (the configured include paths).
     * <p>
     * Used both for the top-level {@code #include "..."} in the current file
     * and for transitive includes encountered while walking stub trees.
     *
     * @param includeName relative path as written between the quotes / angles
     * @param fromDir     directory from which the path should be resolved first
     */
    private void processIncludeFromDir(@Nullable String includeName, @NotNull VirtualFile fromDir) {
        if (includeName == null) {
            return;
        }

        // Source 1: directory-relative lookup
        VirtualFile found = fromDir.findFileByRelativePath(includeName);
        if (found != null) {
            processResolvedFile(found);
            return;
        }

        // Source 2: fall back to configured include search paths
        processIndependInclude(includeName);
    }

    /**
     * Given a resolved {@link VirtualFile}, perform cycle detection and then
     * read its macro definitions — preferring the stub index to avoid full
     * PSI loading, with a PSI fallback when stubs are not yet available.
     *
     * @param includedVFile the file whose macros should be merged into
     *                      {@link #myDefines}
     */
    private void processResolvedFile(@NotNull VirtualFile includedVFile) {
        // Cycle detection: skip files we are already expanding
        if (!myProcessedFiles.add(includedVFile)) {
            return;
        }

        // Prefer stubs — safe during indexing, no cascading PSI loads
        Project project = myPreprocessorFile.getProject();
        ObjectStubTree<?> stubTree = StubTreeLoader.getInstance().readOrBuild(project, includedVFile, null);
        if (stubTree != null) {
            Stub root = stubTree.getRoot();
            if (root instanceof CPreprocessorFileStub) {
                processDefinesFromFileStub((CPreprocessorFileStub) root, includedVFile);
                return;
            }
        }

        // Fall back to full PSI loading (e.g. during editing before stubs are built)
        PsiFile includedPsiFile = myPreprocessorFile.getManager().findFile(includedVFile);
        if (includedPsiFile == null) {
            return;
        }

        // Only C/C++ files have a CPreprocessorLanguage root — skip everything else
        PsiFile preprocessorRoot = includedPsiFile.getViewProvider().getPsi(CPreprocessorLanguage.INSTANCE);
        if (preprocessorRoot == null) {
            return;
        }

        // Recursively expand the included file, sharing the processed-files set and include paths
        PreprocessorExpander childExpander =
                new PreprocessorExpander(preprocessorRoot, myLanguageVersion, myProcessedFiles, myIncludePaths, myDefines, List.of());
        // The child started from our defines, so its final state is the state after the include: names it
        // undefined are gone, the rest are added or overridden.
        if (myDirectiveOffset >= 0) {
            for (Map.Entry<String, ExpandedMacro> entry : myDefines.entrySet()) {
                if (!childExpander.myDefines.containsKey(entry.getKey())) {
                    recordChange(entry.getKey(), null);
                }
            }
            for (Map.Entry<String, ExpandedMacro> entry : childExpander.myDefines.entrySet()) {
                if (myDefines.get(entry.getKey()) != entry.getValue()) {
                    recordChange(entry.getKey(), entry.getValue());
                }
            }
        }
        myDefines.keySet().retainAll(childExpander.myDefines.keySet());
        myDefines.putAll(childExpander.myDefines);
    }

    /**
     * Walk the direct stub children of a preprocessor file stub and collect
     * macro definitions into {@link #myDefines}, recursively following any
     * {@code #include} stubs for transitive header chains.
     *
     * @param fileStub      the root stub of the included file
     * @param includedVFile the virtual file that owns {@code fileStub} (used to
     *                      resolve relative paths in transitive {@code #include}s)
     */
    private void processDefinesFromFileStub(@NotNull CPreprocessorFileStub fileStub,
                                            @NotNull VirtualFile includedVFile) {
        processStubChildren(fileStub, includedVFile.getParent());
    }

    /**
     * Walks a stub subtree applying its directives in order. Conditionals are evaluated against the defines
     * gathered so far and only the active branch is descended into, so a header contributes the defines of the
     * branch that is really compiled rather than of every branch it contains.
     */
    private void processStubChildren(@NotNull StubElement<?> parent, @Nullable VirtualFile dir) {
        HeaderContext context = new HeaderContext(dir);
        for (StubElement<?> child : parent.getChildrenStubs()) {
            if (child instanceof CPreprocessorDirectiveStub directive) {
                directive.expand(context);
            }
            else if (child instanceof CPreprocessorIfBlockStub) {
                CPreprocessorIfBlockStub block = (CPreprocessorIfBlockStub) child;
                String name = block.getVariableName();
                boolean defined = name != null && myDefines.containsKey(name);
                boolean thenActive = defined != block.isReverted();

                for (StubElement<?> body : block.getChildrenStubs()) {
                    if (body instanceof CPreprocessorIfBodyStub
                        && ((CPreprocessorIfBodyStub) body).isElseBody() != thenActive) {
                        processStubChildren(body, dir);
                    }
                }
            }
        }
    }

    /**
     * Defines and undefines shared by both contexts.
     */
    private abstract class BaseContext implements CPreprocessorExpandContext {
        @Override
        public void define(String name, @Nullable List<String> parameters, CharSequence value, @Nullable CPreprocessorDefineDirective element) {
            ExpandedMacro macro = new ExpandedMacro(myLanguageVersion, name, parameters, element, value);
            recordChange(name, macro);
            myDefines.put(name, macro);
        }

        @Override
        public void undefine(String name) {
            recordChange(name, null);
            myDefines.remove(name);
        }

        @Override
        public boolean isDefined(String name) {
            return myDefines.containsKey(name);
        }
    }

    /**
     * Directives of the file being expanded: their text is taken out of what the C parser sees.
     */
    private final class SourceContext extends BaseContext {
        @Override
        public void removeFromSource(TextRange range) {
            myModifications.addOuterRange(range);
        }

        @Override
        public void include(String path, boolean system) {
            if (system) {
                processIndependInclude(path);
            }
            else {
                processInclude(path);
            }
        }
    }

    /**
     * Directives of an included header, applied from its stubs: only their effect on the macros matters.
     */
    private final class HeaderContext extends BaseContext {
        private final @Nullable VirtualFile myDirectory;

        private HeaderContext(@Nullable VirtualFile directory) {
            myDirectory = directory;
        }

        @Override
        public void removeFromSource(TextRange range) {
        }

        @Override
        public void include(String path, boolean system) {
            if (system) {
                processIndependInclude(path);
            }
            else if (myDirectory != null) {
                processIncludeFromDir(path, myDirectory);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Defines given as text - by the module, or predefined by the language version - are object-like macros.
     */
    private static Map<String, ExpandedMacro> seedMacros(CLanguageVersion languageVersion, Map<String, String> seedDefines) {
        Map<String, ExpandedMacro> macros = new LinkedHashMap<>();
        for (Map.Entry<String, String> seed : seedDefines.entrySet()) {
            macros.put(seed.getKey(), new ExpandedMacro(languageVersion, seed.getKey(), null, null, seed.getValue()));
        }
        return macros;
    }

    /**
     * The parameters of the function-like macros defined, for conditions calling them - {@code __GLIBC_USE(LIB_EXT2)}.
     */
    private Map<String, List<String>> parametersView() {
        Map<String, List<String>> view = new LinkedHashMap<>();
        for (Map.Entry<String, ExpandedMacro> entry : myDefines.entrySet()) {
            List<String> parameters = entry.getValue().getParameters();
            if (parameters != null) {
                view.put(entry.getKey(), parameters);
            }
        }
        return view;
    }

    private Map<String, String> definesView() {
        Map<String, String> view = new LinkedHashMap<>();
        for (Map.Entry<String, ExpandedMacro> entry : myDefines.entrySet()) {
            view.put(entry.getKey(), entry.getValue().getText().toString());
        }
        return view;
    }

    /**
     * Picks the branch of a conditional that is really compiled. The block is a flat run of keyword, condition
     * and body, so the branches are scanned in source order and the first one whose condition holds wins -
     * which is what makes {@code #elif} chains behave, with {@code #else} as the branch that always holds.
     */
    private CPsiSharpIfBody selectActiveBody(CPreprocessorIfBlock element) {
        Map<String, String> defines = definesView();
        Map<String, List<String>> parameters = parametersView();
        BranchKind kind = null;
        StringBuilder condition = new StringBuilder();

        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            IElementType type = child.getNode().getElementType();

            if (type == CPreprocessorTokenTypes.S_IF_KEYWORD) {
                kind = BranchKind.IF;
                condition.setLength(0);
            }
            else if (type == CPreprocessorTokenTypes.S_IFDEF_KEYWORD) {
                kind = BranchKind.IFDEF;
                condition.setLength(0);
            }
            else if (type == CPreprocessorTokenTypes.S_IFNDEF_KEYWORD) {
                kind = BranchKind.IFNDEF;
                condition.setLength(0);
            }
            else if (type == CPreprocessorTokenTypes.S_ELIF_KEYWORD) {
                kind = BranchKind.ELIF;
                condition.setLength(0);
            }
            else if (type == CPreprocessorTokenTypes.S_ELSE_KEYWORD) {
                kind = BranchKind.ELSE;
                condition.setLength(0);
            }
            else if (child instanceof CPsiSharpIfBody) {
                if (kind != null && isBranchActive(kind, condition.toString(), defines, parameters)) {
                    return (CPsiSharpIfBody) child;
                }
                kind = null;
            }
            else if (kind != null) {
                condition.append(child.getText());
            }
        }
        return null;
    }

    private static boolean isBranchActive(BranchKind kind, String condition, Map<String, String> defines, Map<String, List<String>> parameters) {
        return switch (kind) {
            case IFDEF -> defines.containsKey(condition.trim());
            case IFNDEF -> !defines.containsKey(condition.trim());
            case ELSE -> true;
            case IF, ELIF -> CPreprocessorConditionEvaluator.evaluate(condition, defines, parameters);
        };
    }

    private enum BranchKind {
        IF,
        IFDEF,
        IFNDEF,
        ELIF,
        ELSE
    }

    private void addOuterRange(int startOffset, int endOffset) {
        if (endOffset > startOffset) {
            myModifications.addOuterRange(new TextRange(startOffset, endOffset));
        }
    }

    /**
     * Ranges of the conditional branches which this expansion excluded, for painting them as inactive code.
     */
    public List<TextRange> getInactiveRanges() {
        return myInactiveRanges;
    }

    public CharSequence buildText(CharSequence sequence) {
        return rangeCollector.applyTemplateDataModifications(sequence, myModifications);
    }

    public CPreprocessorDirectiveCollector getRangeCollector() {
        return rangeCollector;
    }

    public CLanguageVersion getLanguageVersion() {
        return myLanguageVersion;
    }

    /**
     * The names of the macros defined once the file is expanded.
     */
    public java.util.Set<String> getMacroNames() {
        return java.util.Collections.unmodifiableSet(myDefines.keySet());
    }

    @Nullable
    public ExpandedMacro getMacro(@NotNull String name) {
        return myDefines.get(name);
    }

    /**
     * The macro {@code name} is at {@code offset} of the text the C lexer reads - defined by the last {@code #define}
     * before it and not undefined since; {@code -1} for the macro at the end of the file.
     */
    public @Nullable ExpandedMacro getMacroAt(String name, int offset) {
        List<MacroChange> changes = offset < 0 ? null : myChanges.get(name);
        if (changes == null) {
            return myDefines.get(name);
        }
        ExpandedMacro macro = null;
        for (MacroChange change : changes) {
            // a directive is removed from that text, so one right before a use has the use's offset
            if (change.offset() > offset) {
                break;
            }
            macro = change.macro();
        }
        return macro;
    }

    /**
     * Records that the directive being expanded makes {@code name} {@code macro}; before {@link #myDefines} changes.
     */
    private void recordChange(String name, @Nullable ExpandedMacro macro) {
        if (myDirectiveOffset < 0) {
            return;
        }
        List<MacroChange> changes = myChanges.get(name);
        if (changes == null) {
            changes = new ArrayList<>(2);
            changes.add(new MacroChange(-1, myDefines.get(name)));
            myChanges.put(name, changes);
        }
        changes.add(new MacroChange(templateOffsetOf(myDirectiveOffset), macro));
    }

    /**
     * Where {@code sourceOffset} is in the text the C lexer reads - the file without its directives and inactive
     * branches, the ranges {@link #myModifications} has removed so far, which are those before it.
     */
    private int templateOffsetOf(int sourceOffset) {
        int removed = 0;
        for (TextRange range : myModifications.myOuterAndRemoveRanges) {
            if (range instanceof CPreprocessorDirectiveCollector.RangeToRemove) {
                continue;
            }
            if (range.getEndOffset() <= sourceOffset) {
                removed += range.getLength();
            }
            else if (range.getStartOffset() < sourceOffset) {
                removed += sourceOffset - range.getStartOffset();
            }
        }
        return sourceOffset - removed;
    }

    // -----------------------------------------------------------------------
    // Expansion
    // -----------------------------------------------------------------------

    /**
     * The tokens one use of {@code macro} stands for: the arguments of a function-like macro substituted into its
     * body - {@code #} stringizing, {@code ##} pasting, {@code __VA_ARGS__} - and the result rescanned for further
     * macros. A macro is not expanded again inside its own expansion, which is what stops self-reference.
     *
     * @param arguments the argument tokens of a function-like macro, {@code null} for an object-like one
     */
    public List<CMacroToken> expand(ExpandedMacro macro, @Nullable List<List<CMacroToken>> arguments) {
        return expand(macro, arguments, -1);
    }

    /**
     * The same, for a use at {@code offset} of the file: the macros its expansion uses are those defined there.
     */
    public List<CMacroToken> expand(ExpandedMacro macro, @Nullable List<List<CMacroToken>> arguments, int offset) {
        return expand(macro, arguments, new HashSet<>(), offset);
    }

    private List<CMacroToken> expand(ExpandedMacro macro, @Nullable List<List<CMacroToken>> arguments, Set<String> disabled, int offset) {
        List<CMacroToken> tokens = macro.isFunctionLike() ? substitute(macro, arguments == null ? List.of() : arguments, disabled, offset) : macro.getBody();

        Set<String> inner = new HashSet<>(disabled);
        inner.add(macro.getName());
        return rescan(tokens, inner, offset);
    }

    private List<CMacroToken> substitute(ExpandedMacro macro, List<List<CMacroToken>> arguments, Set<String> disabled, int offset) {
        List<String> parameters = macro.getParameters();
        List<CMacroToken> body = macro.getBody();
        List<CMacroToken> result = new ArrayList<>();

        for (int i = 0; i < body.size(); i++) {
            CMacroToken token = body.get(i);

            if (token.type() == CPsiTokens.SHARP && i + 1 < body.size()) {
                List<CMacroToken> argument = argumentOf(parameters, arguments, body.get(i + 1));
                if (argument != null) {
                    result.add(stringize(argument));
                    i++;
                    continue;
                }
            }

            List<CMacroToken> argument = argumentOf(parameters, arguments, token);
            if (argument == null) {
                result.add(token);
            }
            else {
                // an operand of ## is pasted as written, any other argument is expanded first
                boolean pasted = i > 0 && body.get(i - 1).type() == CPsiTokens.SHARPSHARP
                    || i + 1 < body.size() && body.get(i + 1).type() == CPsiTokens.SHARPSHARP;
                result.addAll(pasted ? argument : rescan(argument, disabled, offset));
            }
        }
        return paste(result);
    }

    private static @Nullable List<CMacroToken> argumentOf(List<String> parameters, List<List<CMacroToken>> arguments, CMacroToken token) {
        if (token.type() != CPsiTokens.IDENTIFIER) {
            return null;
        }

        int variadic = parameters.indexOf("...");
        if (variadic >= 0 && token.text().equals("__VA_ARGS__")) {
            List<CMacroToken> joined = new ArrayList<>();
            for (int i = variadic; i < arguments.size(); i++) {
                if (i > variadic) {
                    joined.add(new CMacroToken(CPsiTokens.COMMA, ","));
                }
                joined.addAll(arguments.get(i));
            }
            return joined;
        }

        int index = parameters.indexOf(token.text());
        if (index < 0) {
            return null;
        }
        return index < arguments.size() ? arguments.get(index) : List.of();
    }

    private static CMacroToken stringize(List<CMacroToken> argument) {
        StringBuilder text = new StringBuilder("\"");
        for (int i = 0; i < argument.size(); i++) {
            if (i > 0) {
                text.append(' ');
            }
            String part = argument.get(i).text();
            text.append(part.replace("\\", "\\\\").replace("\"", "\\\""));
        }
        return new CMacroToken(CPsiTokens.STRING_LITERAL, text.append('"').toString());
    }

    /**
     * Joins the operands of every {@code ##} into one token, re-lexed; an operand pair which does not lex as one
     * token stays two.
     */
    private List<CMacroToken> paste(List<CMacroToken> tokens) {
        List<CMacroToken> result = new ArrayList<>(tokens.size());
        for (int i = 0; i < tokens.size(); i++) {
            CMacroToken token = tokens.get(i);
            if (token.type() == CPsiTokens.SHARPSHARP && !result.isEmpty() && i + 1 < tokens.size()) {
                CMacroToken left = result.remove(result.size() - 1);
                CMacroToken right = tokens.get(++i);
                List<CMacroToken> joined = ExpandedMacro.lex(myLanguageVersion, left.text() + right.text());
                if (joined.size() == 1) {
                    result.add(joined.get(0));
                }
                else {
                    result.add(left);
                    result.add(right);
                }
            }
            else {
                result.add(token);
            }
        }
        return result;
    }

    /**
     * Expands the macros used in {@code tokens} - which is how a macro body using another macro, or an argument
     * naming one, ends up as plain tokens.
     */
    private List<CMacroToken> rescan(List<CMacroToken> tokens, Set<String> disabled, int offset) {
        List<CMacroToken> result = new ArrayList<>(tokens.size());
        for (int i = 0; i < tokens.size(); i++) {
            CMacroToken token = tokens.get(i);
            ExpandedMacro macro = token.type() == CPsiTokens.IDENTIFIER && !disabled.contains(token.text()) ? getMacroAt(token.text(), offset) : null;
            if (macro == null) {
                result.add(token);
            }
            else if (!macro.isFunctionLike()) {
                result.addAll(expand(macro, null, disabled, offset));
            }
            else {
                int close = i + 1 < tokens.size() && tokens.get(i + 1).type() == CPsiTokens.LPARENTH ? closingParenthesis(tokens, i + 1) : -1;
                if (close < 0) {
                    // a function-like macro name which is not called stays a name
                    result.add(token);
                }
                else {
                    result.addAll(expand(macro, splitArguments(tokens.subList(i + 2, close)), disabled, offset));
                    i = close;
                }
            }
        }
        return result;
    }

    private static int closingParenthesis(List<CMacroToken> tokens, int open) {
        int depth = 0;
        for (int i = open; i < tokens.size(); i++) {
            IElementType type = tokens.get(i).type();
            if (type == CPsiTokens.LPARENTH) {
                depth++;
            }
            else if (type == CPsiTokens.RPARENTH && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Splits the tokens between the parentheses of a macro call at its top-level commas.
     */
    public static List<List<CMacroToken>> splitArguments(List<CMacroToken> tokens) {
        List<List<CMacroToken>> arguments = new ArrayList<>();
        if (tokens.isEmpty()) {
            return arguments;
        }
        List<CMacroToken> current = new ArrayList<>();
        int depth = 0;
        for (CMacroToken token : tokens) {
            if (token.type() == CPsiTokens.COMMA && depth == 0) {
                arguments.add(current);
                current = new ArrayList<>();
                continue;
            }
            if (token.type() == CPsiTokens.LPARENTH) {
                depth++;
            }
            else if (token.type() == CPsiTokens.RPARENTH) {
                depth--;
            }
            current.add(token);
        }
        arguments.add(current);
        return arguments;
    }
}
