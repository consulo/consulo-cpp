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

import consulo.cpp.lang.psi.*;
import consulo.cpp.lang.psi.resolve.CType.Builtin;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The types of expressions and declarations - what choosing an overload compares and what {@code value.} and
 * {@code value->} reach. Template arguments are carried and substituted: the {@code T item} of a {@code Box<Point>} is a
 * {@code Point}, and the {@code T} of a function template is deduced from the arguments of a call. An overloaded
 * operator, a template result not deduced, a name not found are of an unknown type, which matches anything.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public final class CTypes {
    private static final int MAX_DEPTH = 16;

    private static final TokenSet COMPARISONS = TokenSet.create(CPsiTokens.EQEQ, CPsiTokens.NE, CPsiTokens.LT, CPsiTokens.GT,
        CPsiTokens.LE, CPsiTokens.GE, CPsiTokens.ANDAND, CPsiTokens.OROR);

    private CTypes() {
    }

    // -----------------------------------------------------------------------
    // Members
    // -----------------------------------------------------------------------

    /**
     * The class whose members {@code expression.} reaches.
     */
    public static @Nullable PsiElement scopeOfExpression(PsiElement expression) {
        return scopeOfType(typeOf(expression));
    }

    /**
     * The class whose members a variable, a parameter or a field of it reaches.
     */
    public static @Nullable PsiElement scopeOfVariable(PsiElement variable) {
        return scopeOfType(declaredType(variable));
    }

    /**
     * The class of a type, whether through pointers or not - resolve is generous, {@code p.x} of a pointer resolves.
     */
    private static @Nullable PsiElement scopeOfType(CType type) {
        return type.builtin() == Builtin.DECLARED && type.declaration() != null && !type.isTemplateParameter()
            ? CNameResolver.scopeOf(type.declaration())
            : null;
    }

    // -----------------------------------------------------------------------
    // Expressions
    // -----------------------------------------------------------------------

    public static CType typeOf(@Nullable PsiElement expression) {
        return typeOf(expression, 0);
    }

    private static CType typeOf(@Nullable PsiElement expression, int depth) {
        if (expression == null || depth > MAX_DEPTH) {
            return CType.UNKNOWN;
        }
        int next = depth + 1;
        if (expression instanceof CPsiLiteralExpression) {
            return literalType(expression);
        }
        if (expression instanceof CPsiReferenceExpression) {
            return referenceType(expression, next);
        }
        if (expression instanceof CPsiCallExpression) {
            return callType(expression, next);
        }
        if (expression instanceof CPsiParenthesizedExpression) {
            return typeOf(firstExpression(expression), next);
        }
        if (expression instanceof CPsiPrefixExpression) {
            return prefixType(expression, next);
        }
        if (expression instanceof CPsiPostfixExpression) {
            return typeOf(firstExpression(expression), next).withReference(false);
        }
        if (expression instanceof CPsiBinaryExpression) {
            return binaryType(expression, next);
        }
        if (expression instanceof CPsiAssignmentExpression) {
            return typeOf(firstExpression(expression), next);
        }
        if (expression instanceof CPsiConditionalExpression) {
            List<PsiElement> operands = expressions(expression);
            return operands.size() >= 2 ? typeOf(operands.get(1), next) : CType.UNKNOWN;
        }
        if (expression instanceof CPsiCommaExpression) {
            List<PsiElement> operands = expressions(expression);
            return operands.isEmpty() ? CType.UNKNOWN : typeOf(operands.get(operands.size() - 1), next);
        }
        if (expression instanceof CPsiIndexedPropertyAccessExpression) {
            // array[i]; an overloaded operator[] of a class is not known
            return typeOf(firstExpression(expression), next).dereferenced();
        }
        if (expression instanceof CPsiSizeofExpression) {
            return CType.of(Builtin.LONG);
        }
        if (expression instanceof CPsiCastExpression || expression instanceof CPsiNewExpression) {
            CPsiTypeRef typeRef = CDeclarations.childOfType(expression, CPsiTypeRef.class);
            if (typeRef == null) {
                return CType.UNKNOWN;
            }
            CType type = typeOfTypeRef(typeRef, next);
            int pointers = type.pointers() + countPointers(expression) + (expression instanceof CPsiNewExpression ? 1 : 0);
            return type.isUnknown() ? type : type.withPointers(pointers);
        }
        return CType.UNKNOWN;
    }

    private static CType literalType(PsiElement literal) {
        PsiElement token = literal.getFirstChild();
        if (token == null) {
            return CType.UNKNOWN;
        }
        IElementType type = token.getNode().getElementType();
        String text = token.getText().toLowerCase(Locale.ROOT);
        if (type == CPsiTokens.INTEGER_LITERAL) {
            if (text.endsWith("ll") || text.endsWith("llu") || text.endsWith("ull")) {
                return CType.of(Builtin.LONG_LONG);
            }
            return CType.of(text.endsWith("l") || text.endsWith("lu") || text.endsWith("ul") ? Builtin.LONG : Builtin.INT);
        }
        if (type == CPsiTokens.LONG_LITERAL) {
            return CType.of(Builtin.LONG);
        }
        if (type == CPsiTokens.FLOAT_LITERAL) {
            return CType.of(Builtin.FLOAT);
        }
        if (type == CPsiTokens.DOUBLE_LITERAL) {
            return CType.of(text.endsWith("l") ? Builtin.LONG_DOUBLE : Builtin.DOUBLE);
        }
        if (type == CPsiTokens.CHARACTER_LITERAL) {
            return CType.of(Builtin.CHAR);
        }
        if (type == CPsiTokens.STRING_LITERAL) {
            return CType.of(Builtin.CHAR).withPointers(1);
        }
        if (type == CPsiTokens.BOOL_LITERAL) {
            return CType.of(Builtin.BOOL);
        }
        if (type == CPsiTokens.NULLPTR_KEYWORD) {
            return CType.of(Builtin.NULLPTR);
        }
        return CType.UNKNOWN;
    }

    /**
     * Whether an argument is a null pointer constant: {@code 0}, {@code nullptr}, {@code NULL} expanded.
     */
    public static boolean isNullPointerConstant(@Nullable PsiElement expression) {
        while (expression instanceof CPsiParenthesizedExpression) {
            expression = firstExpression(expression);
        }
        if (!(expression instanceof CPsiLiteralExpression)) {
            return false;
        }
        PsiElement token = expression.getFirstChild();
        if (token == null) {
            return false;
        }
        IElementType type = token.getNode().getElementType();
        return type == CPsiTokens.NULLPTR_KEYWORD || type == CPsiTokens.INTEGER_LITERAL && "0".equals(token.getText());
    }

    private static CType referenceType(PsiElement reference, int depth) {
        PsiElement first = reference.getFirstChild();
        if (first != null && first == reference.getLastChild() && first.getNode().getElementType() == CPsiTokens.THIS_KEYWORD) {
            PsiElement owner = CNameResolver.enclosingClass(reference);
            return owner == null ? CType.UNKNOWN : CType.of(owner, List.of()).withPointers(1);
        }
        PsiElement target = CNameResolver.resolveFirst(reference);
        if (target == null) {
            return CType.UNKNOWN;
        }
        if (target instanceof CPsiEnumConstant) {
            PsiElement enumElement = target.getParent();
            return enumElement instanceof CPsiEnum ? CType.of(enumElement, List.of()) : CType.of(Builtin.INT);
        }
        if (CDeclarations.kindOf(target) != CDeclarationKind.VARIABLE) {
            return CType.UNKNOWN;
        }
        return substitute(declaredType(target, depth), target, receiverType(reference, depth));
    }

    /**
     * The type of what a member is reached through: {@code value} of {@code value.member}, {@code value->member};
     * {@code null} for a name not reached through a value.
     */
    public static @Nullable CType receiverOf(PsiElement reference) {
        return receiverType(reference, 0);
    }

    private static @Nullable CType receiverType(PsiElement reference, int depth) {
        PsiElement qualifier = reference.getFirstChild();
        if (!(qualifier instanceof CPsiExpression)) {
            return null;
        }
        PsiElement operator = nextSignificant(qualifier);
        IElementType type = operator == null ? null : operator.getNode().getElementType();
        if (type != CPsiTokens.DOT && type != CPsiTokens.ARROW) {
            return null;
        }
        return typeOf(qualifier, depth + 1);
    }

    private static CType callType(PsiElement call, int depth) {
        if (CDeclarations.childOfType(call, CPsiParameterList.class) != null) {
            // a lambda
            return CType.UNKNOWN;
        }
        PsiElement callee = call.getFirstChild();
        if (!(callee instanceof CPsiReferenceExpression)) {
            return CType.UNKNOWN;
        }
        PsiElement target = CNameResolver.resolveFirst(callee);
        if (target == null) {
            return CType.UNKNOWN;
        }
        CDeclarationKind kind = CDeclarations.kindOf(target);
        if (kind == CDeclarationKind.TYPE) {
            // Point(1, 2), a functional cast or a construction
            PsiElement scope = CNameResolver.scopeOf(target);
            return scope != null ? CType.of(scope, List.of()) : declaredTypeOfAlias(target, depth);
        }
        if (kind != CDeclarationKind.FUNCTION) {
            return CType.UNKNOWN;
        }
        CType returned = substitute(declaredType(target, depth), target, receiverType(callee, depth));
        if (returned.isTemplateParameter()) {
            returned = deduce(returned, target, argumentsOf(call), depth);
        }
        return returned;
    }

    private static CType prefixType(PsiElement expression, int depth) {
        PsiElement operator = expression.getFirstChild();
        CType operand = typeOf(firstExpression(expression), depth);
        IElementType type = operator == null ? null : operator.getNode().getElementType();
        if (type == CPsiTokens.ASTERISK) {
            return operand.dereferenced();
        }
        if (type == CPsiTokens.AND) {
            return operand.isUnknown() ? operand : operand.withPointers(operand.pointers() + 1);
        }
        if (type == CPsiTokens.EXCL) {
            return CType.of(Builtin.BOOL);
        }
        if (type == CPsiTokens.PLUSPLUS || type == CPsiTokens.MINUSMINUS) {
            return operand;
        }
        // + - ~
        return operand.isArithmeticValue() ? promoted(operand) : CType.UNKNOWN;
    }

    private static CType binaryType(PsiElement expression, int depth) {
        List<PsiElement> operands = expressions(expression);
        PsiElement operator = operands.isEmpty() ? null : nextSignificant(operands.get(0));
        IElementType type = operator == null ? null : operator.getNode().getElementType();
        if (operands.size() != 2 || type == null) {
            return CType.UNKNOWN;
        }
        CType left = typeOf(operands.get(0), depth).withReference(false);
        CType right = typeOf(operands.get(1), depth).withReference(false);
        if (left.isClass() || right.isClass() || left.isUnknown() || right.isUnknown()) {
            // an overloaded operator - a stream's <<, a vector's + - is not known
            return CType.UNKNOWN;
        }
        if (COMPARISONS.contains(type)) {
            return CType.of(Builtin.BOOL);
        }
        if (left.isPointer() && right.isPointer() && type == CPsiTokens.MINUS) {
            return CType.of(Builtin.LONG);
        }
        if (left.isPointer()) {
            return left;
        }
        if (right.isPointer() && type == CPsiTokens.PLUS) {
            return right;
        }
        if (CPsiTokens.SHIFT_OPERATIONS.contains(type)) {
            return promoted(left);
        }
        return left.isArithmeticValue() && right.isArithmeticValue() ? usualArithmetic(left, right) : CType.UNKNOWN;
    }

    /**
     * {@code bool}, {@code char}, {@code short} and unscoped enums become {@code int} in arithmetic.
     */
    public static CType promoted(CType type) {
        if (type.isEnum()) {
            return CType.of(Builtin.INT);
        }
        Builtin builtin = type.builtin();
        return builtin == Builtin.BOOL || builtin == Builtin.CHAR || builtin == Builtin.SHORT ? CType.of(Builtin.INT) : type.withReference(false);
    }

    private static CType usualArithmetic(CType left, CType right) {
        Builtin first = promoted(left).builtin();
        Builtin second = promoted(right).builtin();
        return CType.of(first.ordinal() >= second.ordinal() ? first : second);
    }

    // -----------------------------------------------------------------------
    // Declarations
    // -----------------------------------------------------------------------

    /**
     * The type a variable, a parameter or a field is declared with, or a function returns: its type reference with the
     * pointers, the reference and the array suffixes of its declarator; for {@code auto} what initializes it.
     */
    public static CType declaredType(PsiElement declaration) {
        return declaredType(declaration, 0);
    }

    private static CType declaredType(PsiElement declaration, int depth) {
        if (depth > MAX_DEPTH) {
            return CType.UNKNOWN;
        }
        if (declaration.getFirstChild() == null) {
            // a template parameter: the value of int N is not followed
            return CType.UNKNOWN;
        }
        CPsiTypeRef typeRef = CDeclarations.typeRefOf(declaration);
        boolean reference = hasDeclaratorToken(declaration, CPsiTokens.AND) || hasDeclaratorToken(declaration, CPsiTokens.ANDAND);
        if (typeRef == null) {
            if (declaration instanceof CPsiDeclarationMethod) {
                // a constructor, a destructor, a conversion operator
                return CType.UNKNOWN;
            }
            return autoType(declaration, depth + 1).withReference(reference);
        }
        CType base = typeOfTypeRef(typeRef, depth + 1);
        if (base.isUnknown()) {
            return base;
        }
        int pointers = base.pointers() + countPointers(declaration) + (declaration instanceof CPsiDeclarationMethod ? 0 : countArraySuffixes(declaration));
        return base.withPointers(pointers).withReference(reference);
    }

    private static CType autoType(PsiElement variable, int depth) {
        PsiElement initializer = null;
        boolean afterAssign = false;
        for (PsiElement child = variable.getFirstChild(); child != null; child = child.getNextSibling()) {
            IElementType type = child.getNode().getElementType();
            if (type == CPsiTokens.EQ) {
                afterAssign = true;
            }
            else if (child instanceof CPsiExpression && afterAssign) {
                initializer = child;
                break;
            }
            else if (child instanceof CPsiInitializerList list) {
                initializer = CDeclarations.childOfType(list, CPsiExpression.class);
                break;
            }
        }
        if (initializer != null) {
            return typeOf(initializer, depth).withReference(false);
        }
        // for (auto& point : points): the element of an array or what a pointer points to
        PsiElement colon = nextSignificant(variable);
        if (colon != null && colon.getNode().getElementType() == CPsiTokens.COLON && variable.getParent() instanceof CPsiForStatement) {
            for (PsiElement range = colon.getNextSibling(); range != null; range = range.getNextSibling()) {
                if (range instanceof CPsiExpression) {
                    return typeOf(range, depth).dereferenced();
                }
            }
        }
        return CType.UNKNOWN;
    }

    /**
     * The type a type reference names: a builtin of its keywords, or the class, enum, alias or template parameter its
     * name resolves to, with the arguments of a template class.
     */
    public static CType typeOfTypeRef(CPsiTypeRef typeRef) {
        return typeOfTypeRef(typeRef, 0);
    }

    private static CType typeOfTypeRef(CPsiTypeRef typeRef, int depth) {
        if (depth > MAX_DEPTH) {
            return CType.UNKNOWN;
        }
        Builtin builtin = builtinOf(typeRef);
        if (builtin != null) {
            return CType.of(builtin);
        }
        PsiElement target = CNameResolver.resolveFirst(typeRef);
        if (target == null) {
            return CType.UNKNOWN;
        }
        if (target.getFirstChild() == null) {
            // typename T
            return CType.of(target, List.of());
        }
        if (CDeclarations.isTypeAlias(target)) {
            return declaredTypeOfAlias(target, depth);
        }
        if (CDeclarations.isAggregate(target)) {
            return CType.of(target, templateArguments(typeRef, depth));
        }
        return CType.UNKNOWN;
    }

    private static CType declaredTypeOfAlias(PsiElement alias, int depth) {
        if (!CDeclarations.isTypeAlias(alias)) {
            return CType.UNKNOWN;
        }
        PsiElement aggregate = CNameResolver.scopeOf(alias);
        if (aggregate != null && aggregate.getParent() == alias) {
            // typedef struct { ... } Point;
            return CType.of(aggregate, List.of());
        }
        CPsiTypeRef typeRef = CDeclarations.typeRefOf(alias);
        if (typeRef == null) {
            return CType.UNKNOWN;
        }
        CType type = typeOfTypeRef(typeRef, depth + 1);
        return type.isUnknown() ? type : type.withPointers(type.pointers() + countPointers(alias));
    }

    private static @Nullable Builtin builtinOf(CPsiTypeRef typeRef) {
        List<PsiElement> tokens = new ArrayList<>();
        for (PsiElement child = typeRef.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode().getElementType() == CPsiTokens.LT) {
                break;
            }
            tokens.add(child);
        }
        return builtinOf(tokens);
    }

    /**
     * The builtin the keywords among {@code tokens} spell - {@code unsigned long long}, {@code long double} - or
     * {@code null} when they spell none.
     */
    private static @Nullable Builtin builtinOf(List<PsiElement> tokens) {
        int longs = 0;
        Builtin found = null;
        for (PsiElement child : tokens) {
            IElementType type = child.getNode().getElementType();
            if (type == CPsiTokens.LONG_KEYWORD) {
                longs++;
            }
            else if (type == CPsiTokens.VOID_KEYWORD) {
                found = Builtin.VOID;
            }
            else if (type == CPsiTokens.BOOL_KEYWORD) {
                found = Builtin.BOOL;
            }
            else if (type == CPsiTokens.CHAR_KEYWORD || type == CPsiTokens.WCHAR_KEYWORD || type == CPsiTokens.CHAR16_KEYWORD
                || type == CPsiTokens.CHAR32_KEYWORD) {
                found = Builtin.CHAR;
            }
            else if (type == CPsiTokens.SHORT_KEYWORD) {
                found = Builtin.SHORT;
            }
            else if (type == CPsiTokens.FLOAT_KEYWORD) {
                found = Builtin.FLOAT;
            }
            else if (type == CPsiTokens.DOUBLE_KEYWORD) {
                found = Builtin.DOUBLE;
            }
            else if (type == CPsiTokens.__INT64_KEYWORD) {
                found = Builtin.LONG_LONG;
            }
            else if ((type == CPsiTokens.INT_KEYWORD || type == CPsiTokens.SIGNED_KEYWORD || type == CPsiTokens.UNSIGNED_KEYWORD)
                && found == null) {
                found = Builtin.INT;
            }
        }
        if (longs > 0) {
            if (found == Builtin.DOUBLE) {
                return Builtin.LONG_DOUBLE;
            }
            return longs > 1 ? Builtin.LONG_LONG : Builtin.LONG;
        }
        return found;
    }

    /**
     * The arguments of {@code Box<Point, int>}: types, or unknown for a value; one level - an argument's own arguments
     * are not read.
     */
    private static List<CType> templateArguments(CPsiTypeRef typeRef, int depth) {
        List<CType> arguments = new ArrayList<>();
        int level = 0;
        List<PsiElement> argument = new ArrayList<>();
        for (PsiElement child = typeRef.getFirstChild(); child != null; child = child.getNextSibling()) {
            IElementType type = child.getNode().getElementType();
            if (child instanceof PsiWhiteSpace || child instanceof PsiComment) {
                continue;
            }
            int closes = type == CPsiTokens.GT ? 1 : type == CPsiTokens.GTGT ? 2 : 0;
            if (type == CPsiTokens.LT) {
                level++;
                if (level == 1) {
                    continue;
                }
            }
            else if (closes > 0) {
                level -= closes;
                if (level <= 0) {
                    arguments.add(argumentType(typeRef, argument, depth));
                    break;
                }
            }
            else if (type == CPsiTokens.COMMA && level == 1) {
                arguments.add(argumentType(typeRef, argument, depth));
                argument = new ArrayList<>();
                continue;
            }
            if (level >= 1) {
                argument.add(child);
            }
        }
        return arguments;
    }

    private static CType argumentType(CPsiTypeRef typeRef, List<PsiElement> tokens, int depth) {
        int pointers = 0;
        List<PsiElement> names = new ArrayList<>();
        List<PsiElement> head = new ArrayList<>();
        for (PsiElement token : tokens) {
            IElementType type = token.getNode().getElementType();
            if (type == CPsiTokens.LT) {
                break;
            }
            head.add(token);
            if (type == CPsiTokens.ASTERISK) {
                pointers++;
            }
            else if (type == CPsiTokens.IDENTIFIER) {
                names.add(token);
            }
        }
        Builtin builtin = builtinOf(head);
        if (builtin != null) {
            return CType.of(builtin).withPointers(pointers);
        }
        if (names.isEmpty()) {
            return CType.UNKNOWN;
        }
        CNameResolver.NameChain chain = new CNameResolver.NameChain(names, false);
        List<PsiElement> targets = CNameResolver.resolveChainTargets(typeRef, chain, CDeclarationKind.TYPES);
        if (targets.isEmpty()) {
            return CType.UNKNOWN;
        }
        PsiElement target = targets.get(0);
        CType type = target.getFirstChild() == null ? CType.of(target, List.of())
            : CDeclarations.isTypeAlias(target) ? declaredTypeOfAlias(target, depth + 1)
            : CDeclarations.isAggregate(target) ? CType.of(target, List.of()) : CType.UNKNOWN;
        return type.isUnknown() ? type : type.withPointers(type.pointers() + pointers);
    }

    // -----------------------------------------------------------------------
    // Templates
    // -----------------------------------------------------------------------

    /**
     * The type of a member as seen through a value of {@code receiver}: a {@code T} of {@code Box<T>} becomes the
     * argument the receiver's {@code Box<Point>} gives it.
     */
    public static CType substitute(CType type, PsiElement member, @Nullable CType receiver) {
        if (receiver == null || receiver.arguments().isEmpty() || !type.isTemplateParameter()) {
            return type;
        }
        PsiElement owner = CDeclarations.enclosingAggregate(member);
        if (owner == null || receiver.declaration() == null || !owner.getManager().areElementsEquivalent(owner, receiver.declaration())) {
            return type;
        }
        List<CTemplates.Parameter> parameters = CTemplates.parametersOf(owner);
        for (int i = 0; i < parameters.size() && i < receiver.arguments().size(); i++) {
            if (parameters.get(i).name() == type.declaration()) {
                return combine(receiver.arguments().get(i), type);
            }
        }
        return type;
    }

    /**
     * The {@code T} a call of a function template makes of its {@code T}: the type of the first argument whose parameter
     * is that {@code T}.
     */
    private static CType deduce(CType parameterType, PsiElement function, List<PsiElement> arguments, int depth) {
        Map<PsiElement, CType> deduced = deduceAll(function, arguments, depth);
        CType argument = deduced.get(parameterType.declaration());
        return argument == null ? CType.UNKNOWN : combine(argument, parameterType);
    }

    public static Map<PsiElement, CType> deduceAll(PsiElement function, List<PsiElement> arguments, int depth) {
        Map<PsiElement, CType> deduced = new HashMap<>();
        List<PsiElement> parameters = parametersOf(function);
        for (int i = 0; i < parameters.size() && i < arguments.size(); i++) {
            CType parameter = declaredType(parameters.get(i), depth + 1);
            if (parameter.isTemplateParameter() && !deduced.containsKey(parameter.declaration())) {
                CType argument = typeOf(arguments.get(i), depth + 1).withReference(false);
                if (!argument.isUnknown() && argument.pointers() >= parameter.pointers()) {
                    deduced.put(parameter.declaration(), argument.withPointers(argument.pointers() - parameter.pointers()));
                }
            }
        }
        return deduced;
    }

    /**
     * {@code argument} put where the template parameter of {@code written} is: {@code T*} of an {@code int} is
     * {@code int*}.
     */
    private static CType combine(CType argument, CType written) {
        if (argument.isUnknown()) {
            return argument;
        }
        return argument.withPointers(argument.pointers() + written.pointers()).withReference(written.reference());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * The parameters of a function, without a lone {@code void} - {@code f(void)} takes none.
     */
    public static List<PsiElement> parametersOf(PsiElement function) {
        CPsiParameterList list = CDeclarations.childOfType(function, CPsiParameterList.class);
        List<PsiElement> parameters = new ArrayList<>();
        if (list == null) {
            return parameters;
        }
        for (PsiElement child = list.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof CPsiParameter) {
                parameters.add(child);
            }
        }
        if (parameters.size() == 1 && CDeclarations.nameIdentifier(parameters.get(0)) == null) {
            CPsiTypeRef typeRef = CDeclarations.childOfType(parameters.get(0), CPsiTypeRef.class);
            if (typeRef != null && builtinOf(typeRef) == Builtin.VOID && countPointers(parameters.get(0)) == 0) {
                parameters.clear();
            }
        }
        return parameters;
    }

    public static List<PsiElement> argumentsOf(PsiElement call) {
        CPsiArgumentList list = CDeclarations.childOfType(call, CPsiArgumentList.class);
        return list == null ? List.of() : expressions(list);
    }

    private static boolean hasDeclaratorToken(PsiElement declaration, IElementType token) {
        for (PsiElement child = declaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode().getElementType() == token) {
                return true;
            }
        }
        return false;
    }

    /**
     * The {@code *} of a declarator - among the direct children of the declaration, not inside its type or initializer.
     */
    private static int countPointers(PsiElement declaration) {
        int count = 0;
        for (PsiElement child = declaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            IElementType type = child.getNode().getElementType();
            if (type == CPsiTokens.ASTERISK) {
                count++;
            }
            else if (type == CPsiTokens.EQ || child instanceof CPsiParameterList || child instanceof CPsiCodeBlock) {
                break;
            }
        }
        return count;
    }

    private static int countArraySuffixes(PsiElement declaration) {
        int count = 0;
        for (PsiElement child = declaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            IElementType type = child.getNode().getElementType();
            if (type == CPsiTokens.LBRACKET) {
                count++;
            }
            else if (type == CPsiTokens.EQ) {
                break;
            }
        }
        return count;
    }

    private static @Nullable PsiElement firstExpression(PsiElement element) {
        return CDeclarations.childOfType(element, CPsiExpression.class);
    }

    private static List<PsiElement> expressions(PsiElement element) {
        List<PsiElement> expressions = new ArrayList<>();
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof CPsiExpression) {
                expressions.add(child);
            }
        }
        return expressions;
    }

    private static @Nullable PsiElement nextSignificant(PsiElement element) {
        PsiElement next = element.getNextSibling();
        while (next instanceof PsiWhiteSpace || next instanceof PsiComment) {
            next = next.getNextSibling();
        }
        return next;
    }
}
