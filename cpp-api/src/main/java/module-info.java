/**
 * @author VISTALL
 * @since 15/01/2023
 */
module consulo.cpp.api
{
    requires transitive consulo.language.api;
    requires transitive consulo.language.impl;
    requires transitive consulo.language.editor.api;

    exports consulo.cpp.api.icon;
    exports consulo.cpp.config;
    exports consulo.cpp.lang;
    exports consulo.cpp.lang.editor;
    exports consulo.cpp.lang.editor.completion;
    exports consulo.cpp.lang.editor.findUsage;
    exports consulo.cpp.lang.editor.highlight;
    exports consulo.cpp.lang.lexer;
    exports consulo.cpp.lang.parser;
    exports consulo.cpp.lang.psi;
    exports consulo.cpp.lang.psi.impl;
    exports consulo.cpp.lang.psi.resolve;
    exports consulo.cpp.lang.psi.visitor;
    exports consulo.cpp.lang.version;
    exports consulo.cpp.module.extension;
    exports consulo.cpp.moduleAware;
    exports consulo.cpp.preprocessor;
    exports consulo.cpp.preprocessor.expand;
    exports consulo.cpp.preprocessor.fileProvider;
    exports consulo.cpp.preprocessor.lexer;
    exports consulo.cpp.preprocessor.parser;
    exports consulo.cpp.preprocessor.psi;
    exports consulo.cpp.preprocessor.psi.impl;
    exports consulo.cpp.preprocessor.psi.impl.visitor;
    exports consulo.cpp.preprocessor.psi.stub;
    exports consulo.cpp.sdk;
    exports consulo.cpp.sdk.dialect;
    exports consulo.cpp.sdk.dialect.impl;
    exports consulo.cpp.util;
}