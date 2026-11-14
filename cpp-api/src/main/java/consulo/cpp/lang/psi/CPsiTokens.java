/*
 * Copyright 2011 napile
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

package consulo.cpp.lang.psi;

import consulo.cpp.preprocessor.psi.CPreprocessorTokenTypes;
import consulo.language.ast.IElementType;
import consulo.language.ast.StandardTokenTypes;
import consulo.language.ast.TokenSet;

/**
 * @author VISTALL
 * @since 2:42/10.12.2011
 */
public interface CPsiTokens extends StandardTokenTypes, CPreprocessorTokenTypes {
    IElementType IDENTIFIER = new CBaseElementType("IDENTIFIER");
    IElementType NEW_LINE = new CBaseElementType("NEW_LINE");
    // comments
    IElementType C_STYLE_COMMENT = new CBaseElementType("C_STYLE_COMMENT"); // /* comment
    IElementType END_OF_LINE_COMMENT = new CBaseElementType("END_OF_LINE_COMMENT");     // // comment

    // keywords
    IElementType NAMESPACE_KEYWORD = new CBaseElementType("NAMESPACE_KEYWORD"); // namespace
    IElementType VOID_KEYWORD = new CBaseElementType("VOID_KEYWORD"); // void
    IElementType CASE_KEYWORD = new CBaseElementType("CASE_KEYWORD"); // case
    IElementType CONTINUE_KEYWORD = new CBaseElementType("CONTINUE_KEYWORD");
    IElementType DEFAULT_KEYWORD = new CBaseElementType("DEFAULT_KEYWORD");
    IElementType DO_KEYWORD = new CBaseElementType("DO_KEYWORD");
    IElementType CONST_KEYWORD = new CBaseElementType("CONST_KEYWORD");
    IElementType SIGNED_KEYWORD = new CBaseElementType("SIGNED_KEYWORD");   //signed
    IElementType UNSIGNED_KEYWORD = new CBaseElementType("UNSIGNED_KEYWORD");
    IElementType ELSE_KEYWORD = new CBaseElementType("ELSE_KEYWORD");
    IElementType BREAK_KEYWORD = new CBaseElementType("BREAK_KEYWORD");
    IElementType RETURN_KEYWORD = new CBaseElementType("RETURN_KEYWORD");
    IElementType WHILE_KEYWORD = new CBaseElementType("WHILE_KEYWORD");
    IElementType FOR_KEYWORD = new CBaseElementType("FOR_KEYWORD");
    IElementType GOTO_KEYWORD = new CBaseElementType("GOTO_KEYWORD");
    IElementType IF_KEYWORD = new CBaseElementType("IF_KEYWORD");
    IElementType NEW_KEYWORD = new CBaseElementType("NEW_KEYWORD");
    IElementType SWITCH_KEYWORD = new CBaseElementType("SWITCH_KEYWORD");
    IElementType PUBLIC_KEYWORD = new CBaseElementType("PUBLIC_KEYWORD");
    IElementType PRIVATE_KEYWORD = new CBaseElementType("PRIVATE_KEYWORD");
    IElementType STATIC_KEYWORD = new CBaseElementType("STATIC_KEYWORD");
    IElementType EXPLICIT_KEYWORD = new CBaseElementType("EXPLICIT_KEYWORD");  //explicit
    IElementType VIRTUAL_KEYWORD = new CBaseElementType("VIRTUAL_KEYWORD");  //virtual
    IElementType TYPEDEF_KEYWORD = new CBaseElementType("TYPEDEF_KEYWORD"); // typedef
    IElementType EXTERN_KEYWORD = new CBaseElementType("EXTERN_KEYWORD"); // extern
    IElementType ENUM_KEYWORD = new CBaseElementType("ENUM_KEYWORD"); // enum
    IElementType CLASS_KEYWORD = new CBaseElementType("CLASS_KEYWORD"); // class
    IElementType STRUCT_KEYWORD = new CBaseElementType("STRUCT_KEYWORD"); // struct
    IElementType UNION_KEYWORD = new CBaseElementType("UNION_KEYWORD"); // union
    IElementType SIZEOF_KEYWORD = new CBaseElementType("SIZEOF_KEYWORD"); // sizeof

    // C storage classes, qualifiers and specifiers
    IElementType INLINE_KEYWORD = new CBaseElementType("INLINE_KEYWORD"); // inline, __inline, __inline__
    IElementType VOLATILE_KEYWORD = new CBaseElementType("VOLATILE_KEYWORD"); // volatile, __volatile__
    IElementType REGISTER_KEYWORD = new CBaseElementType("REGISTER_KEYWORD"); // register
    IElementType AUTO_KEYWORD = new CBaseElementType("AUTO_KEYWORD"); // auto
    IElementType RESTRICT_KEYWORD = new CBaseElementType("RESTRICT_KEYWORD"); // restrict, __restrict, __restrict__
    IElementType NORETURN_KEYWORD = new CBaseElementType("NORETURN_KEYWORD"); // _Noreturn
    IElementType THREAD_LOCAL_KEYWORD = new CBaseElementType("THREAD_LOCAL_KEYWORD"); // thread_local, _Thread_local, __thread
    IElementType ATOMIC_KEYWORD = new CBaseElementType("ATOMIC_KEYWORD"); // _Atomic
    IElementType COMPLEX_KEYWORD = new CBaseElementType("COMPLEX_KEYWORD"); // _Complex, __complex__
    IElementType STATIC_ASSERT_KEYWORD = new CBaseElementType("STATIC_ASSERT_KEYWORD"); // static_assert, _Static_assert
    IElementType ALIGNOF_KEYWORD = new CBaseElementType("ALIGNOF_KEYWORD"); // alignof, _Alignof, __alignof__
    IElementType ALIGNAS_KEYWORD = new CBaseElementType("ALIGNAS_KEYWORD"); // alignas, _Alignas
    IElementType TYPEOF_KEYWORD = new CBaseElementType("TYPEOF_KEYWORD"); // typeof, __typeof__
    IElementType GENERIC_KEYWORD = new CBaseElementType("GENERIC_KEYWORD"); // _Generic
    IElementType CONSTEXPR_KEYWORD = new CBaseElementType("CONSTEXPR_KEYWORD"); // constexpr
    IElementType NULLPTR_KEYWORD = new CBaseElementType("NULLPTR_KEYWORD"); // nullptr

    // compiler extensions
    IElementType ATTRIBUTE_KEYWORD = new CBaseElementType("ATTRIBUTE_KEYWORD"); // __attribute__
    IElementType DECLSPEC_KEYWORD = new CBaseElementType("DECLSPEC_KEYWORD"); // __declspec
    IElementType EXTENSION_KEYWORD = new CBaseElementType("EXTENSION_KEYWORD"); // __extension__
    /**
     * A dialect's address space - {@code device}, {@code constant}, {@code threadgroup} of Metal: a qualifier, accepted
     * where {@code const} is, see {@link consulo.cpp.lang.version.CLanguageVersion#getDialectKeywords()}.
     */
    IElementType ADDRESS_SPACE_KEYWORD = new CBaseElementType("ADDRESS_SPACE_KEYWORD");
    /**
     * A dialect's function qualifier - {@code kernel}, {@code vertex}, {@code fragment} of Metal: accepted where
     * {@code inline} is.
     */
    IElementType FUNCTION_QUALIFIER_KEYWORD = new CBaseElementType("FUNCTION_QUALIFIER_KEYWORD");
    IElementType ASM_KEYWORD = new CBaseElementType("ASM_KEYWORD"); // asm, __asm__

    // C++
    IElementType PROTECTED_KEYWORD = new CBaseElementType("PROTECTED_KEYWORD"); // protected
    IElementType FRIEND_KEYWORD = new CBaseElementType("FRIEND_KEYWORD"); // friend
    IElementType MUTABLE_KEYWORD = new CBaseElementType("MUTABLE_KEYWORD"); // mutable
    IElementType THIS_KEYWORD = new CBaseElementType("THIS_KEYWORD"); // this
    IElementType OPERATOR_KEYWORD = new CBaseElementType("OPERATOR_KEYWORD"); // operator
    IElementType DELETE_KEYWORD = new CBaseElementType("DELETE_KEYWORD"); // delete
    IElementType TEMPLATE_KEYWORD = new CBaseElementType("TEMPLATE_KEYWORD"); // template
    IElementType TYPENAME_KEYWORD = new CBaseElementType("TYPENAME_KEYWORD"); // typename
    IElementType USING_KEYWORD = new CBaseElementType("USING_KEYWORD"); // using
    IElementType TRY_KEYWORD = new CBaseElementType("TRY_KEYWORD"); // try
    IElementType CATCH_KEYWORD = new CBaseElementType("CATCH_KEYWORD"); // catch
    IElementType THROW_KEYWORD = new CBaseElementType("THROW_KEYWORD"); // throw
    IElementType CAST_KEYWORD = new CBaseElementType("CAST_KEYWORD"); // static_cast, dynamic_cast, const_cast, reinterpret_cast
    IElementType TYPEID_KEYWORD = new CBaseElementType("TYPEID_KEYWORD"); // typeid
    IElementType DECLTYPE_KEYWORD = new CBaseElementType("DECLTYPE_KEYWORD"); // decltype
    /**
     * The contextual words of C++20 constraints, lexed as identifiers and made keywords by the parser where they are.
     */
    IElementType CONCEPT_KEYWORD = new CBaseElementType("CONCEPT_KEYWORD"); // concept
    IElementType REQUIRES_KEYWORD = new CBaseElementType("REQUIRES_KEYWORD"); // requires
    IElementType NOEXCEPT_KEYWORD = new CBaseElementType("NOEXCEPT_KEYWORD"); // noexcept

    // types
    IElementType CHAR_KEYWORD = new CBaseElementType("CHAR_KEYWORD"); // char
    IElementType BOOL_KEYWORD = new CBaseElementType("BOOL_KEYWORD"); // bool
    IElementType SHORT_KEYWORD = new CBaseElementType("SHORT_KEYWORD"); // short
    IElementType INT_KEYWORD = new CBaseElementType("INT_KEYWORD"); // int
    IElementType LONG_KEYWORD = new CBaseElementType("LONG_KEYWORD"); // long
    IElementType __INT64_KEYWORD = new CBaseElementType("__INT64_KEYWORD"); // __int64
    IElementType FLOAT_KEYWORD = new CBaseElementType("FLOAT_KEYWORD"); // float
    IElementType DOUBLE_KEYWORD = new CBaseElementType("DOUBLE_KEYWORD"); // double
    IElementType WCHAR_KEYWORD = new CBaseElementType("WCHAR_KEYWORD"); // wchar_t (a keyword of C++ only)
    IElementType CHAR16_KEYWORD = new CBaseElementType("CHAR16_KEYWORD"); // char16_t
    IElementType CHAR32_KEYWORD = new CBaseElementType("CHAR32_KEYWORD"); // char32_t

    // data
    IElementType INTEGER_LITERAL = new CBaseElementType("INTEGER_LITERAL");
    IElementType LONG_LITERAL = new CBaseElementType("LONG_LITERAL");
    IElementType FLOAT_LITERAL = new CBaseElementType("FLOAT_LITERAL");
    IElementType DOUBLE_LITERAL = new CBaseElementType("DOUBLE_LITERAL");
    IElementType CHARACTER_LITERAL = new CBaseElementType("CHARACTER_LITERAL");
    IElementType STRING_LITERAL = new CBaseElementType("STRING_LITERAL");
    IElementType STRING_INCLUDE_LITERAL = new CBaseElementType("STRING_INCLUDE_LITERAL");
    IElementType BOOL_LITERAL = new CBaseElementType("BOOL_LITERAL");

    //
    IElementType LPARENTH = new CBaseElementType("LPARENTH");
    IElementType RPARENTH = new CBaseElementType("RPARENTH");
    IElementType LBRACE = new CBaseElementType("LBRACE");
    IElementType RBRACE = new CBaseElementType("RBRACE");
    IElementType LBRACKET = new CBaseElementType("LBRACKET");
    IElementType RBRACKET = new CBaseElementType("RBRACKET");
    IElementType SEMICOLON = new CBaseElementType("SEMICOLON");
    IElementType COMMA = new CBaseElementType("COMMA");
    IElementType DOT = new CBaseElementType("DOT");
    IElementType ELLIPSIS = new CBaseElementType("ELLIPSIS");
    IElementType AT = new CBaseElementType("AT");

    IElementType EQ = new CBaseElementType("EQ");
    IElementType GT = new CBaseElementType("GT");
    IElementType LT = new CBaseElementType("LT");
    IElementType EXCL = new CBaseElementType("EXCL");
    IElementType TILDE = new CBaseElementType("TILDE");
    IElementType QUEST = new CBaseElementType("QUEST");
    IElementType COLON = new CBaseElementType("COLON");
    IElementType DOUBLE_COLON = new CBaseElementType("DOUBLE_COLON");
    IElementType PLUS = new CBaseElementType("PLUS");
    IElementType MINUS = new CBaseElementType("MINUS");
    IElementType ASTERISK = new CBaseElementType("ASTERISK");
    IElementType DIV = new CBaseElementType("DIV");
    IElementType NEXT_LINE = new CBaseElementType("NEXT_LINE");  // \
    IElementType AND = new CBaseElementType("AND");
    IElementType OR = new CBaseElementType("OR");
    IElementType XOR = new CBaseElementType("XOR");
    IElementType PERC = new CBaseElementType("PERC");
    IElementType ARROW = new CBaseElementType("ARROW");
    IElementType SHARP = new CBaseElementType("SHARP"); // # - stringizing, inside macro bodies
    IElementType SHARPSHARP = new CBaseElementType("SHARPSHARP"); // ## - token pasting, inside macro bodies

    IElementType EQEQ = new CBaseElementType("EQEQ");
    IElementType LE = new CBaseElementType("LE");
    IElementType GE = new CBaseElementType("GE");
    IElementType NE = new CBaseElementType("NE");
    IElementType ANDAND = new CBaseElementType("ANDAND");
    IElementType OROR = new CBaseElementType("OROR");
    IElementType PLUSPLUS = new CBaseElementType("PLUSPLUS");
    IElementType MINUSMINUS = new CBaseElementType("MINUSMINUS");
    IElementType LTLT = new CBaseElementType("LTLT");
    IElementType GTGT = new CBaseElementType("GTGT");
    IElementType PLUSEQ = new CBaseElementType("PLUSEQ");
    IElementType MINUSEQ = new CBaseElementType("MINUSEQ");
    IElementType ASTERISKEQ = new CBaseElementType("ASTERISKEQ");
    IElementType DIVEQ = new CBaseElementType("DIVEQ");
    IElementType ANDEQ = new CBaseElementType("ANDEQ");
    IElementType OREQ = new CBaseElementType("OREQ");
    IElementType XOREQ = new CBaseElementType("XOREQ");
    IElementType PERCEQ = new CBaseElementType("PERCEQ");
    IElementType LTLTEQ = new CBaseElementType("LTLTEQ");
    IElementType GTGTEQ = new CBaseElementType("GTGTEQ");

    TokenSet ACCESS_MODIFIERS = TokenSet.create(PUBLIC_KEYWORD, PRIVATE_KEYWORD, PROTECTED_KEYWORD);

    /**
     * Storage classes, qualifiers and function specifiers - the part of a declaration's specifiers which is not the
     * type itself. They may come before or after the type, in any order.
     */
    TokenSet MODIFIERS = TokenSet.create(
        STATIC_KEYWORD, EXTERN_KEYWORD, REGISTER_KEYWORD, AUTO_KEYWORD, THREAD_LOCAL_KEYWORD, MUTABLE_KEYWORD,
        CONST_KEYWORD, VOLATILE_KEYWORD, RESTRICT_KEYWORD, ATOMIC_KEYWORD,
        INLINE_KEYWORD, NORETURN_KEYWORD, VIRTUAL_KEYWORD, EXPLICIT_KEYWORD, FRIEND_KEYWORD, CONSTEXPR_KEYWORD,
        EXTENSION_KEYWORD, ADDRESS_SPACE_KEYWORD, FUNCTION_QUALIFIER_KEYWORD
    );

    /**
     * Qualifiers which may follow a {@code *} of a declarator.
     */
    TokenSet POINTER_QUALIFIERS = TokenSet.create(CONST_KEYWORD, VOLATILE_KEYWORD, RESTRICT_KEYWORD, ATOMIC_KEYWORD, ADDRESS_SPACE_KEYWORD);

    TokenSet WHITE_SPACE_SET = TokenSet.create(WHITE_SPACE);
    TokenSet STRING_LITERAL_SET = TokenSet.create(STRING_LITERAL, CHARACTER_LITERAL);
    TokenSet COMMENT_SET = TokenSet.create(C_STYLE_COMMENT, END_OF_LINE_COMMENT);
    TokenSet LITERAL_EXPRESSION_SET = TokenSet.create(STRING_LITERAL, CHARACTER_LITERAL, INTEGER_LITERAL, LONG_LITERAL, DOUBLE_LITERAL, FLOAT_LITERAL, BOOL_LITERAL, NULLPTR_KEYWORD);

    TokenSet EQUALITY_OPERATIONS = TokenSet.create(EQEQ, NE);
    TokenSet ASSIGNMENT_OPERATIONS = TokenSet.create(EQ, PLUSEQ, MINUSEQ, ASTERISKEQ, PERCEQ, LTLTEQ, GTGTEQ, ANDEQ, OREQ, XOREQ, DIVEQ);
    /**
     * Unary prefix operators — includes address-of ({@code &}) and dereference ({@code *}).
     */
    TokenSet UNARY_OPERATIONS = TokenSet.create(PLUS, MINUS, PLUSPLUS, MINUSMINUS, TILDE, EXCL, AND, ASTERISK);
    TokenSet SHIFT_OPERATIONS = TokenSet.create(LTLT, GTGT);
    TokenSet ADDITIVE_OPERATIONS = TokenSet.create(PLUS, MINUS);
    TokenSet MULTIPLICATIVE_OPERATIONS = TokenSet.create(ASTERISK, DIV, PERC);
    TokenSet RELATIONAL_OPERATIONS = TokenSet.create(LT, GT, LE, GE);

    /**
     * Type keywords which combine with each other - {@code unsigned long long int}.
     */
    TokenSet TYPES = TokenSet.create(
        INT_KEYWORD, __INT64_KEYWORD, BOOL_KEYWORD, CHAR_KEYWORD, WCHAR_KEYWORD, CHAR16_KEYWORD, CHAR32_KEYWORD,
        SHORT_KEYWORD, LONG_KEYWORD, FLOAT_KEYWORD, DOUBLE_KEYWORD, VOID_KEYWORD, SIGNED_KEYWORD, UNSIGNED_KEYWORD,
        COMPLEX_KEYWORD
    );

    /**
     * Aggregate-type starters: {@code struct} and {@code union}.
     */
    TokenSet AGGREGATE_TYPES = TokenSet.create(STRUCT_KEYWORD, UNION_KEYWORD);

    TokenSet OPERATION_SET = TokenSet.create
        (
            EQ, GT, LT, EXCL, TILDE, QUEST, COLON, PLUS, MINUS, ASTERISK, DIV, AND, OR, XOR,
            PERC, EQEQ, LE, GE, NE, ANDAND, OROR, PLUSPLUS, MINUSMINUS, LTLT, GTGT,
            PLUSEQ, MINUSEQ, ASTERISKEQ, DIVEQ, ANDEQ, OREQ, XOREQ, PERCEQ, LTLTEQ, GTGTEQ
        );

    TokenSet KEYWORD_SET = TokenSet.create
        (
            BREAK_KEYWORD, CASE_KEYWORD, CONTINUE_KEYWORD,
            DEFAULT_KEYWORD, DO_KEYWORD, ELSE_KEYWORD,
            FOR_KEYWORD, GOTO_KEYWORD, IF_KEYWORD,
            NEW_KEYWORD, PUBLIC_KEYWORD, RETURN_KEYWORD,
            SWITCH_KEYWORD, VOID_KEYWORD, WHILE_KEYWORD,
            CONST_KEYWORD, TYPEDEF_KEYWORD, SIGNED_KEYWORD, UNSIGNED_KEYWORD,
            CHAR_KEYWORD, SHORT_KEYWORD, INT_KEYWORD, LONG_KEYWORD, __INT64_KEYWORD,
            FLOAT_KEYWORD, DOUBLE_KEYWORD, WCHAR_KEYWORD, CHAR16_KEYWORD, CHAR32_KEYWORD, COMPLEX_KEYWORD,
            S_INCLUDE_KEYWORD, S_DEFINE_KEYWORD, S_IFDEF_KEYWORD, S_IFNDEF_KEYWORD, S_ELSE_KEYWORD, S_ENDIF_KEYWORD,
            S_IF_KEYWORD, S_ELIF_KEYWORD, S_UNDEF_KEYWORD, S_PRAGMA_KEYWORD,
            ENUM_KEYWORD, EXTERN_KEYWORD, BOOL_KEYWORD,
            PRIVATE_KEYWORD, PROTECTED_KEYWORD, STATIC_KEYWORD, CLASS_KEYWORD, NAMESPACE_KEYWORD,
            VIRTUAL_KEYWORD, EXPLICIT_KEYWORD, FRIEND_KEYWORD, MUTABLE_KEYWORD,
            STRUCT_KEYWORD, UNION_KEYWORD, SIZEOF_KEYWORD,
            INLINE_KEYWORD, VOLATILE_KEYWORD, REGISTER_KEYWORD, AUTO_KEYWORD, RESTRICT_KEYWORD, NORETURN_KEYWORD,
            ADDRESS_SPACE_KEYWORD, FUNCTION_QUALIFIER_KEYWORD,
            THREAD_LOCAL_KEYWORD, ATOMIC_KEYWORD, STATIC_ASSERT_KEYWORD, ALIGNOF_KEYWORD, ALIGNAS_KEYWORD,
            TYPEOF_KEYWORD, GENERIC_KEYWORD, CONSTEXPR_KEYWORD, NULLPTR_KEYWORD,
            ATTRIBUTE_KEYWORD, DECLSPEC_KEYWORD, EXTENSION_KEYWORD, ASM_KEYWORD,
            THIS_KEYWORD, OPERATOR_KEYWORD, DELETE_KEYWORD, TEMPLATE_KEYWORD, TYPENAME_KEYWORD, USING_KEYWORD,
            TRY_KEYWORD, CATCH_KEYWORD, THROW_KEYWORD, CAST_KEYWORD, TYPEID_KEYWORD, DECLTYPE_KEYWORD, NOEXCEPT_KEYWORD
        );
}
