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

import consulo.application.progress.ProgressManager;
import consulo.cpp.lang.psi.*;
import consulo.cpp.lang.psi.resolve.CType.Builtin;
import consulo.cpp.lang.version.CLanguageVersion;
import consulo.cpp.lang.version.CLanguageVersions;
import consulo.language.ast.ASTNode;
import consulo.cpp.preprocessor.psi.CPreprocessorElementTypes;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.PsiElementResolveResult;
import consulo.language.psi.ResolveResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chooses among the functions a call may mean, as C++ does: the viable ones - those taking that many arguments, each of
 * which converts to its parameter - and among them the best, which converts no argument worse than any other and one
 * better. Each candidate is ranked once, before anything is compared; a function template loses a tie to a function.
 * <p>
 * Types the plugin does not know - an unresolved name, an overloaded operator - rank as "unknown", which is neither
 * better nor worse than anything: they neither exclude a candidate nor decide between two.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public final class COverloads {
    /**
     * How an argument converts to a parameter, best first.
     */
    public enum Rank {
        EXACT,
        PROMOTION,
        CONVERSION,
        /**
         * A pointer or a {@code nullptr} to {@code bool}: worse than any other standard conversion.
         */
        BOOLEAN_CONVERSION,
        USER_DEFINED,
        ELLIPSIS,
        /**
         * The types are not both known: no comparison is made.
         */
        UNKNOWN,
        NO_MATCH
    }

    /**
     * Why a candidate is not viable, for the highlighting to say.
     */
    public record Problem(int argument, @Nullable CType argumentType, @Nullable CType parameterType, boolean arity) {
    }

    /**
     * A function ranked against the arguments of one call.
     */
    public record Candidate(PsiElement function, List<Rank> ranks, boolean template, @Nullable Problem problem) {
        public boolean isViable() {
            return problem == null;
        }
    }

    private static final int MAX_BASE_DEPTH = 16;

    private COverloads() {
    }

    /**
     * The candidates of a call: the best ones valid and first, the others after them as invalid results.
     *
     * @param receiver the type of the value a member function is called on, {@code null} for none
     */
    public static ResolveResult[] select(List<PsiElement> functions, List<PsiElement> arguments, @Nullable CType receiver) {
        List<Candidate> candidates = rank(functions, arguments, receiver);
        List<Candidate> best = best(candidates);
        List<ResolveResult> results = new ArrayList<>(candidates.size());
        for (Candidate candidate : best) {
            results.add(new PsiElementResolveResult(candidate.function(), true));
        }
        for (Candidate candidate : candidates) {
            if (!best.contains(candidate)) {
                results.add(new PsiElementResolveResult(candidate.function(), false));
            }
        }
        return results.toArray(ResolveResult.EMPTY_ARRAY);
    }

    public static List<Candidate> rank(List<PsiElement> functions, List<PsiElement> arguments, @Nullable CType receiver) {
        List<CType> argumentTypes = new ArrayList<>(arguments.size());
        List<Boolean> nullConstants = new ArrayList<>(arguments.size());
        for (PsiElement argument : arguments) {
            argumentTypes.add(CTypes.typeOf(argument).withReference(false));
            nullConstants.add(CTypes.isNullPointerConstant(argument));
        }
        boolean expanded = false;
        for (PsiElement argument : arguments) {
            PsiElement next = argument.getNextSibling();
            while (next instanceof PsiWhiteSpace) {
                next = next.getNextSibling();
            }
            expanded |= next != null && next.getNode().getElementType() == CPsiTokens.ELLIPSIS
                || argument.getLastChild() != null && argument.getLastChild().getNode().getElementType() == CPsiTokens.ELLIPSIS;
        }
        List<Candidate> candidates = new ArrayList<>(functions.size());
        for (PsiElement function : functions) {
            ProgressManager.checkCanceled();
            candidates.add(rankOne(function, argumentTypes, nullConstants, receiver, expanded));
        }
        return candidates;
    }

    /**
     * {@code int f();} of C before C23: a declaration without a prototype, which says nothing of the parameters.
     */
    private static boolean isUnprototyped(PsiElement function) {
        CPsiParameterList list = CDeclarations.childOfType(function, CPsiParameterList.class);
        if (list == null || CDeclarations.childOfType(list, CPsiParameter.class) != null) {
            return false;
        }
        CLanguageVersion version = CLanguageVersions.of(function);
        return !version.isCpp();
    }

    private static Candidate rankOne(PsiElement function, List<CType> arguments, List<Boolean> nullConstants, @Nullable CType receiver,
                                     boolean expanded) {
        boolean template = !CTemplates.parametersOf(function).isEmpty();
        List<PsiElement> parameters = CTypes.parametersOf(function);
        // f() of C before C23 declares no parameters, rather than none: any arguments go
        boolean variadic = expanded || parameters.isEmpty() && isUnprototyped(function);
        int required = 0;
        List<CType> parameterTypes = new ArrayList<>(parameters.size());
        for (PsiElement parameter : parameters) {
            ASTNode node = parameter.getNode();
            if (node.findChildByType(CPsiTokens.THIS_KEYWORD) != null) {
                // the explicit object parameter of C++23, this Self&& self: the object, not an argument
                continue;
            }
            if (node.findChildByType(CPsiTokens.ELLIPSIS) != null) {
                // ..., or a pack T... args
                variadic = true;
                continue;
            }
            if (node.findChildByType(CPsiTokens.EQ) == null) {
                required = parameterTypes.size() + 1;
            }
            parameterTypes.add(CTypes.substitute(CTypes.declaredType(parameter), function, receiver).withReference(false));
        }

        List<Rank> ranks = new ArrayList<>(arguments.size());
        if (expanded) {
            // f(args...): how many arguments there are is not known
            for (int i = 0; i < arguments.size(); i++) {
                ranks.add(Rank.UNKNOWN);
            }
            return new Candidate(function, ranks, template, null);
        }
        if (arguments.size() < required || arguments.size() > parameterTypes.size() && !variadic) {
            return new Candidate(function, ranks, template, new Problem(-1, null, null, true));
        }
        for (int i = 0; i < arguments.size(); i++) {
            if (i >= parameterTypes.size()) {
                ranks.add(Rank.ELLIPSIS);
                continue;
            }
            Rank rank = rank(arguments.get(i), parameterTypes.get(i), nullConstants.get(i));
            if (rank == Rank.NO_MATCH) {
                return new Candidate(function, ranks, template, new Problem(i, arguments.get(i), parameterTypes.get(i), false));
            }
            ranks.add(rank);
        }
        return new Candidate(function, ranks, template, null);
    }

    /**
     * The viable candidates no other one is better than; none when none is viable.
     */
    private static List<Candidate> best(List<Candidate> candidates) {
        List<Candidate> viable = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.isViable()) {
                viable.add(candidate);
            }
        }
        List<Candidate> best = new ArrayList<>();
        for (Candidate candidate : viable) {
            boolean beaten = false;
            for (Candidate other : viable) {
                if (other != candidate && isBetter(other, candidate)) {
                    beaten = true;
                    break;
                }
            }
            if (!beaten) {
                best.add(candidate);
            }
        }
        return best;
    }

    /**
     * Whether {@code first} is better than {@code second}: no argument worse and one better, or else a function where the
     * other is a template.
     */
    private static boolean isBetter(Candidate first, Candidate second) {
        boolean strictly = false;
        int count = Math.min(first.ranks().size(), second.ranks().size());
        for (int i = 0; i < count; i++) {
            Rank a = first.ranks().get(i);
            Rank b = second.ranks().get(i);
            if (a == Rank.UNKNOWN || b == Rank.UNKNOWN) {
                continue;
            }
            int compared = Integer.compare(a.ordinal(), b.ordinal());
            if (compared > 0) {
                return false;
            }
            if (compared < 0) {
                strictly = true;
            }
        }
        return strictly || !first.template() && second.template();
    }

    // -----------------------------------------------------------------------
    // Implicit conversions
    // -----------------------------------------------------------------------

    /**
     * How a value of {@code argument} converts to {@code parameter}, references and qualifiers apart.
     *
     * @param nullConstant whether the argument is {@code 0} or {@code nullptr}, which converts to any pointer
     */
    public static Rank rank(CType argument, CType parameter, boolean nullConstant) {
        if (argument.isUnknown() || parameter.isUnknown() || argument.isTemplateParameter()) {
            return Rank.UNKNOWN;
        }
        if (parameter.isTemplateParameter()) {
            // deduced from the argument
            return argument.pointers() >= parameter.pointers() ? Rank.EXACT : Rank.NO_MATCH;
        }
        if (parameter.isPointer()) {
            return pointerRank(argument, parameter, nullConstant);
        }
        if (parameter.isClass()) {
            return classRank(argument, parameter);
        }
        if (parameter.isEnum()) {
            return argument.isSameAs(parameter) ? Rank.EXACT : argument.isClass() ? Rank.UNKNOWN : Rank.NO_MATCH;
        }
        if (parameter.builtin().isArithmetic()) {
            return arithmeticRank(argument, parameter);
        }
        return Rank.UNKNOWN;
    }

    private static Rank pointerRank(CType argument, CType parameter, boolean nullConstant) {
        if (nullConstant || argument.builtin() == Builtin.NULLPTR) {
            return Rank.CONVERSION;
        }
        if (!argument.isPointer()) {
            // a class may convert itself to a pointer
            return argument.isClass() ? Rank.UNKNOWN : Rank.NO_MATCH;
        }
        if (parameter.pointers() == 1 && parameter.builtin() == Builtin.VOID) {
            return argument.pointers() == 1 && argument.builtin() == Builtin.VOID ? Rank.EXACT : Rank.CONVERSION;
        }
        if (argument.pointers() != parameter.pointers()) {
            return Rank.NO_MATCH;
        }
        if (argument.isSameAs(parameter)) {
            return Rank.EXACT;
        }
        CType argumentBase = argument.withPointers(0);
        CType parameterBase = parameter.withPointers(0);
        if (argument.pointers() == 1 && argumentBase.isClass() && parameterBase.isClass()) {
            return isDerivedFrom(argumentBase.declaration(), parameterBase.declaration()) ? Rank.CONVERSION : Rank.NO_MATCH;
        }
        return Rank.NO_MATCH;
    }

    private static Rank classRank(CType argument, CType parameter) {
        if (argument.isSameAs(parameter)) {
            return Rank.EXACT;
        }
        if (hasAttributes(parameter.declaration())) {
            // __attribute__((transparent_union)) takes any of its members' types; what an attribute changes is not known
            return Rank.UNKNOWN;
        }
        if (argument.isClass() && isDerivedFrom(argument.declaration(), parameter.declaration())) {
            return Rank.CONVERSION;
        }
        PsiElement target = parameter.declaration();
        if (target != null && !hasConstructors(target)) {
            // an aggregate converts from nothing else - unless the argument's class converts itself
            return argument.isClass() && hasConversionOperator(argument.declaration()) ? Rank.USER_DEFINED : Rank.NO_MATCH;
        }
        // a converting constructor may take it; which ones would, is not checked
        return Rank.USER_DEFINED;
    }

    private static Rank arithmeticRank(CType argument, CType parameter) {
        if (argument.isPointer() || argument.builtin() == Builtin.NULLPTR) {
            return parameter.builtin() == Builtin.BOOL ? Rank.BOOLEAN_CONVERSION : Rank.NO_MATCH;
        }
        if (argument.isEnum()) {
            if (isScopedEnum(argument.declaration())) {
                return Rank.NO_MATCH;
            }
            return parameter.builtin() == Builtin.INT ? Rank.PROMOTION : Rank.CONVERSION;
        }
        if (argument.isClass()) {
            return hasConversionOperator(argument.declaration()) ? Rank.USER_DEFINED : Rank.NO_MATCH;
        }
        Builtin from = argument.builtin();
        Builtin to = parameter.builtin();
        if (!from.isArithmetic()) {
            return Rank.NO_MATCH;
        }
        if (from == to) {
            return Rank.EXACT;
        }
        if (to == Builtin.INT && (from == Builtin.BOOL || from == Builtin.CHAR || from == Builtin.SHORT) || from == Builtin.FLOAT && to == Builtin.DOUBLE) {
            return Rank.PROMOTION;
        }
        return Rank.CONVERSION;
    }

    // -----------------------------------------------------------------------
    // Classes
    // -----------------------------------------------------------------------

    public static boolean isDerivedFrom(@Nullable PsiElement derived, @Nullable PsiElement base) {
        return derived != null && base != null && isDerivedFrom(derived, base, new HashSet<>(), 0);
    }

    private static boolean isDerivedFrom(PsiElement derived, PsiElement base, Set<PsiElement> visited, int depth) {
        if (depth > MAX_BASE_DEPTH || !visited.add(derived)) {
            return false;
        }
        CPsiSuperClassList superClasses = CDeclarations.childOfType(derived, CPsiSuperClassList.class);
        if (superClasses == null) {
            return false;
        }
        for (CPsiSuperClass superClass : superClasses.getSuperClasses()) {
            PsiElement scope = CNameResolver.scopeOf(CNameResolver.resolveFirst(superClass));
            if (scope != null && (scope.getManager().areElementsEquivalent(scope, base) || isDerivedFrom(scope, base, visited, depth + 1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a class may have a constructor: it declares a member function without a return type - a constructor, or a
     * macro the file could not expand, {@code GENERATED_BODY()} - or a macro makes part of its body.
     */
    private static boolean hasConstructors(PsiElement aggregate) {
        if (hasMacroExpandedMembers(aggregate)) {
            return true;
        }
        for (PsiElement member : CDeclarations.ownMembersOf(aggregate)) {
            if (member instanceof CPsiDeclarationMethod && CDeclarations.typeRefOf(member) == null) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasConversionOperator(@Nullable PsiElement aggregate) {
        if (aggregate == null) {
            return true;
        }
        if (hasMacroExpandedMembers(aggregate)) {
            return true;
        }
        for (PsiElement child = aggregate.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (containsOperator(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsOperator(PsiElement element) {
        if (element instanceof CPsiAccessorOwner) {
            for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (containsOperator(child)) {
                    return true;
                }
            }
            return false;
        }
        return element instanceof CPsiDeclarationMethod && element.getNode().findChildByType(CPsiTokens.OPERATOR_KEYWORD) != null;
    }

    /**
     * Whether a macro expansion makes part of a class body - {@code GENERATED_BODY()}: what it declares is not all in the
     * tree, so the class is not judged on what is.
     */
    private static boolean hasMacroExpandedMembers(PsiElement aggregate) {
        for (PsiElement child = aggregate.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof CPsiAccessorOwner && hasMacroExpandedMembers(child)) {
                return true;
            }
            if (child.getNode().getElementType() == CPreprocessorElementTypes.MACRO_INVOCATION || child instanceof PsiErrorElement
                || child.getTextLength() == 0 && child.getFirstChild() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a class or the typedef naming it carries an attribute.
     */
    private static boolean hasAttributes(@Nullable PsiElement aggregate) {
        if (aggregate == null) {
            return false;
        }
        for (PsiElement element = aggregate; element != null; element = element.getParent() instanceof CPsiTypeDeclaration typedef ? typedef : null) {
            for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof CPsiAttributeList || child.getNode().getElementType() == CPsiTokens.ATTRIBUTE_KEYWORD) {
                    return true;
                }
            }
            if (element != aggregate) {
                break;
            }
        }
        return false;
    }

    private static boolean isScopedEnum(@Nullable PsiElement element) {
        return element instanceof CPsiEnum enumElement && CDeclarations.isScopedEnum(enumElement);
    }
}
