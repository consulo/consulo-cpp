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
package consulo.cpp.lang.version;

import consulo.cpp.lang.CLanguage;
import consulo.cpp.lang.editor.highlight.CSyntaxHighlighter;
import consulo.cpp.lang.lexer._CppLexer;
import consulo.cpp.lang.parser.CPsiParserImpl;
import consulo.cpp.lang.psi.CPsiTokens;
import consulo.cpp.preprocessor.CPreprocessorLanguage;
import consulo.cpp.preprocessor.CPreprocessorLanguageVersion;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.language.lexer.FlexAdapter;
import consulo.language.lexer.Lexer;
import consulo.language.parser.PsiParser;
import consulo.language.version.LanguageVersion;
import consulo.language.version.LanguageVersionWithParsing;

import consulo.virtualFileSystem.fileType.FileType;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A version of {@link CLanguage}: one C or C++ standard, or a dialect built on one.
 * <p>
 * Versions are registered as extensions, and each standard extends the one before it ({@code C++20} extends
 * {@code C++17}), inheriting its {@link CLanguageFeature features}. A plugin adds a dialect the same way - an Arduino
 * version would extend {@link Cpp17LanguageVersion}, add its own predefined macros in
 * {@link #fillPredefinedMacros}, and claim {@code .ino} files by implementing
 * {@link consulo.language.version.LanguageVersionWithDefinition}.
 * <p>
 * Everything that reads the text of a file goes through its version: the lexer and parser of the C layer, the
 * {@link #getPreprocessorVersion() preprocessor version}, and the syntax highlighter. A dialect with syntax of its
 * own - Objective-C - overrides them and keeps the rest.
 *
 * @author VISTALL
 * @since 2026-10-19
 */
public abstract class CLanguageVersion extends LanguageVersion implements LanguageVersionWithParsing {
    /**
     * The feature each version-dependent keyword needs; without it the word lexes as an identifier. Spellings which
     * are reserved everywhere - {@code _Static_assert}, {@code __inline} - are lexed without asking.
     */
    private static final Map<IElementType, CLanguageFeature> KEYWORD_FEATURES = Map.ofEntries(
        Map.entry(CPsiTokens.BOOL_KEYWORD, CLanguageFeature.BOOL_KEYWORDS),
        Map.entry(CPsiTokens.BOOL_LITERAL, CLanguageFeature.BOOL_KEYWORDS),
        Map.entry(CPsiTokens.CLASS_KEYWORD, CLanguageFeature.CLASSES),
        Map.entry(CPsiTokens.PUBLIC_KEYWORD, CLanguageFeature.CLASSES),
        Map.entry(CPsiTokens.PRIVATE_KEYWORD, CLanguageFeature.CLASSES),
        Map.entry(CPsiTokens.PROTECTED_KEYWORD, CLanguageFeature.CLASSES),
        Map.entry(CPsiTokens.VIRTUAL_KEYWORD, CLanguageFeature.CLASSES),
        Map.entry(CPsiTokens.EXPLICIT_KEYWORD, CLanguageFeature.CLASSES),
        Map.entry(CPsiTokens.FRIEND_KEYWORD, CLanguageFeature.CLASSES),
        Map.entry(CPsiTokens.MUTABLE_KEYWORD, CLanguageFeature.CLASSES),
        Map.entry(CPsiTokens.THIS_KEYWORD, CLanguageFeature.CLASSES),
        Map.entry(CPsiTokens.OPERATOR_KEYWORD, CLanguageFeature.CLASSES),
        Map.entry(CPsiTokens.CAST_KEYWORD, CLanguageFeature.CLASSES),
        Map.entry(CPsiTokens.TYPEID_KEYWORD, CLanguageFeature.CLASSES),
        Map.entry(CPsiTokens.WCHAR_KEYWORD, CLanguageFeature.CLASSES),
        Map.entry(CPsiTokens.NAMESPACE_KEYWORD, CLanguageFeature.NAMESPACES),
        Map.entry(CPsiTokens.USING_KEYWORD, CLanguageFeature.NAMESPACES),
        Map.entry(CPsiTokens.NEW_KEYWORD, CLanguageFeature.NEW_EXPRESSION),
        Map.entry(CPsiTokens.DELETE_KEYWORD, CLanguageFeature.NEW_EXPRESSION),
        Map.entry(CPsiTokens.TEMPLATE_KEYWORD, CLanguageFeature.TEMPLATES),
        Map.entry(CPsiTokens.TYPENAME_KEYWORD, CLanguageFeature.TEMPLATES),
        Map.entry(CPsiTokens.TRY_KEYWORD, CLanguageFeature.EXCEPTIONS),
        Map.entry(CPsiTokens.CATCH_KEYWORD, CLanguageFeature.EXCEPTIONS),
        Map.entry(CPsiTokens.THROW_KEYWORD, CLanguageFeature.EXCEPTIONS),
        Map.entry(CPsiTokens.INLINE_KEYWORD, CLanguageFeature.INLINE),
        Map.entry(CPsiTokens.RESTRICT_KEYWORD, CLanguageFeature.RESTRICT),
        Map.entry(CPsiTokens.TYPEOF_KEYWORD, CLanguageFeature.TYPEOF),
        Map.entry(CPsiTokens.NULLPTR_KEYWORD, CLanguageFeature.NULLPTR),
        Map.entry(CPsiTokens.CONSTEXPR_KEYWORD, CLanguageFeature.CONSTEXPR),
        Map.entry(CPsiTokens.STATIC_ASSERT_KEYWORD, CLanguageFeature.STATIC_ASSERT),
        Map.entry(CPsiTokens.ALIGNOF_KEYWORD, CLanguageFeature.ALIGNMENT),
        Map.entry(CPsiTokens.ALIGNAS_KEYWORD, CLanguageFeature.ALIGNMENT),
        Map.entry(CPsiTokens.THREAD_LOCAL_KEYWORD, CLanguageFeature.THREAD_LOCAL),
        Map.entry(CPsiTokens.DECLTYPE_KEYWORD, CLanguageFeature.DECLTYPE),
        Map.entry(CPsiTokens.NOEXCEPT_KEYWORD, CLanguageFeature.NOEXCEPT),
        Map.entry(CPsiTokens.CHAR16_KEYWORD, CLanguageFeature.UNICODE_CHAR_TYPES),
        Map.entry(CPsiTokens.CHAR32_KEYWORD, CLanguageFeature.UNICODE_CHAR_TYPES)
    );

    private final Set<CLanguageFeature> myFeatures = EnumSet.noneOf(CLanguageFeature.class);

    protected CLanguageVersion(String id, String presentableName) {
        super(id, presentableName, CLanguage.INSTANCE);
    }

    protected void addFeature(CLanguageFeature feature) {
        myFeatures.add(feature);
    }

    public Set<CLanguageFeature> getFeatures() {
        return Collections.unmodifiableSet(myFeatures);
    }

    public boolean hasFeature(CLanguageFeature feature) {
        return myFeatures.contains(feature);
    }

    /**
     * The standard a dialect builds on - CUDA C++20 builds on C++20; {@code null} for a standard itself. A file of a
     * type the dialect {@link #isDialectFileType claims} gets the dialect of the standard its module configures.
     */
    public @Nullable CLanguageVersion getBaseVersion() {
        return null;
    }

    /**
     * Whether files of {@code fileType} are of this dialect - {@code .cu} of CUDA.
     */
    public boolean isDialectFileType(FileType fileType) {
        return false;
    }

    public String getPresentableName() {
        return getName();
    }

    /**
     * Whether this version is C++ (or a dialect of it) rather than C.
     */
    public abstract boolean isCpp();

    /**
     * Orders versions of the same language: a newer standard weighs more. The standards use their year.
     */
    public abstract int getWeight();

    /**
     * Value of {@code __cplusplus} for C++ or {@code __STDC_VERSION__} for C; empty when the standard defines none.
     */
    public abstract String getVersionMacroValue();

    /**
     * Macros the compiler defines before the first line of every file.
     */
    public final Map<String, String> getPredefinedMacros() {
        Map<String, String> macros = new LinkedHashMap<>();
        fillPredefinedMacros(macros);
        return macros;
    }

    protected void fillPredefinedMacros(Map<String, String> macros) {
        String versionValue = getVersionMacroValue();
        if (isCpp()) {
            macros.put("__cplusplus", versionValue);
        }
        else {
            macros.put("__STDC__", "1");
            if (!versionValue.isEmpty()) {
                macros.put("__STDC_VERSION__", versionValue);
            }
        }
    }

    /**
     * Returns {@code keyword} when this version has the feature it belongs to, {@link CPsiTokens#IDENTIFIER}
     * otherwise - so {@code class} stays a plain name in C.
     */
    public IElementType keywordOrIdentifier(IElementType keyword) {
        CLanguageFeature feature = KEYWORD_FEATURES.get(keyword);
        return feature == null || hasFeature(feature) ? keyword : CPsiTokens.IDENTIFIER;
    }

    @Override
    public Lexer createLexer() {
        return new FlexAdapter(new _CppLexer(this));
    }

    @Override
    public PsiParser createParser() {
        return new CPsiParserImpl();
    }

    /**
     * Highlighter of files of this version. A dialect with tokens of its own supplies one which colors them.
     */
    public SyntaxHighlighter getSyntaxHighlighter() {
        return new CSyntaxHighlighter(this);
    }

    /**
     * Version of the preprocessor layer of a file - directives, includes and macro definitions - which is parsed
     * before {@link #createLexer()} sees the directive-free text. A dialect with directives of its own, such as the
     * {@code #import} of Objective-C, returns its own.
     */
    public CPreprocessorLanguageVersion getPreprocessorVersion() {
        CPreprocessorLanguageVersion version = CPreprocessorLanguage.INSTANCE.findVersionByClass(CPreprocessorLanguageVersion.class);
        if (version == null) {
            throw new IllegalStateException(CPreprocessorLanguageVersion.class.getName() + " is not registered");
        }
        return version;
    }

    @Override
    public TokenSet getWhitespaceTokens() {
        return CPsiTokens.WHITE_SPACE_SET;
    }

    @Override
    public TokenSet getCommentTokens() {
        return CPsiTokens.COMMENT_SET;
    }

    @Override
    public TokenSet getStringLiteralElements() {
        return CPsiTokens.STRING_LITERAL_SET;
    }
}
