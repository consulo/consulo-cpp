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
package consulo.cpp.lang.parser;

import consulo.cpp.lang.version.CLanguageFeature;
import consulo.cpp.lang.version.CLanguageVersion;
import consulo.language.parser.PsiBuilder;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * State of one parse: the builder, the language version, and the type names seen so far. The parts of the grammar -
 * {@link CDeclarationParsing declarations}, {@link CStatementParsing statements}, {@link CExpressionParsing
 * expressions} - hang off it, created by factory methods a dialect overrides to replace a part.
 *
 * @author VISTALL
 * @since 2026-10-19
 */
public class CParsingContext {
    /**
     * Type names of the standard library which are typedefs in its headers - known even when the headers are not.
     */
    private static final Set<String> LIBRARY_TYPE_NAMES = Set.of(
        "FILE", "DIR", "va_list", "jmp_buf", "sigjmp_buf", "fd_set", "bool", "wchar_t"
    );

    private final PsiBuilder myBuilder;
    private final CLanguageVersion myLanguageVersion;
    private final Set<String> myTypeNames = new HashSet<>();
    private int myErrorCount;

    private @Nullable CDeclarationParsing myDeclarationParsing;
    private @Nullable CStatementParsing myStatementParsing;
    private @Nullable CExpressionParsing myExpressionParsing;

    public CParsingContext(PsiBuilder builder, CLanguageVersion languageVersion) {
        myBuilder = builder;
        myLanguageVersion = languageVersion;
    }

    public PsiBuilder getBuilder() {
        return myBuilder;
    }

    /**
     * Counts a reported error, so a speculative parse can tell whether what it read needed one.
     */
    public void errorReported() {
        myErrorCount++;
    }

    public int getErrorCount() {
        return myErrorCount;
    }

    public CLanguageVersion getLanguageVersion() {
        return myLanguageVersion;
    }

    public boolean isCpp() {
        return myLanguageVersion.isCpp();
    }

    public boolean hasFeature(CLanguageFeature feature) {
        return myLanguageVersion.hasFeature(feature);
    }

    /**
     * Whether {@code name} names a type: declared by a typedef earlier in the file (or a class, in C++), a typedef of
     * the standard library, or a name following its {@code _t} convention. Without the headers this is a guess, used
     * only where the grammar is ambiguous - {@code T *x} and {@code (T) x}.
     */
    public boolean isTypeName(String name) {
        return myTypeNames.contains(name)
            || LIBRARY_TYPE_NAMES.contains(name)
            || name.length() > 2 && name.endsWith("_t");
    }

    public void addTypeName(String name) {
        myTypeNames.add(name);
    }

    public CDeclarationParsing getDeclarationParsing() {
        CDeclarationParsing parsing = myDeclarationParsing;
        if (parsing == null) {
            myDeclarationParsing = parsing = createDeclarationParsing();
        }
        return parsing;
    }

    public CStatementParsing getStatementParsing() {
        CStatementParsing parsing = myStatementParsing;
        if (parsing == null) {
            myStatementParsing = parsing = createStatementParsing();
        }
        return parsing;
    }

    public CExpressionParsing getExpressionParsing() {
        CExpressionParsing parsing = myExpressionParsing;
        if (parsing == null) {
            myExpressionParsing = parsing = createExpressionParsing();
        }
        return parsing;
    }

    protected CDeclarationParsing createDeclarationParsing() {
        return isCpp() ? new CppDeclarationParsing(this) : new CDeclarationParsing(this);
    }

    protected CStatementParsing createStatementParsing() {
        return isCpp() ? new CppStatementParsing(this) : new CStatementParsing(this);
    }

    protected CExpressionParsing createExpressionParsing() {
        return isCpp() ? new CppExpressionParsing(this) : new CExpressionParsing(this);
    }
}
