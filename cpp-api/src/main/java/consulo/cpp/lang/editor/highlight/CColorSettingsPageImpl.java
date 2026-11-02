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

package consulo.cpp.lang.editor.highlight;

import consulo.annotation.component.ExtensionImpl;
import consulo.colorScheme.TextAttributesKey;
import consulo.colorScheme.setting.AttributesDescriptor;
import consulo.colorScheme.setting.ColorDescriptor;
import consulo.cpp.lang.version.CLanguageVersions;
import consulo.cpp.localize.CLocalize;
import consulo.language.editor.colorScheme.setting.ColorSettingsPage;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.localize.LocalizeValue;
import org.jetbrains.annotations.NotNull;
import consulo.cpp.lang.CLanguage;

import java.util.HashMap;
import java.util.Map;

/**
 * @author VISTALL
 * @date 17:14/11.12.2011
 */
@ExtensionImpl
public class CColorSettingsPageImpl implements ColorSettingsPage {
    @NotNull
    @Override
    public LocalizeValue getDisplayName() {
        return CLanguage.INSTANCE.getDisplayName();
    }

    @NotNull
    @Override
    public AttributesDescriptor[] getAttributeDescriptors() {
        return new AttributesDescriptor[]
            {
                new AttributesDescriptor(CLocalize.keyword(), CSyntaxHighlighter.KEYWORD),
                new AttributesDescriptor(CLocalize.number(), CSyntaxHighlighter.NUMBER),
                new AttributesDescriptor(CLocalize.string(), CSyntaxHighlighter.STRING),
                new AttributesDescriptor(CLocalize.constant(), CSyntaxHighlighter.CONSTANT),
                new AttributesDescriptor(CLocalize.compilerVariable(), CSyntaxHighlighter.COMPILER_VARIABLE),
                new AttributesDescriptor(CLocalize.preprocessorDirective(), CSyntaxHighlighter.PREPROCESSOR_DIRECTIVE),
                new AttributesDescriptor(CLocalize.includePath(), CSyntaxHighlighter.INCLUDE_PATH),
                new AttributesDescriptor(CLocalize.className(), CSyntaxHighlighter.CLASS_NAME),
                new AttributesDescriptor(CLocalize.typeAlias(), CSyntaxHighlighter.TYPE_ALIAS),
                new AttributesDescriptor(CLocalize.templateParameter(), CSyntaxHighlighter.TEMPLATE_PARAMETER),
                new AttributesDescriptor(CLocalize.namespaceName(), CSyntaxHighlighter.NAMESPACE),
                new AttributesDescriptor(CLocalize.functionDeclaration(), CSyntaxHighlighter.FUNCTION_DECLARATION),
                new AttributesDescriptor(CLocalize.functionCall(), CSyntaxHighlighter.FUNCTION_CALL),
                new AttributesDescriptor(CLocalize.memberFunction(), CSyntaxHighlighter.MEMBER_FUNCTION),
                new AttributesDescriptor(CLocalize.field(), CSyntaxHighlighter.FIELD),
                new AttributesDescriptor(CLocalize.staticField(), CSyntaxHighlighter.STATIC_FIELD),
                new AttributesDescriptor(CLocalize.globalVariable(), CSyntaxHighlighter.GLOBAL_VARIABLE),
                new AttributesDescriptor(CLocalize.localVariable(), CSyntaxHighlighter.LOCAL_VARIABLE),
                new AttributesDescriptor(CLocalize.parameter(), CSyntaxHighlighter.PARAMETER),
                new AttributesDescriptor(CLocalize.comma(), CSyntaxHighlighter.COMMA),
                new AttributesDescriptor(CLocalize.dot(), CSyntaxHighlighter.DOT),
                new AttributesDescriptor(CLocalize.lineComment(), CSyntaxHighlighter.LINE_COMMENT),
                new AttributesDescriptor(CLocalize.blockComment(), CSyntaxHighlighter.BLOCK_COMMENT),
            };
    }

    @NotNull
    @Override
    public ColorDescriptor[] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public SyntaxHighlighter getHighlighter() {
        return CLanguageVersions.getDefaultCpp().getSyntaxHighlighter();
    }

    @NotNull
    @Override
    public String getDemoText() {
        return "<blockcomment>/*\n" +
            " * This is block comment\n" +
            " * napile project\n" +
            "*/</blockcomment>\n" +
            "<linecomment>//This is line comment</linecomment>\n" +
            "<directive>#include</directive> <path>\"example.h\"</path>\n" +
            "<directive>#include</directive> <path>&lt;stdio.h&gt;</path>\n" +
            "<directive>#define</directive> <compilervar>LIMIT</compilervar> <number>10</number>\n\n" +
            "<directive>#ifdef</directive> <compilervar>VARTEST</compilervar>\n" +
            "<kw>int</kw> testVar = <number>1</number>;\n" +
            "<directive>#endif</directive>\n\n" +
            "<kw>enum</kw> ExEnum\n" +
            "{\n" +
            "  <const>Constant0</const><comma>,</comma>\n" +
            "  <const>Constant1</const> = <number>1</number>\n" +
            "};\n\n" +
            "<kw>typedef</kw> <kw>unsigned</kw> <kw>int</kw> <alias>uint</alias>;\n" +
            "<kw>int</kw> <global>counter</global> = <number>0</number>;\n\n" +
            "<kw>namespace</kw> <ns>geo</ns>\n" +
            "{\n" +
            "  <kw>struct</kw> <class>Point</class>\n" +
            "  {\n" +
            "    <kw>int</kw> <field>x</field>;\n" +
            "    <kw>static</kw> <kw>int</kw> <static>count</static>;\n" +
            "    <kw>int</kw> <member>length</member>() <kw>const</kw>;\n" +
            "  };\n" +
            "}\n\n" +
            "<kw>template</kw> <<kw>typename</kw> <tparam>T</tparam>> <tparam>T</tparam> <fdecl>twice</fdecl>(<tparam>T</tparam> <param>value</param>);\n\n" +
            "<alias>uint</alias> <fdecl>compute</fdecl>(<ns>geo</ns>::<class>Point</class> <param>point</param>)\n" +
            "{\n" +
            "  <kw>int</kw> <local>total</local> = <param>point</param>.<field>x</field> + <ns>geo</ns>::<class>Point</class>::<static>count</static>;\n" +
            "  <kw>return</kw> <local>total</local> + <param>point</param>.<member>length</member>() + <fcall>compute</fcall>(<param>point</param>);\n" +
            "}\n";
    }

    @Override
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        Map<String, TextAttributesKey> map = new HashMap<String, TextAttributesKey>();
        map.put("compilervar", CSyntaxHighlighter.COMPILER_VARIABLE);
        map.put("directive", CSyntaxHighlighter.PREPROCESSOR_DIRECTIVE);
        map.put("path", CSyntaxHighlighter.INCLUDE_PATH);
        map.put("string", CSyntaxHighlighter.STRING);
        map.put("kw", CSyntaxHighlighter.KEYWORD);
        map.put("number", CSyntaxHighlighter.NUMBER);
        map.put("const", CSyntaxHighlighter.CONSTANT);
        map.put("class", CSyntaxHighlighter.CLASS_NAME);
        map.put("alias", CSyntaxHighlighter.TYPE_ALIAS);
        map.put("ns", CSyntaxHighlighter.NAMESPACE);
        map.put("tparam", CSyntaxHighlighter.TEMPLATE_PARAMETER);
        map.put("fdecl", CSyntaxHighlighter.FUNCTION_DECLARATION);
        map.put("fcall", CSyntaxHighlighter.FUNCTION_CALL);
        map.put("member", CSyntaxHighlighter.MEMBER_FUNCTION);
        map.put("field", CSyntaxHighlighter.FIELD);
        map.put("static", CSyntaxHighlighter.STATIC_FIELD);
        map.put("global", CSyntaxHighlighter.GLOBAL_VARIABLE);
        map.put("local", CSyntaxHighlighter.LOCAL_VARIABLE);
        map.put("param", CSyntaxHighlighter.PARAMETER);
        map.put("comma", CSyntaxHighlighter.COMMA);
        map.put("dot", CSyntaxHighlighter.DOT);
        map.put("linecomment", CSyntaxHighlighter.LINE_COMMENT);
        map.put("blockcomment", CSyntaxHighlighter.BLOCK_COMMENT);
        return map;
    }
}
