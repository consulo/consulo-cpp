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
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.resolve.ResolveState;
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

    private static final int MAX_BASE_DEPTH = 8;

    /**
     * The type traits of the standard library which give their argument back, as far as resolving its members goes -
     * {@code typename remove_cv<T>::type} is too far to follow - and the pointers they add or take away.
     */
    private static final Map<String, Integer> TRAIT_POINTERS = Map.ofEntries(
        Map.entry("remove_cv_t", 0), Map.entry("remove_const_t", 0), Map.entry("remove_volatile_t", 0),
        Map.entry("remove_reference_t", 0), Map.entry("remove_cvref_t", 0), Map.entry("__remove_cvref_t", 0),
        Map.entry("__remove_cv_t", 0), Map.entry("decay_t", 0), Map.entry("__decay_t", 0), Map.entry("type_identity_t", 0),
        Map.entry("add_const_t", 0), Map.entry("add_cv_t", 0), Map.entry("add_lvalue_reference_t", 0),
        Map.entry("add_rvalue_reference_t", 0), Map.entry("remove_extent_t", 0), Map.entry("remove_all_extents_t", 0),
        Map.entry("remove_pointer_t", -1), Map.entry("add_pointer_t", 1));

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
     * The class whose members {@code expression->} reaches: what the class's {@code operator->} gives for a value of a
     * class - {@code std::optional<Point>}, {@code std::unique_ptr<Shape>} - the class pointed to otherwise.
     */
    public static @Nullable PsiElement scopeOfArrow(PsiElement expression) {
        return scopeOfType(arrowed(typeOf(expression), 0));
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
            // array[i], or the operator[] of a class
            CType indexed = typeOf(firstExpression(expression), next);
            return indexed.isClass() ? operatorResult(indexed, CPsiTokens.LBRACKET, next).withReference(false) : indexed.dereferenced();
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
        List<PsiElement> bindings = CDeclarations.bindingNames(target);
        if (!bindings.isEmpty()) {
            PsiElement name = reference.getLastChild();
            for (int i = 0; i < bindings.size(); i++) {
                if (name != null && bindings.get(i).getText().equals(name.getText())) {
                    return bindingType(target, i, depth);
                }
            }
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
        CType receiver = typeOf(qualifier, depth + 1);
        return type == CPsiTokens.ARROW ? arrowed(receiver, depth + 1) : receiver;
    }

    /**
     * What {@code ->} reaches through a value of {@code type}: the value pointed to, or for a value of a class what its
     * {@code operator->} returns.
     */
    private static CType arrowed(CType type, int depth) {
        if (!type.isClass() || depth > MAX_DEPTH) {
            return type;
        }
        CType returned = operatorResult(type, CPsiTokens.ARROW, depth);
        return returned.isUnknown() ? type : returned;
    }

    /**
     * What an operator of a class returns for a value of {@code type}. One returning a type not followed - the
     * {@code pointer} of {@code unique_ptr}, a {@code T} of a base class - is taken to give the first template argument,
     * as a smart pointer's {@code ->} and a container's {@code []} do.
     */
    private static CType operatorResult(CType type, IElementType symbol, int depth) {
        PsiElement scope = type.declaration() == null ? null : CNameResolver.scopeOf(type.declaration());
        PsiElement operator = scope == null ? null : operatorOf(scope, symbol, 0);
        if (operator == null) {
            return CType.UNKNOWN;
        }
        CType returned = substitute(declaredType(operator, depth + 1), operator, type);
        if (returned.isUnknown() || returned.isTemplateParameter()) {
            return type.arguments().isEmpty() ? CType.UNKNOWN
                : type.arguments().get(0).withPointers(type.arguments().get(0).pointers() + (symbol == CPsiTokens.ARROW ? 1 : 0));
        }
        return returned;
    }

    /**
     * The operator of a class, in its body, a section of it or a base class; operators have no name to look up.
     */
    private static @Nullable PsiElement operatorOf(PsiElement aggregate, IElementType symbol, int depth) {
        for (PsiElement member = aggregate.getFirstChild(); member != null; member = member.getNextSibling()) {
            if (member instanceof CPsiAccessorOwner) {
                PsiElement found = operatorOf(member, symbol, depth);
                if (found != null) {
                    return found;
                }
            }
            else if (member instanceof CPsiDeclarationMethod) {
                var keyword = member.getNode().findChildByType(CPsiTokens.OPERATOR_KEYWORD);
                PsiElement next = keyword == null ? null : nextSignificant(keyword.getPsi());
                if (next != null && next.getNode().getElementType() == symbol) {
                    return member;
                }
            }
        }
        CPsiSuperClassList bases = depth < MAX_BASE_DEPTH ? CDeclarations.childOfType(aggregate, CPsiSuperClassList.class) : null;
        if (bases != null) {
            for (CPsiSuperClass base : bases.getSuperClasses()) {
                PsiElement baseScope = CNameResolver.scopeOf(CNameResolver.resolveFirst(base));
                if (baseScope != null) {
                    PsiElement found = operatorOf(CScopes.definitionOf(baseScope, base.getContainingFile()), symbol, depth + 1);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    private static CType callType(PsiElement call, int depth) {
        if (CDeclarations.childOfType(call, CPsiParameterList.class) != null) {
            // a lambda
            return CType.UNKNOWN;
        }
        PsiElement callee = call.getFirstChild();
        if (callee != null && callee.getNode().getElementType() == CPsiTokens.TYPEID_KEYWORD) {
            return typeInfo(call);
        }
        if (!(callee instanceof CPsiReferenceExpression)) {
            return CType.UNKNOWN;
        }
        PsiElement target = CNameResolver.resolveFirst(callee);
        if (target == null && callee.getReference() instanceof PsiPolyVariantReference reference) {
            // a declaration and its definition, or overloads not told apart: any says what the call gives
            ResolveResult[] results = reference.multiResolve(false);
            target = results.length == 0 ? null : results[0].getElement();
        }
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
        CType receiver = receiverType(callee, depth);
        CType returned = returnType(target, call, receiver, depth);
        if (returned.isUnknown() && callee.getReference() instanceof PsiPolyVariantReference reference) {
            // the overload chosen returns what is not followed - auto f() -> decltype(...) - another may say
            for (ResolveResult result : reference.multiResolve(false)) {
                PsiElement other = result.getElement();
                if (other != null && other != target && CDeclarations.kindOf(other) == CDeclarationKind.FUNCTION) {
                    returned = returnType(other, call, receiver, depth);
                    if (!returned.isUnknown()) {
                        break;
                    }
                }
            }
        }
        return returned;
    }

    /**
     * The template arguments a call writes, {@code <Circle>} of {@code std::make_unique<Circle>(2.0)}.
     */
    private static List<CType> explicitTemplateArguments(@Nullable PsiElement callee, int depth) {
        PsiElement last = callee == null ? null : callee.getLastChild();
        while (last instanceof PsiWhiteSpace || last instanceof PsiComment) {
            last = last.getPrevSibling();
        }
        if (last == null || last.getNode().getElementType() != CPsiTokens.GT && last.getNode().getElementType() != CPsiTokens.GTGT) {
            return List.of();
        }
        List<PsiElement> tokens = new ArrayList<>();
        int level = 0;
        for (PsiElement child = last; child != null; child = child.getPrevSibling()) {
            tokens.add(0, child);
            IElementType type = child.getNode().getElementType();
            level += type == CPsiTokens.GT ? 1 : type == CPsiTokens.GTGT ? 2 : type == CPsiTokens.LT ? -1 : 0;
            if (level == 0) {
                return type == CPsiTokens.LT ? templateArguments(callee, tokens, depth + 1) : List.of();
            }
        }
        return List.of();
    }

    /**
     * What {@code typeid(x)} is of: {@code std::type_info}, when {@code <typeinfo>} is included.
     */
    private static CType typeInfo(PsiElement typeid) {
        for (PsiElement std : CNameResolver.resolveGlobal(typeid, "std", CDeclarationKind.SCOPES)) {
            PsiElement namespace = CNameResolver.scopeOf(std);
            if (namespace == null) {
                continue;
            }
            for (PsiElement typeInfo : CNameResolver.resolveMember(typeid, namespace, "type_info", CDeclarationKind.TYPES, ResolveState.initial())) {
                if (CDeclarations.isAggregate(typeInfo)) {
                    return CType.of(CScopes.definitionOf(typeInfo, typeid.getContainingFile()), List.of());
                }
            }
        }
        return CType.UNKNOWN;
    }

    private static CType returnType(PsiElement function, PsiElement call, @Nullable CType receiver, int depth) {
        CType returned = substitute(declaredType(function, depth), function, receiver);
        List<CType> explicit = explicitTemplateArguments(call.getFirstChild(), depth);
        if (!explicit.isEmpty()) {
            // make_unique<Circle>(2.0), duration_cast<milliseconds>(d)
            returned = substitute(returned, CTemplates.parametersOf(function), explicit, 0);
        }
        if (returned.isTemplateParameter()) {
            returned = deduce(returned, function, argumentsOf(call), depth);
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
            for (PsiElement child = declaration.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (CDeclarations.isAggregate(child)) {
                    // union { double radius; ... } u;
                    return CType.of(child, List.of()).withPointers(countPointers(declaration)).withReference(reference);
                }
            }
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

    /**
     * The type of the {@code index}th name of a structured binding: that template argument of what it takes apart - a
     * {@code std::pair}, a {@code std::tuple} - or of the elements of the range a {@code for} goes through, the key and
     * the value of a {@code std::map}.
     */
    private static CType bindingType(PsiElement variable, int index, int depth) {
        PsiElement initializer = initializerOf(variable);
        if (initializer != null) {
            return argument(typeOf(initializer, depth), index);
        }
        PsiElement range = rangeOf(variable);
        if (range == null) {
            return CType.UNKNOWN;
        }
        return argument(elementOf(typeOf(range, depth), depth), index);
    }

    private static CType argument(CType type, int index) {
        return type.isClass() && index < type.arguments().size() ? type.arguments().get(index) : CType.UNKNOWN;
    }

    private static @Nullable PsiElement initializerOf(PsiElement variable) {
        boolean afterAssign = false;
        for (PsiElement child = variable.getFirstChild(); child != null; child = child.getNextSibling()) {
            IElementType type = child.getNode().getElementType();
            if (type == CPsiTokens.EQ) {
                afterAssign = true;
            }
            else if (child instanceof CPsiExpression && afterAssign) {
                return child;
            }
            else if (child instanceof CPsiInitializerList list) {
                return CDeclarations.childOfType(list, CPsiExpression.class);
            }
        }
        return null;
    }

    /**
     * What a range-based {@code for} goes through, for its variable.
     */
    private static @Nullable PsiElement rangeOf(PsiElement variable) {
        PsiElement colon = nextSignificant(variable);
        if (colon != null && colon.getNode().getElementType() == CPsiTokens.COLON && variable.getParent() instanceof CPsiForStatement) {
            for (PsiElement range = colon.getNextSibling(); range != null; range = range.getNextSibling()) {
                if (range instanceof CPsiExpression) {
                    return range;
                }
            }
        }
        return null;
    }

    private static CType autoType(PsiElement variable, int depth) {
        PsiElement initializer = initializerOf(variable);
        if (initializer != null) {
            return typeOf(initializer, depth).withReference(false);
        }
        // for (auto& point : points): an element of what it goes through
        PsiElement range = rangeOf(variable);
        return range == null ? CType.UNKNOWN : elementOf(typeOf(range, depth), depth);
    }

    /**
     * What a range-for over a value of {@code type} goes through: the elements of an array, or of a container class -
     * its {@code value_type}, {@code pair<const K, V>} of {@code std::map<K, V>}, or else its first template argument.
     */
    private static CType elementOf(CType range, int depth) {
        if (range.pointers() == 0 && range.isClass()) {
            PsiElement scope = CNameResolver.scopeOf(range.declaration());
            if (scope != null) {
                for (PsiElement member : CDeclarations.ownMembersOf(scope)) {
                    if ("value_type".equals(CDeclarations.introducedName(member)) && CDeclarations.isTypeAlias(member)) {
                        CType element = substitute(declaredTypeOfAlias(member, depth + 1), member, range);
                        if (!element.isUnknown() && !element.isTemplateParameter()) {
                            return element;
                        }
                        break;
                    }
                }
            }
            if (!range.arguments().isEmpty()) {
                return range.arguments().get(0);
            }
        }
        return range.dereferenced();
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
        CType enabled = enableIfType(typeRef, depth);
        if (enabled != null) {
            return enabled;
        }
        CType declared = decltypeType(typeRef, depth);
        if (declared != null) {
            return declared;
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
            String name = CDeclarations.nameOf(target);
            if ("enable_if_t".equals(name) || "__enable_if_t".equals(name)) {
                // enable_if_t<condition, T> is T where it is defined at all
                List<CType> arguments = templateArguments(typeRef, depth);
                return arguments.size() >= 2 ? arguments.get(1) : CType.of(Builtin.VOID);
            }
            Integer pointers = name == null ? null : TRAIT_POINTERS.get(name);
            if (pointers != null) {
                // remove_cv_t<T>, decay_t<T>: T, as far as its members go
                List<CType> arguments = templateArguments(typeRef, depth);
                if (arguments.isEmpty() || arguments.get(0).isUnknown()) {
                    return CType.UNKNOWN;
                }
                CType argument = arguments.get(0).withReference(false);
                return argument.withPointers(Math.max(0, argument.pointers() + pointers));
            }
            CType aliased = declaredTypeOfAlias(target, depth);
            List<CTemplates.Parameter> parameters = CTemplates.parametersOf(target);
            if (!parameters.isEmpty() && !aliased.isUnknown()) {
                // an alias template, template<typename T> using Ptr = T*; - Ptr<int> is int*
                List<CType> arguments = templateArguments(typeRef, depth);
                if (!arguments.isEmpty()) {
                    aliased = substitute(aliased, parameters, arguments, 0);
                }
            }
            return aliased;
        }
        if (CDeclarations.isAggregate(target)) {
            return CType.of(CScopes.definitionOf(target, typeRef.getContainingFile()), templateArguments(typeRef, depth));
        }
        return CType.UNKNOWN;
    }

    /**
     * {@code decltype(points)}: the declared type of the variable named; {@code null} for a type reference which is no
     * {@code decltype}, unknown for one of an expression which is not a name.
     */
    private static @Nullable CType decltypeType(CPsiTypeRef typeRef, int depth) {
        PsiElement keyword = typeRef.getFirstChild();
        while (keyword instanceof PsiWhiteSpace || keyword instanceof PsiComment) {
            keyword = keyword.getNextSibling();
        }
        if (keyword == null || keyword.getNode().getElementType() != CPsiTokens.DECLTYPE_KEYWORD) {
            return null;
        }
        List<PsiElement> names = new ArrayList<>();
        boolean global = false;
        int level = 0;
        for (PsiElement child = keyword.getNextSibling(); child != null; child = child.getNextSibling()) {
            if (child instanceof PsiWhiteSpace || child instanceof PsiComment) {
                continue;
            }
            IElementType type = child.getNode().getElementType();
            if (type == CPsiTokens.LPARENTH) {
                if (level++ > 0) {
                    return CType.UNKNOWN;
                }
            }
            else if (type == CPsiTokens.RPARENTH) {
                break;
            }
            else if (type == CPsiTokens.IDENTIFIER && level == 1) {
                names.add(child);
            }
            else if (type == CPsiTokens.DOUBLE_COLON && level == 1) {
                global |= names.isEmpty();
            }
            else {
                // decltype(auto), decltype(a + b)
                return CType.UNKNOWN;
            }
        }
        if (names.isEmpty()) {
            return CType.UNKNOWN;
        }
        for (PsiElement target : CNameResolver.resolveChainTargets(typeRef, new CNameResolver.NameChain(names, global), CDeclarationKind.ALL)) {
            if (CDeclarations.kindOf(target) == CDeclarationKind.VARIABLE) {
                return declaredType(target, depth + 1);
            }
        }
        return CType.UNKNOWN;
    }

    /**
     * {@code typename enable_if<condition, T>::type}: {@code T} where it is defined at all; {@code null} for a type
     * reference of any other form.
     */
    private static @Nullable CType enableIfType(CPsiTypeRef typeRef, int depth) {
        List<PsiElement> tokens = new ArrayList<>();
        for (PsiElement child = typeRef.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof PsiWhiteSpace) && !(child instanceof PsiComment)) {
                tokens.add(child);
            }
        }
        int start = -1;
        for (int i = 0; i + 1 < tokens.size(); i++) {
            if (tokens.get(i).getText().equals("enable_if") && tokens.get(i + 1).getNode().getElementType() == CPsiTokens.LT) {
                start = i + 1;
                break;
            }
        }
        if (start < 0) {
            return null;
        }
        int level = 0;
        for (int i = start; i < tokens.size(); i++) {
            IElementType type = tokens.get(i).getNode().getElementType();
            level += type == CPsiTokens.LT ? 1 : type == CPsiTokens.GT ? -1 : type == CPsiTokens.GTGT ? -2 : 0;
            if (level <= 0) {
                // the name ends with ::type right after the arguments
                boolean member = i + 3 == tokens.size() && tokens.get(i + 1).getNode().getElementType() == CPsiTokens.DOUBLE_COLON
                    && tokens.get(i + 2).getText().equals("type");
                if (!member) {
                    return null;
                }
                List<CType> arguments = templateArguments(typeRef, tokens.subList(start, i + 1), depth + 1);
                return arguments.size() >= 2 ? arguments.get(1) : CType.of(Builtin.VOID);
            }
        }
        return null;
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
        String name = CDeclarations.nameOf(alias);
        if (name != null && name.endsWith("iterator")) {
            // an iterator class of the library - __normal_iterator<pointer, vector> - says nothing of its elements
            CType element = containerMemberType(alias, depth);
            if (!element.isUnknown()) {
                return element;
            }
        }
        CPsiTypeRef typeRef = CDeclarations.typeRefOf(alias);
        if (typeRef == null) {
            return CType.UNKNOWN;
        }
        CType type = typeOfTypeRef(typeRef, depth + 1);
        if (type.isUnknown()) {
            return containerMemberType(alias, depth);
        }
        return type.withPointers(type.pointers() + countPointers(alias));
    }

    /**
     * The {@code reference}, {@code pointer} and {@code iterator} of a container, which it takes from its allocator's
     * traits or its implementation - too far to follow: a {@code value_type&}, and a {@code value_type*} for the others,
     * as the requirements of a container have them and as an iterator is used.
     */
    private static CType containerMemberType(PsiElement alias, int depth) {
        String name = CDeclarations.nameOf(alias);
        boolean reference = "reference".equals(name) || "const_reference".equals(name);
        // an iterator is used as a pointer to an element is: *it, it->first
        boolean pointer = "pointer".equals(name) || "const_pointer".equals(name) || name != null && name.endsWith("iterator");
        PsiElement owner = reference || pointer ? CDeclarations.enclosingAggregate(alias) : null;
        if (owner == null) {
            return CType.UNKNOWN;
        }
        for (PsiElement member : CDeclarations.ownMembersOf(owner)) {
            if ("value_type".equals(CDeclarations.introducedName(member)) && CDeclarations.isTypeAlias(member)) {
                CType valueType = declaredTypeOfAlias(member, depth + 1);
                return valueType.isUnknown() || reference ? valueType : valueType.withPointers(valueType.pointers() + 1);
            }
        }
        return CType.UNKNOWN;
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
        List<PsiElement> tokens = new ArrayList<>();
        for (PsiElement child = typeRef.getFirstChild(); child != null; child = child.getNextSibling()) {
            tokens.add(child);
        }
        return templateArguments(typeRef, tokens, depth);
    }

    /**
     * The arguments of the first {@code <...>} of {@code tokens}.
     */
    private static List<CType> templateArguments(PsiElement typeRef, List<PsiElement> tokens, int depth) {
        List<CType> arguments = new ArrayList<>();
        if (depth > MAX_DEPTH) {
            return arguments;
        }
        int level = 0;
        List<PsiElement> argument = new ArrayList<>();
        for (PsiElement child : tokens) {
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
        if (level > 0 && !argument.isEmpty()) {
            // the >> of vector<unique_ptr<T>> closed this list with the one around it
            arguments.add(argumentType(typeRef, argument, depth));
        }
        return arguments;
    }

    private static CType argumentType(PsiElement typeRef, List<PsiElement> tokens, int depth) {
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
            : CDeclarations.isAggregate(target) ? CType.of(CScopes.definitionOf(target, typeRef.getContainingFile()), head.size() < tokens.size() ? templateArguments(typeRef, tokens.subList(head.size(), tokens.size()), depth + 1) : List.of())
            : CType.UNKNOWN;
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
        if (receiver == null || receiver.arguments().isEmpty() || !type.isTemplateParameter() && type.arguments().isEmpty()) {
            return type;
        }
        PsiElement owner = CDeclarations.enclosingAggregate(member);
        if (owner == null || receiver.declaration() == null || !owner.getManager().areElementsEquivalent(owner, receiver.declaration())) {
            return type;
        }
        return substitute(type, CTemplates.parametersOf(owner), receiver.arguments(), 0);
    }

    /**
     * {@code type} with the parameters replaced by the arguments - in its template arguments too, the {@code _Key} of
     * {@code pair<const _Key, _Tp>}.
     */
    private static CType substitute(CType type, List<CTemplates.Parameter> parameters, List<CType> arguments, int depth) {
        if (type.isTemplateParameter()) {
            for (int i = 0; i < parameters.size() && i < arguments.size(); i++) {
                if (parameters.get(i).name() == type.declaration()) {
                    return combine(arguments.get(i), type);
                }
            }
            return type;
        }
        if (type.arguments().isEmpty() || depth > MAX_DEPTH) {
            return type;
        }
        List<CType> substituted = new ArrayList<>(type.arguments().size());
        for (CType argument : type.arguments()) {
            substituted.add(substitute(argument, parameters, arguments, depth + 1));
        }
        return new CType(type.builtin(), type.declaration(), type.pointers(), type.reference(), List.copyOf(substituted));
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
