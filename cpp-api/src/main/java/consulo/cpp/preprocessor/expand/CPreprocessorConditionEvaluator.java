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
package consulo.cpp.preprocessor.expand;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates the controlling expression of {@code #if} and {@code #elif}.
 * <p>
 * Supports what conditional compilation actually relies on: {@code defined}, the operators of C on integers and
 * {@code ?:}, parentheses, integer literals, macro names and calls of function-like macros - an undefined name is
 * {@code 0}, as in C. Anything it cannot make sense of evaluates to false rather than throwing, so a malformed
 * condition simply hides its branch instead of breaking the parse.
 */
public final class CPreprocessorConditionEvaluator {
    private final String myText;
    private final Map<String, String> myDefines;
    /**
     * The parameters of the function-like macros among the defines, {@code F} of {@code __GLIBC_USE(F)}.
     */
    private final Map<String, List<String>> myParameters;
    private int myPosition;

    private CPreprocessorConditionEvaluator(String text, Map<String, String> defines, Map<String, List<String>> parameters) {
        myText = text;
        myDefines = defines;
        myParameters = parameters;
    }

    public static boolean evaluate(String condition, Map<String, String> defines) {
        return evaluate(condition, defines, Map.of());
    }

    public static boolean evaluate(String condition, Map<String, String> defines, Map<String, List<String>> parameters) {
        if (condition == null) {
            return false;
        }
        // a condition continued over lines, #if a \<newline> || b, is one line
        String trimmed = condition.replace("\\\r\n", " ").replace("\\\n", " ").trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        try {
            CPreprocessorConditionEvaluator evaluator = new CPreprocessorConditionEvaluator(trimmed, defines, parameters);
            long value = evaluator.parseConditional(0);
            evaluator.skipSpaces();
            return evaluator.myPosition >= trimmed.length() && value != 0;
        }
        catch (RuntimeException malformed) {
            return false;
        }
    }

    /**
     * {@code c ? a : b}, then the binary operators of C from the loosest: {@code ||}, {@code &&}, {@code |}, {@code ^},
     * {@code &}, equality, relational, shifts, additive, multiplicative.
     */
    private long parseConditional(int depth) {
        long condition = parseBinary(0, depth);
        if (eat("?")) {
            long whenTrue = parseConditional(depth);
            if (!eat(":")) {
                throw new IllegalStateException(": expected");
            }
            long whenFalse = parseConditional(depth);
            return condition != 0 ? whenTrue : whenFalse;
        }
        return condition;
    }

    private static final String[][] LEVELS = {
        {"||"}, {"&&"}, {"|"}, {"^"}, {"&"}, {"==", "!="}, {"<=", ">=", "<", ">"}, {"<<", ">>"}, {"+", "-"}, {"*", "/", "%"}
    };

    private long parseBinary(int level, int depth) {
        if (level == LEVELS.length) {
            return parseUnary(depth);
        }
        long value = parseBinary(level + 1, depth);
        while (true) {
            String operator = eatOperator(LEVELS[level]);
            if (operator == null) {
                return value;
            }
            long right = parseBinary(level + 1, depth);
            value = apply(operator, value, right);
        }
    }

    private static long apply(String operator, long left, long right) {
        return switch (operator) {
            case "||" -> left != 0 || right != 0 ? 1 : 0;
            case "&&" -> left != 0 && right != 0 ? 1 : 0;
            case "|" -> left | right;
            case "^" -> left ^ right;
            case "&" -> left & right;
            case "==" -> left == right ? 1 : 0;
            case "!=" -> left != right ? 1 : 0;
            case "<=" -> left <= right ? 1 : 0;
            case ">=" -> left >= right ? 1 : 0;
            case "<" -> left < right ? 1 : 0;
            case ">" -> left > right ? 1 : 0;
            case "<<" -> left << right;
            case ">>" -> left >> right;
            case "+" -> left + right;
            case "-" -> left - right;
            case "*" -> left * right;
            // a division by zero makes the condition malformed, as it is an error to the compiler
            case "/" -> left / right;
            case "%" -> left % right;
            default -> throw new IllegalStateException(operator);
        };
    }

    /**
     * One of the operators, not the start of a longer one: {@code |} is not the first half of {@code ||}.
     */
    private @Nullable String eatOperator(String[] operators) {
        skipSpaces();
        for (String operator : operators) {
            if (!myText.startsWith(operator, myPosition)) {
                continue;
            }
            int end = myPosition + operator.length();
            char next = end < myText.length() ? myText.charAt(end) : ' ';
            boolean longer = switch (operator) {
                case "|" -> next == '|';
                case "&" -> next == '&';
                case "<", ">" -> next == '=' || next == operator.charAt(0);
                case "!" -> next == '=';
                default -> false;
            };
            if (!longer) {
                myPosition = end;
                return operator;
            }
        }
        return null;
    }

    private long parseUnary(int depth) {
        skipSpaces();
        if (eatOperator(new String[]{"!"}) != null) {
            return parseUnary(depth) == 0 ? 1 : 0;
        }
        if (eat("~")) {
            return ~parseUnary(depth);
        }
        if (eat("-")) {
            return -parseUnary(depth);
        }
        if (eat("+")) {
            return parseUnary(depth);
        }
        if (eat("(")) {
            long value = parseConditional(depth + 1);
            eat(")");
            return value;
        }

        String word = readWord();
        if (word.isEmpty()) {
            throw new IllegalStateException("not a condition");
        }
        if ("defined".equals(word)) {
            boolean parenthesised = eat("(");
            String name = readWord();
            if (parenthesised) {
                eat(")");
            }
            return myDefines.containsKey(name) ? 1 : 0;
        }
        return valueOf(word, depth);
    }

    /**
     * A macro used in a condition contributes its replacement text, which may itself be a macro; the depth guard
     * keeps a self-referential define from recursing forever.
     */
    private long valueOf(String word, int depth) {
        if (isNumber(word)) {
            return parseNumber(word);
        }
        String replacement = myDefines.get(word);
        List<String> parameters = myParameters.get(word);
        if (parameters != null || replacement == null) {
            skipSpaces();
            if (myPosition < myText.length() && myText.charAt(myPosition) == '(') {
                // F(x): the body with the arguments in place of the parameters; an unknown F(x) is 0
                List<String> arguments = readArguments();
                if (replacement == null || parameters == null) {
                    // __has_feature(x), __has_builtin(x), __has_include(<x>): what a compiler has - answered yes, as
                    // the compilers headers are written for have most of what they ask about
                    return word.startsWith("__has_") ? 1 : 0;
                }
                replacement = substitute(replacement, parameters, arguments);
            }
            else if (replacement == null) {
                return 0;
            }
        }
        String trimmed = replacement.trim();
        if (trimmed.isEmpty()) {
            return 1;
        }
        if (depth > 16) {
            return 0;
        }
        CPreprocessorConditionEvaluator nested = new CPreprocessorConditionEvaluator(trimmed, myDefines, myParameters);
        long value = nested.parseConditional(depth + 1);
        return value;
    }

    /**
     * The arguments of a macro call at the {@code (}, split at the commas outside nested parentheses.
     */
    private List<String> readArguments() {
        List<String> arguments = new ArrayList<>();
        int level = 0;
        int start = myPosition + 1;
        for (; myPosition < myText.length(); myPosition++) {
            char c = myText.charAt(myPosition);
            if (c == '(') {
                level++;
            }
            else if (c == ')' && --level == 0) {
                arguments.add(myText.substring(start, myPosition).trim());
                myPosition++;
                return arguments;
            }
            else if (c == ',' && level == 1) {
                arguments.add(myText.substring(start, myPosition).trim());
                start = myPosition + 1;
            }
        }
        throw new IllegalStateException("unclosed macro call");
    }

    /**
     * A macro body with the arguments in place of its parameters, and {@code a ## b} pasted.
     */
    private static String substitute(String body, List<String> parameters, List<String> arguments) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < body.length() && (Character.isLetterOrDigit(body.charAt(i)) || body.charAt(i) == '_')) {
                    i++;
                }
                String word = body.substring(start, i);
                int index = parameters.indexOf(word);
                result.append(index >= 0 && index < arguments.size() ? arguments.get(index) : word);
            }
            else {
                result.append(c);
                i++;
            }
        }
        return result.toString().replaceAll("\\s*##\\s*", "");
    }

    private static boolean isNumber(String word) {
        return !word.isEmpty() && Character.isDigit(word.charAt(0));
    }

    private static long parseNumber(String word) {
        String digits = word;
        while (!digits.isEmpty() && "uUlL".indexOf(digits.charAt(digits.length() - 1)) >= 0) {
            digits = digits.substring(0, digits.length() - 1);
        }
        try {
            if (digits.startsWith("0x") || digits.startsWith("0X")) {
                return Long.parseLong(digits.substring(2), 16);
            }
            return Long.parseLong(digits);
        }
        catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    private String readWord() {
        skipSpaces();
        int start = myPosition;
        while (myPosition < myText.length()
            && (Character.isLetterOrDigit(myText.charAt(myPosition)) || myText.charAt(myPosition) == '_')) {
            myPosition++;
        }
        return myText.substring(start, myPosition);
    }

    private boolean eat(String token) {
        skipSpaces();
        if (myText.startsWith(token, myPosition)) {
            myPosition += token.length();
            return true;
        }
        return false;
    }

    private void skipSpaces() {
        while (myPosition < myText.length() && Character.isWhitespace(myText.charAt(myPosition))) {
            myPosition++;
        }
    }
}
