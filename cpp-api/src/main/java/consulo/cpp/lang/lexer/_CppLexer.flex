/* It's an automatically generated code. Do not modify it. */
package consulo.cpp.lang.lexer;

import consulo.language.ast.IElementType;
import consulo.language.lexer.FlexLexer;
import consulo.cpp.lang.psi.CPsiTokens;
import consulo.cpp.lang.version.CLanguageFeature;
import consulo.cpp.lang.version.CLanguageVersion;

@SuppressWarnings({"ALL"})
%%

%{
  private CLanguageVersion myVersion;

  // start and delimiter of the raw string literal being lexed
  private int myRawStringStart;
  private String myRawStringDelimiter;

  public _CppLexer(CLanguageVersion version) {
    this((java.io.Reader)null);
    myVersion = version;
  }

  private IElementType keyword(IElementType keyword) {
    return myVersion.keywordOrIdentifier(keyword);
  }

  public void goTo(int offset) {
    zzCurrentPos = zzMarkedPos = zzStartRead = offset;
    zzAtEOF = offset < zzEndRead;
  }
%}

%unicode
%public
%class _CppLexer
%implements FlexLexer
%function advance
%type IElementType
%eof{  return;
%eof}

%xstate RAW_STRING

WHITE_SPACE_CHAR=[\ \r\t\f\n]
// a backslash at the end of a line joins it with the next one
LINE_CONTINUATION=\\[\ \t]*\r?\n

IDENTIFIER=[:jletter:] [:jletterdigit:]*

C_STYLE_COMMENT="/*""*"*("/"|([^"/""*"]{COMMENT_TAIL}))?
COMMENT_TAIL=([^"*"]*("*"+[^"*""/"])?)*("*"+"/")?
END_OF_LINE_COMMENT="/""/"[^\r\n]*

// ' separates digits since C++14 and C23
DECIMAL_DIGITS=[0-9]([0-9']*[0-9])?
HEX_DIGITS=[0-9a-fA-F]([0-9a-fA-F']*[0-9a-fA-F])?
BINARY_DIGITS=[01]([01']*[01])?

// u, l, ull, z, f16, the imaginary i and il, and a C++ user-defined suffix, 10_km
LITERAL_SUFFIX=[a-zA-Z_][a-zA-Z0-9_]*
INTEGER_LITERAL=([1-9]([0-9']*[0-9])?|0([0-7']*[0-7])?|0[xX]{HEX_DIGITS}|0[bB]{BINARY_DIGITS}){LITERAL_SUFFIX}?

EXPONENT=[eE][+-]?[0-9]+
DECIMAL_FLOAT=({DECIMAL_DIGITS}"."{DECIMAL_DIGITS}?{EXPONENT}?)|("."{DECIMAL_DIGITS}{EXPONENT}?)|({DECIMAL_DIGITS}{EXPONENT})
HEX_FLOAT=0[xX]({HEX_DIGITS}?"."{HEX_DIGITS}|{HEX_DIGITS}"."?)[pP][+-]?[0-9]+
FLOAT_LITERAL=({DECIMAL_FLOAT}|{HEX_FLOAT})[fF]
DOUBLE_LITERAL=({DECIMAL_FLOAT}|{HEX_FLOAT}){LITERAL_SUFFIX}?

ENCODING_PREFIX=L|u8|u|U
ESCAPE_SEQUENCE=\\[^\r\n]
CHARACTER_LITERAL={ENCODING_PREFIX}?"'"([^\\\'\r\n]|{ESCAPE_SEQUENCE})*("'"|\\)?
STRING_LITERAL={ENCODING_PREFIX}?\"([^\\\"\r\n]|{ESCAPE_SEQUENCE}|{LINE_CONTINUATION})*(\"|\\)?

RAW_STRING_DELIMITER=[^()\\\ \t\r\n\"]{0,16}
RAW_STRING_START={ENCODING_PREFIX}?R\"{RAW_STRING_DELIMITER}\(

%%

<YYINITIAL> {WHITE_SPACE_CHAR}+ { return CPsiTokens.WHITE_SPACE; }
<YYINITIAL> {LINE_CONTINUATION} { return CPsiTokens.WHITE_SPACE; }

<YYINITIAL> {C_STYLE_COMMENT} { return CPsiTokens.C_STYLE_COMMENT; }
<YYINITIAL> {END_OF_LINE_COMMENT} { return CPsiTokens.END_OF_LINE_COMMENT; }

// a float first: 1e3 reads as much as an integer 1 with a suffix e3 would
<YYINITIAL> {FLOAT_LITERAL} { return CPsiTokens.FLOAT_LITERAL; }
<YYINITIAL> {DOUBLE_LITERAL} { return CPsiTokens.DOUBLE_LITERAL; }
<YYINITIAL> {INTEGER_LITERAL} { return CPsiTokens.INTEGER_LITERAL; }

<YYINITIAL> {CHARACTER_LITERAL} { return CPsiTokens.CHARACTER_LITERAL; }
<YYINITIAL> {STRING_LITERAL} { return CPsiTokens.STRING_LITERAL; }

<YYINITIAL> {RAW_STRING_START} {
    String text = yytext().toString();
    int quote = text.indexOf('"');
    if (!myVersion.hasFeature(CLanguageFeature.RAW_STRINGS)) {
      // before C++11 the prefix is a plain name followed by an ordinary string
      yypushback(yylength() - quote);
      return CPsiTokens.IDENTIFIER;
    }
    myRawStringStart = getTokenStart();
    myRawStringDelimiter = text.substring(quote + 1, text.length() - 1);
    yybegin(RAW_STRING);
  }

<RAW_STRING> \){RAW_STRING_DELIMITER}\" {
    String text = yytext().toString();
    if (text.substring(1, text.length() - 1).equals(myRawStringDelimiter)) {
      yybegin(YYINITIAL);
      zzStartRead = myRawStringStart;
      return CPsiTokens.STRING_LITERAL;
    }
  }
<RAW_STRING> [^)]+|\) { }
<RAW_STRING> <<EOF>> {
    yybegin(YYINITIAL);
    zzStartRead = myRawStringStart;
    return CPsiTokens.STRING_LITERAL;
  }

<YYINITIAL> "break" { return keyword(CPsiTokens.BREAK_KEYWORD); }
<YYINITIAL> "case" { return keyword(CPsiTokens.CASE_KEYWORD); }
<YYINITIAL> "continue" { return keyword(CPsiTokens.CONTINUE_KEYWORD); }
<YYINITIAL> "default" { return keyword(CPsiTokens.DEFAULT_KEYWORD); }
<YYINITIAL> "do" { return keyword(CPsiTokens.DO_KEYWORD); }
<YYINITIAL> "else" { return keyword(CPsiTokens.ELSE_KEYWORD); }
<YYINITIAL> "for" { return keyword(CPsiTokens.FOR_KEYWORD); }
<YYINITIAL> "goto" { return keyword(CPsiTokens.GOTO_KEYWORD); }
<YYINITIAL> "if" { return keyword(CPsiTokens.IF_KEYWORD); }
<YYINITIAL> "switch" { return keyword(CPsiTokens.SWITCH_KEYWORD); }
<YYINITIAL> "return" { return keyword(CPsiTokens.RETURN_KEYWORD); }
<YYINITIAL> "while" { return keyword(CPsiTokens.WHILE_KEYWORD); }
<YYINITIAL> "typedef" { return keyword(CPsiTokens.TYPEDEF_KEYWORD); }
<YYINITIAL> "enum" { return keyword(CPsiTokens.ENUM_KEYWORD); }
<YYINITIAL> "struct" { return keyword(CPsiTokens.STRUCT_KEYWORD); }
<YYINITIAL> "union" { return keyword(CPsiTokens.UNION_KEYWORD); }
<YYINITIAL> "sizeof" { return keyword(CPsiTokens.SIZEOF_KEYWORD); }

<YYINITIAL> "static" { return keyword(CPsiTokens.STATIC_KEYWORD); }
<YYINITIAL> "extern" { return keyword(CPsiTokens.EXTERN_KEYWORD); }
<YYINITIAL> "register" { return keyword(CPsiTokens.REGISTER_KEYWORD); }
<YYINITIAL> "auto" { return keyword(CPsiTokens.AUTO_KEYWORD); }
<YYINITIAL> "const"|"__const"|"__const__" { return keyword(CPsiTokens.CONST_KEYWORD); }
<YYINITIAL> "volatile"|"__volatile"|"__volatile__" { return keyword(CPsiTokens.VOLATILE_KEYWORD); }
<YYINITIAL> "restrict" { return keyword(CPsiTokens.RESTRICT_KEYWORD); }
<YYINITIAL> "__restrict"|"__restrict__" { return CPsiTokens.RESTRICT_KEYWORD; }
<YYINITIAL> "inline" { return keyword(CPsiTokens.INLINE_KEYWORD); }
<YYINITIAL> "__inline"|"__inline__"|"__forceinline" { return CPsiTokens.INLINE_KEYWORD; }
<YYINITIAL> "_Noreturn" { return CPsiTokens.NORETURN_KEYWORD; }
<YYINITIAL> "thread_local" { return keyword(CPsiTokens.THREAD_LOCAL_KEYWORD); }
<YYINITIAL> "_Thread_local"|"__thread" { return CPsiTokens.THREAD_LOCAL_KEYWORD; }
<YYINITIAL> "_Atomic" { return CPsiTokens.ATOMIC_KEYWORD; }
<YYINITIAL> "static_assert" { return keyword(CPsiTokens.STATIC_ASSERT_KEYWORD); }
<YYINITIAL> "_Static_assert" { return CPsiTokens.STATIC_ASSERT_KEYWORD; }
<YYINITIAL> "alignof" { return keyword(CPsiTokens.ALIGNOF_KEYWORD); }
<YYINITIAL> "_Alignof"|"__alignof"|"__alignof__" { return CPsiTokens.ALIGNOF_KEYWORD; }
<YYINITIAL> "alignas" { return keyword(CPsiTokens.ALIGNAS_KEYWORD); }
<YYINITIAL> "_Alignas" { return CPsiTokens.ALIGNAS_KEYWORD; }
<YYINITIAL> "typeof" { return keyword(CPsiTokens.TYPEOF_KEYWORD); }
<YYINITIAL> "__typeof"|"__typeof__" { return CPsiTokens.TYPEOF_KEYWORD; }
<YYINITIAL> "_Generic" { return CPsiTokens.GENERIC_KEYWORD; }
<YYINITIAL> "constexpr" { return keyword(CPsiTokens.CONSTEXPR_KEYWORD); }
<YYINITIAL> "nullptr" { return keyword(CPsiTokens.NULLPTR_KEYWORD); }

<YYINITIAL> "__attribute__"|"__attribute" { return CPsiTokens.ATTRIBUTE_KEYWORD; }
<YYINITIAL> "__declspec" { return CPsiTokens.DECLSPEC_KEYWORD; }
<YYINITIAL> "__extension__" { return CPsiTokens.EXTENSION_KEYWORD; }
<YYINITIAL> "asm"|"__asm"|"__asm__" { return CPsiTokens.ASM_KEYWORD; }

<YYINITIAL> "void" { return keyword(CPsiTokens.VOID_KEYWORD); }
<YYINITIAL> "char" { return keyword(CPsiTokens.CHAR_KEYWORD); }
<YYINITIAL> "short" { return keyword(CPsiTokens.SHORT_KEYWORD); }
<YYINITIAL> "int" { return keyword(CPsiTokens.INT_KEYWORD); }
<YYINITIAL> "long" { return keyword(CPsiTokens.LONG_KEYWORD); }
<YYINITIAL> "__int64" { return keyword(CPsiTokens.__INT64_KEYWORD); }
<YYINITIAL> "float" { return keyword(CPsiTokens.FLOAT_KEYWORD); }
<YYINITIAL> "double" { return keyword(CPsiTokens.DOUBLE_KEYWORD); }
<YYINITIAL> "signed"|"__signed"|"__signed__" { return keyword(CPsiTokens.SIGNED_KEYWORD); }
<YYINITIAL> "unsigned" { return keyword(CPsiTokens.UNSIGNED_KEYWORD); }
<YYINITIAL> "bool" { return keyword(CPsiTokens.BOOL_KEYWORD); }
<YYINITIAL> "_Bool" { return CPsiTokens.BOOL_KEYWORD; }
<YYINITIAL> "_Complex"|"__complex__" { return CPsiTokens.COMPLEX_KEYWORD; }
<YYINITIAL> "wchar_t" { return keyword(CPsiTokens.WCHAR_KEYWORD); }
<YYINITIAL> "char16_t" { return keyword(CPsiTokens.CHAR16_KEYWORD); }
<YYINITIAL> "char32_t" { return keyword(CPsiTokens.CHAR32_KEYWORD); }

<YYINITIAL> "true" { return keyword(CPsiTokens.BOOL_LITERAL); }
<YYINITIAL> "false" { return keyword(CPsiTokens.BOOL_LITERAL); }

<YYINITIAL> "class" { return keyword(CPsiTokens.CLASS_KEYWORD); }
<YYINITIAL> "namespace" { return keyword(CPsiTokens.NAMESPACE_KEYWORD); }
<YYINITIAL> "using" { return keyword(CPsiTokens.USING_KEYWORD); }
<YYINITIAL> "public" { return keyword(CPsiTokens.PUBLIC_KEYWORD); }
<YYINITIAL> "private" { return keyword(CPsiTokens.PRIVATE_KEYWORD); }
<YYINITIAL> "protected" { return keyword(CPsiTokens.PROTECTED_KEYWORD); }
<YYINITIAL> "virtual" { return keyword(CPsiTokens.VIRTUAL_KEYWORD); }
<YYINITIAL> "explicit" { return keyword(CPsiTokens.EXPLICIT_KEYWORD); }
<YYINITIAL> "friend" { return keyword(CPsiTokens.FRIEND_KEYWORD); }
<YYINITIAL> "mutable" { return keyword(CPsiTokens.MUTABLE_KEYWORD); }
<YYINITIAL> "this" { return keyword(CPsiTokens.THIS_KEYWORD); }
<YYINITIAL> "operator" { return keyword(CPsiTokens.OPERATOR_KEYWORD); }
<YYINITIAL> "new" { return keyword(CPsiTokens.NEW_KEYWORD); }
<YYINITIAL> "delete" { return keyword(CPsiTokens.DELETE_KEYWORD); }
<YYINITIAL> "template" { return keyword(CPsiTokens.TEMPLATE_KEYWORD); }
<YYINITIAL> "typename" { return keyword(CPsiTokens.TYPENAME_KEYWORD); }
<YYINITIAL> "try" { return keyword(CPsiTokens.TRY_KEYWORD); }
<YYINITIAL> "catch" { return keyword(CPsiTokens.CATCH_KEYWORD); }
<YYINITIAL> "throw" { return keyword(CPsiTokens.THROW_KEYWORD); }
<YYINITIAL> "static_cast"|"dynamic_cast"|"const_cast"|"reinterpret_cast" { return keyword(CPsiTokens.CAST_KEYWORD); }
<YYINITIAL> "typeid" { return keyword(CPsiTokens.TYPEID_KEYWORD); }
<YYINITIAL> "decltype" { return keyword(CPsiTokens.DECLTYPE_KEYWORD); }
<YYINITIAL> "noexcept" { return keyword(CPsiTokens.NOEXCEPT_KEYWORD); }

<YYINITIAL> {IDENTIFIER} { return CPsiTokens.IDENTIFIER; }

<YYINITIAL> "..." { return CPsiTokens.ELLIPSIS; }
<YYINITIAL> "::" { return CPsiTokens.DOUBLE_COLON; }
<YYINITIAL> "->" { return CPsiTokens.ARROW; }
<YYINITIAL> "##" { return CPsiTokens.SHARPSHARP; }
<YYINITIAL> "#" { return CPsiTokens.SHARP; }

<YYINITIAL> "++" { return CPsiTokens.PLUSPLUS; }
<YYINITIAL> "--" { return CPsiTokens.MINUSMINUS; }
<YYINITIAL> "==" { return CPsiTokens.EQEQ; }
<YYINITIAL> "!=" { return CPsiTokens.NE; }
<YYINITIAL> "<=" { return CPsiTokens.LE; }
<YYINITIAL> ">=" { return CPsiTokens.GE; }
<YYINITIAL> "&&" { return CPsiTokens.ANDAND; }
<YYINITIAL> "||" { return CPsiTokens.OROR; }
<YYINITIAL> "<<" { return CPsiTokens.LTLT; }
<YYINITIAL> ">>" { return CPsiTokens.GTGT; }

<YYINITIAL> "+=" { return CPsiTokens.PLUSEQ; }
<YYINITIAL> "-=" { return CPsiTokens.MINUSEQ; }
<YYINITIAL> "*=" { return CPsiTokens.ASTERISKEQ; }
<YYINITIAL> "/=" { return CPsiTokens.DIVEQ; }
<YYINITIAL> "%=" { return CPsiTokens.PERCEQ; }
<YYINITIAL> "&=" { return CPsiTokens.ANDEQ; }
<YYINITIAL> "|=" { return CPsiTokens.OREQ; }
<YYINITIAL> "^=" { return CPsiTokens.XOREQ; }
<YYINITIAL> "<<=" { return CPsiTokens.LTLTEQ; }
<YYINITIAL> ">>=" { return CPsiTokens.GTGTEQ; }

<YYINITIAL> "("   { return CPsiTokens.LPARENTH; }
<YYINITIAL> ")"   { return CPsiTokens.RPARENTH; }
<YYINITIAL> "{"   { return CPsiTokens.LBRACE; }
<YYINITIAL> "}"   { return CPsiTokens.RBRACE; }
<YYINITIAL> "["   { return CPsiTokens.LBRACKET; }
<YYINITIAL> "]"   { return CPsiTokens.RBRACKET; }
<YYINITIAL> ";"   { return CPsiTokens.SEMICOLON; }
<YYINITIAL> ","   { return CPsiTokens.COMMA; }
<YYINITIAL> "."   { return CPsiTokens.DOT; }

<YYINITIAL> "=" { return CPsiTokens.EQ; }
<YYINITIAL> "!" { return CPsiTokens.EXCL; }
<YYINITIAL> "~" { return CPsiTokens.TILDE; }
<YYINITIAL> "?" { return CPsiTokens.QUEST; }
<YYINITIAL> ":" { return CPsiTokens.COLON; }
<YYINITIAL> "+" { return CPsiTokens.PLUS; }
<YYINITIAL> "-" { return CPsiTokens.MINUS; }
<YYINITIAL> "*" { return CPsiTokens.ASTERISK; }
<YYINITIAL> "/" { return CPsiTokens.DIV; }
<YYINITIAL> "%" { return CPsiTokens.PERC; }
<YYINITIAL> "&" { return CPsiTokens.AND; }
<YYINITIAL> "|" { return CPsiTokens.OR; }
<YYINITIAL> "^" { return CPsiTokens.XOR; }
<YYINITIAL> "<" { return CPsiTokens.LT; }
<YYINITIAL> ">" { return CPsiTokens.GT; }
<YYINITIAL> "\\" { return CPsiTokens.NEXT_LINE; }
<YYINITIAL> "@" { return CPsiTokens.AT; }

<YYINITIAL> [^] { return CPsiTokens.BAD_CHARACTER; }
