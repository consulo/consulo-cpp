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
package consulo.cpp.lexer;

import consulo.cpp.lang.lexer.CHighlightingLexer;
import consulo.cpp.lang.version.Cpp17LanguageVersion;
import consulo.language.lexer.Lexer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The tokens the editor colors directives with, and that a restart at any token with its state lexes the rest the same,
 * as the editor restarts the lexer after an edit.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public class CHighlightingLexerTest {
    private static final String TEXT = """
        #include <stdio.h>
        #  include "util.h" // the helpers
        #define LIMIT(x) \\
            ((x) * 10)
        #if defined(LIMIT) && VERSION > 2
        int a = 1; // #not a directive
        #else
        #error unsupported
        #endif
        int b = a # 2;
        """;

    @Test
    public void testDirectives() {
        List<String> tokens = lex(TEXT, 0, 0);
        List<String> significant = new ArrayList<>();
        for (String token : tokens) {
            if (!token.startsWith("WHITE_SPACE") && !token.startsWith("NEW_LINE")) {
                significant.add(token);
            }
        }
        assertEquals(List.of(
            "S_INCLUDE_KEYWORD '#include'", "STRING_INCLUDE_LITERAL '<stdio.h>'",
            "S_INCLUDE_KEYWORD '#  include'", "STRING_INCLUDE_LITERAL '\"util.h\"'", "END_OF_LINE_COMMENT '// the helpers'",
            "S_DEFINE_KEYWORD '#define'", "IDENTIFIER 'LIMIT'", "LPARENTH '('", "IDENTIFIER 'x'", "RPARENTH ')'",
            "LPARENTH '('", "LPARENTH '('", "IDENTIFIER 'x'", "RPARENTH ')'", "ASTERISK '*'", "INTEGER_LITERAL '10'", "RPARENTH ')'",
            "S_IF_KEYWORD '#if'", "S_DIRECTIVE_KEYWORD 'defined'", "LPARENTH '('", "IDENTIFIER 'LIMIT'", "RPARENTH ')'", "ANDAND '&&'",
            "IDENTIFIER 'VERSION'", "GT '>'", "INTEGER_LITERAL '2'",
            "INT_KEYWORD 'int'", "IDENTIFIER 'a'", "EQ '='", "INTEGER_LITERAL '1'", "SEMICOLON ';'", "END_OF_LINE_COMMENT '// #not a directive'",
            "S_ELSE_KEYWORD '#else'",
            "S_DIRECTIVE_KEYWORD '#error'", "IDENTIFIER 'unsupported'",
            "S_ENDIF_KEYWORD '#endif'",
            "INT_KEYWORD 'int'", "IDENTIFIER 'b'", "EQ '='", "IDENTIFIER 'a'", "SHARP '#'", "INTEGER_LITERAL '2'", "SEMICOLON ';'"
        ), significant.stream().filter(token -> !token.startsWith("BAD_CHARACTER")).toList());
    }

    @Test
    public void testRestartAtAnyToken() {
        Lexer lexer = lexer();
        lexer.start(TEXT, 0, TEXT.length(), 0);
        List<int[]> starts = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            starts.add(new int[]{lexer.getTokenStart(), lexer.getState()});
            lexer.advance();
        }
        List<String> all = lex(TEXT, 0, 0);
        for (int i = 0; i < starts.size(); i++) {
            List<String> restarted = lex(TEXT, starts.get(i)[0], starts.get(i)[1]);
            assertEquals(all.subList(i, all.size()), restarted, "restarted at offset " + starts.get(i)[0]);
        }
    }

    private static List<String> lex(String text, int start, int state) {
        Lexer lexer = lexer();
        lexer.start(text, start, text.length(), state);
        List<String> tokens = new ArrayList<>();
        int previousEnd = start;
        while (lexer.getTokenType() != null) {
            assertEquals(previousEnd, lexer.getTokenStart(), "tokens must be contiguous");
            tokens.add(lexer.getTokenType() + " '" + text.substring(lexer.getTokenStart(), lexer.getTokenEnd()) + "'");
            previousEnd = lexer.getTokenEnd();
            lexer.advance();
        }
        assertEquals(text.length(), previousEnd, "the whole text must be lexed");
        return tokens;
    }

    private static Lexer lexer() {
        return new CHighlightingLexer(new Cpp17LanguageVersion().createLexer());
    }
}
