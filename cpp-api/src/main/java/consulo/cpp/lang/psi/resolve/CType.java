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

import consulo.cpp.lang.psi.CPsiEnum;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The type of a value as far as choosing an overload and finding members goes: a builtin, or a declaration - a class,
 * struct, union, enum, or the parameter of a template not substituted - with the pointers on it, whether it is a
 * reference, and the arguments of a template class. {@code const} and {@code volatile} are not kept: they do not change
 * the rank of a conversion.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public record CType(Builtin builtin, @Nullable PsiElement declaration, int pointers, boolean reference, List<CType> arguments) {
    public enum Builtin {
        /**
         * Not computed: an unresolved name, an operator overloaded, a template result. It matches anything, and
         * nothing is reported about it.
         */
        UNKNOWN,
        /**
         * A class, struct, union or enum - see {@link #declaration()}; a template parameter is one too.
         */
        DECLARED,
        VOID,
        BOOL,
        CHAR,
        SHORT,
        INT,
        LONG,
        LONG_LONG,
        FLOAT,
        DOUBLE,
        LONG_DOUBLE,
        NULLPTR;

        public boolean isIntegral() {
            return this == BOOL || this == CHAR || this == SHORT || this == INT || this == LONG || this == LONG_LONG;
        }

        public boolean isFloating() {
            return this == FLOAT || this == DOUBLE || this == LONG_DOUBLE;
        }

        public boolean isArithmetic() {
            return isIntegral() || isFloating();
        }
    }

    public static final CType UNKNOWN = new CType(Builtin.UNKNOWN, null, 0, false, List.of());

    public static CType of(Builtin builtin) {
        return new CType(builtin, null, 0, false, List.of());
    }

    public static CType of(PsiElement declaration, List<CType> arguments) {
        return new CType(Builtin.DECLARED, declaration, 0, false, List.copyOf(arguments));
    }

    public boolean isUnknown() {
        return builtin == Builtin.UNKNOWN;
    }

    public boolean isPointer() {
        return pointers > 0;
    }

    /**
     * A class, struct or union seen through no pointer: what has members.
     */
    public boolean isClass() {
        return builtin == Builtin.DECLARED && pointers == 0 && declaration != null && CDeclarations.isAggregate(declaration)
            && !(declaration instanceof CPsiEnum);
    }

    public boolean isEnum() {
        return builtin == Builtin.DECLARED && pointers == 0 && declaration instanceof CPsiEnum;
    }

    /**
     * A template parameter left as it is - in a template, or deduced at a call.
     */
    public boolean isTemplateParameter() {
        return builtin == Builtin.DECLARED && declaration != null && declaration.getFirstChild() == null;
    }

    public boolean isArithmeticValue() {
        return pointers == 0 && (builtin.isArithmetic() || isEnum());
    }

    public CType withPointers(int count) {
        return new CType(builtin, declaration, Math.max(0, count), false, arguments);
    }

    public CType withReference(boolean value) {
        return new CType(builtin, declaration, pointers, value, arguments);
    }

    /**
     * The value a pointer points to; unknown for a value which is no pointer - an overloaded {@code operator*}.
     */
    public CType dereferenced() {
        if (isUnknown()) {
            return this;
        }
        return pointers > 0 ? withPointers(pointers - 1) : UNKNOWN;
    }

    /**
     * Whether two types are the same, references and qualifiers apart.
     */
    public boolean isSameAs(CType other) {
        if (builtin != other.builtin || pointers != other.pointers) {
            return false;
        }
        if (builtin != Builtin.DECLARED) {
            return true;
        }
        return declaration != null && other.declaration != null && declaration.getManager().areElementsEquivalent(declaration, other.declaration);
    }

    /**
     * As C++ spells it, for the messages of the highlighting.
     */
    public String presentableText() {
        String base = switch (builtin) {
            case UNKNOWN -> "?";
            case DECLARED -> {
                String name = declaration == null ? null : CDeclarations.nameOf(declaration);
                yield name == null ? "?" : name;
            }
            case LONG_LONG -> "long long";
            case LONG_DOUBLE -> "long double";
            case NULLPTR -> "std::nullptr_t";
            default -> builtin.name().toLowerCase();
        };
        StringBuilder text = new StringBuilder(base);
        if (!arguments.isEmpty()) {
            text.append('<');
            for (int i = 0; i < arguments.size(); i++) {
                text.append(i == 0 ? "" : ", ").append(arguments.get(i).presentableText());
            }
            text.append('>');
        }
        text.append("*".repeat(pointers));
        if (reference) {
            text.append('&');
        }
        return text.toString();
    }
}
