/**
 * @author VISTALL
 * @since 15/01/2023
 */
module consulo.cpp {
    requires consulo.application.api;
    requires consulo.color.scheme.api;
    requires consulo.language.api;
    requires consulo.language.editor.api;
    requires consulo.language.editor.refactoring.api;
    requires consulo.module.api;
    requires consulo.module.content.api;
    requires consulo.project.api;
    requires consulo.project.ui.view.api;
    requires consulo.ui.api;
    requires consulo.ui.ex.api;
    requires consulo.virtual.file.system.api;

    requires consulo.cpp.api;
    requires consulo.nativeDev.debugger.api;
}