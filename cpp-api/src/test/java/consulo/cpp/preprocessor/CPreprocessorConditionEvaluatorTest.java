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
package consulo.cpp.preprocessor;

import consulo.cpp.preprocessor.expand.CPreprocessorConditionEvaluator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CPreprocessorConditionEvaluatorTest {
    private static final Map<String, String> DEFINES = Map.of(
        "DEBUG", "1",
        "ZERO", "0",
        "GUARD", "",
        "LEVEL", "3",
        "ALIAS", "LEVEL"
    );

    private static boolean eval(String condition) {
        return CPreprocessorConditionEvaluator.evaluate(condition, DEFINES);
    }

    @Test
    public void functionLikeMacros() {
        // glibc's features.h: __GLIBC_USE (F) is __GLIBC_USE_ ## F
        Map<String, String> defines = Map.of("__GLIBC_USE", "__GLIBC_USE_ ## F", "__GLIBC_USE_LIB_EXT2", "1", "__GLIBC_USE_ISOC23", "0",
            "MAX", "((a) > (b) ? (a) : (b))");
        Map<String, List<String>> parameters = Map.of("__GLIBC_USE", List.of("F"), "MAX", List.of("a", "b"));
        assertTrue(CPreprocessorConditionEvaluator.evaluate("__GLIBC_USE (LIB_EXT2)", defines, parameters));
        assertFalse(CPreprocessorConditionEvaluator.evaluate("__GLIBC_USE (ISOC23)", defines, parameters));
        assertTrue(CPreprocessorConditionEvaluator.evaluate("defined X || __GLIBC_USE (LIB_EXT2) || __GLIBC_USE (ISOC23)", defines, parameters));
        assertTrue(CPreprocessorConditionEvaluator.evaluate("MAX(2, (3)) == 3", defines, parameters));
        // an unknown macro called is 0, not a malformed condition
        assertTrue(CPreprocessorConditionEvaluator.evaluate("UNKNOWN(1) || 1", defines, parameters));
        assertFalse(CPreprocessorConditionEvaluator.evaluate("UNKNOWN(1)", defines, parameters));
        // the compiler's __has_* are answered yes
        assertTrue(CPreprocessorConditionEvaluator.evaluate("__has_feature(cxx_access_control_sfinae) && __has_include(<stdio.h>)", defines, parameters));
    }

    @Test
    public void arithmetic() {
        // glibc's __GNUC_PREREQ (4, 6) with gcc 16
        Map<String, String> defines = Map.of("__GNUC__", "16", "__GNUC_MINOR__", "1",
            "__GNUC_PREREQ", "((__GNUC__ << 16) + __GNUC_MINOR__ >= ((maj) << 16) + (min))");
        Map<String, List<String>> parameters = Map.of("__GNUC_PREREQ", List.of("maj", "min"));
        assertTrue(CPreprocessorConditionEvaluator.evaluate("__GNUC_PREREQ (4, 6)", defines, parameters));
        assertFalse(CPreprocessorConditionEvaluator.evaluate("__GNUC_PREREQ (17, 0)", defines, parameters));
        assertTrue(eval("LEVEL * 100 + 2 == 302"));
        assertTrue(eval("(LEVEL & 1) && ~0 && -1 < 0 && 7 % 4 == 3 && (1 | 2) == 3 && (6 ^ 3) == 5"));
        assertTrue(eval("DEBUG ? LEVEL == 3 : 0"));
        assertFalse(eval("1 / ZERO"));
    }

    @Test
    public void comments() {
        // Qt's qtconfigmacros.h
        assertTrue(eval("!defined(QT_NAMESPACE) || defined(Q_MOC_RUN) /* user namespace */"));
        assertTrue(eval("DEBUG // a debug build"));
        assertFalse(eval("ZERO /* still 0 */ && DEBUG"));
    }

    @Test
    public void continuedLines() {
        assertTrue(eval("defined(DEBUG) \\\n    && LEVEL == 3"));
    }

    @Test
    public void literals() {
        assertTrue(eval("1"));
        assertFalse(eval("0"));
        assertTrue(eval("0x10"));
        assertTrue(eval("2u"));
    }

    @Test
    public void definedOperator() {
        assertTrue(eval("defined(DEBUG)"));
        assertTrue(eval("defined DEBUG"));
        assertFalse(eval("defined(MISSING)"));
        assertTrue(eval("!defined(MISSING)"));
    }

    /** A name with no replacement text is still defined, so it is true in a condition. */
    @Test
    public void valuelessDefineIsTrue() {
        assertTrue(eval("defined(GUARD)"));
        assertTrue(eval("GUARD"));
    }

    @Test
    public void undefinedNameIsZero() {
        assertFalse(eval("MISSING"));
        assertTrue(eval("MISSING == 0"));
    }

    @Test
    public void booleanOperators() {
        assertTrue(eval("defined(DEBUG) && !defined(MISSING)"));
        assertFalse(eval("defined(DEBUG) && defined(MISSING)"));
        assertTrue(eval("defined(MISSING) || DEBUG"));
        assertFalse(eval("ZERO || MISSING"));
    }

    @Test
    public void comparisons() {
        assertTrue(eval("LEVEL >= 3"));
        assertFalse(eval("LEVEL > 3"));
        assertTrue(eval("LEVEL != 0"));
        assertTrue(eval("LEVEL == 3"));
    }

    @Test
    public void parentheses() {
        assertTrue(eval("(defined(MISSING) || DEBUG) && LEVEL >= 3"));
        assertFalse(eval("defined(MISSING) || (DEBUG && ZERO)"));
    }

    /** A macro whose replacement is another macro resolves through. */
    @Test
    public void macroValuesResolve() {
        assertTrue(eval("ALIAS == 3"));
    }

    /** A condition that cannot be parsed hides its branch rather than breaking the parse. */
    @Test
    public void malformedIsFalse() {
        assertFalse(eval("&&"));
        assertFalse(eval(""));
        assertFalse(eval(null));
        assertFalse(eval("1 +"));
    }
}
