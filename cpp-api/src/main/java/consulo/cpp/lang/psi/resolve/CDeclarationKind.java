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
package consulo.cpp.lang.psi.resolve;

import java.util.EnumSet;
import java.util.Set;

/**
 * What a name declares, as far as looking it up goes: C and C++ keep variables, functions and types in one ordinary
 * name space, and a name before {@code ::} can only be a type or a namespace.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public enum CDeclarationKind {
    /**
     * A local, a parameter, a field, a global or an enum constant.
     */
    VARIABLE,
    FUNCTION,
    /**
     * A class, struct, union, enum or typedef.
     */
    TYPE,
    NAMESPACE;

    public static final Set<CDeclarationKind> ALL = Set.copyOf(EnumSet.allOf(CDeclarationKind.class));

    public static final Set<CDeclarationKind> SCOPES = Set.copyOf(EnumSet.of(TYPE, NAMESPACE));

    public static final Set<CDeclarationKind> TYPES = Set.of(TYPE);

    public static final Set<CDeclarationKind> NAMESPACES = Set.of(NAMESPACE);

    public static final Set<CDeclarationKind> FUNCTIONS = Set.of(FUNCTION);
}
