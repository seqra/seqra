package org.opentaint.org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.AddExpr
import org.opentaint.semgrep.pattern.Annotation
import org.opentaint.semgrep.pattern.BoolConstant
import org.opentaint.semgrep.pattern.CatchStatement
import org.opentaint.semgrep.pattern.ClassDeclaration
import org.opentaint.semgrep.pattern.DeepExpr
import org.opentaint.semgrep.pattern.Ellipsis
import org.opentaint.semgrep.pattern.EllipsisArgumentPrefix
import org.opentaint.semgrep.pattern.EllipsisMetavar
import org.opentaint.semgrep.pattern.EllipsisMethodInvocations
import org.opentaint.semgrep.pattern.EmptyPatternSequence
import org.opentaint.semgrep.pattern.FieldAccess
import org.opentaint.semgrep.pattern.FormalArgument
import org.opentaint.semgrep.pattern.Identifier
import org.opentaint.semgrep.pattern.ImportStatement
import org.opentaint.semgrep.pattern.IntLiteral
import org.opentaint.semgrep.pattern.Metavar
import org.opentaint.semgrep.pattern.MethodArguments
import org.opentaint.semgrep.pattern.MethodDeclaration
import org.opentaint.semgrep.pattern.MethodInvocation
import org.opentaint.semgrep.pattern.Modifier
import org.opentaint.semgrep.pattern.Name
import org.opentaint.semgrep.pattern.NamedValue
import org.opentaint.semgrep.pattern.NoArgs
import org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.semgrep.pattern.NullLiteral
import org.opentaint.semgrep.pattern.ObjectCreation
import org.opentaint.semgrep.pattern.PatternArgumentPrefix
import org.opentaint.semgrep.pattern.PatternSequence
import org.opentaint.semgrep.pattern.ReturnStmt
import org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.semgrep.pattern.StaticFieldAccess
import org.opentaint.semgrep.pattern.StringEllipsis
import org.opentaint.semgrep.pattern.StringLiteral
import org.opentaint.semgrep.pattern.ThisExpr
import org.opentaint.semgrep.pattern.TypeName
import org.opentaint.semgrep.pattern.TypedMetavar
import org.opentaint.semgrep.pattern.VariableAssignment
import org.opentaint.semgrep.pattern.conversion.cartesianProductMapTo

interface PatternRewriter {
    fun SemgrepJavaPattern.rewrite(): List<SemgrepJavaPattern> = when (this) {
        is MethodArguments -> rewriteMethodArguments()
        is AddExpr -> rewriteAddExpr()
        is Annotation -> rewriteAnnotation()
        is BoolConstant -> rewriteBoolConstant()
        Ellipsis -> rewriteEllipsis()
        EmptyPatternSequence -> rewriteEmptyPatternSequence()
        is Identifier -> rewriteIdentifier()
        is Metavar -> rewriteMetavar()
        is MethodDeclaration -> rewriteMethodDeclaration()

        is EllipsisMethodInvocations -> rewriteEllipsisMethodInvocations()
        is FieldAccess -> rewriteFieldAccess()
        is StaticFieldAccess -> rewriteStaticFieldAccess()
        is FormalArgument -> rewriteFormalArgument()
        is NamedValue -> rewriteNamedValue()

        is MethodInvocation -> rewriteMethodInvocation()
        is ObjectCreation -> rewriteObjectCreation()
        is PatternSequence -> rewritePatternSequence()
        is ReturnStmt -> rewriteReturnStmt()
        StringEllipsis -> rewriteStringEllipsis()
        is StringLiteral -> rewriteStringLiteral()
        ThisExpr -> rewriteThisExpr()
        is TypedMetavar -> rewriteTypedMetavar()
        is VariableAssignment -> rewriteVariableAssignment()
        is ClassDeclaration -> rewriteClassDeclaration()
        is NullLiteral -> rewriteNullLiteral()
        is IntLiteral -> rewriteIntLiteral()
        is ImportStatement -> rewriteImportStatement()
        is CatchStatement -> rewriteCatchStatement()
        is DeepExpr -> rewriteDeepExpr()
        is EllipsisMetavar -> rewriteEllipsisMetavar()
    }

    fun AddExpr.rewriteAddExpr(): List<SemgrepJavaPattern> {
        val newLeftOptions = left.rewrite()
        val newRightOptions = right.rewrite()

        return newLeftOptions.flatMap { newLeft ->
            newRightOptions.flatMap { newRight ->
                createAddExpr(newLeft, newRight)
            }
        }
    }

    fun MethodDeclaration.rewriteMethodDeclaration(): List<SemgrepJavaPattern> {
        val newName = name.rewriteName()
        val newReturnType = returnType?.rewriteTypeName()
        val newArgsOptions = args.rewriteMethodArguments()
        val newBodyOptions = body.rewrite()
        val newModifiersOptions = modifiers.map { it.rewriteModifier() }.cartesianProductMapTo { it.toList() }

        return newArgsOptions.flatMap { newArgs ->
            newBodyOptions.flatMap { newBody ->
                newModifiersOptions.flatMap { newModifiers ->
                    createMethodDeclaration(newName, newReturnType, newArgs, newBody, newModifiers)
                }
            }
        }
    }

    fun ClassDeclaration.rewriteClassDeclaration(): List<SemgrepJavaPattern> {
        val newName = name.rewriteName()
        val newExtends = extends?.rewriteTypeName()
        val newImplements = implements.map { it.rewriteTypeName() }
        val newModifiersOptions = modifiers.map { it.rewriteModifier() }.cartesianProductMapTo { it.toList() }
        val newBodyOptions = body.rewrite()

        return newModifiersOptions.flatMap { newModifiers ->
            newBodyOptions.flatMap { newBody ->
                createClassDeclaration(newName, newExtends, newImplements, newModifiers, newBody)
            }
        }
    }

    fun NamedValue.rewriteNamedValue(): List<SemgrepJavaPattern> {
        val newName = name.rewriteName()
        val newValues = value.rewrite()

        return newValues.flatMap { newValue ->
            createNamedValue(newName, newValue)
        }
    }

    fun createNamedValue(name: Name, value: SemgrepJavaPattern): List<NamedValue> = listOf(NamedValue(name, value))

    fun createAddExpr(left: SemgrepJavaPattern, right: SemgrepJavaPattern): List<SemgrepJavaPattern> =
        listOf(AddExpr(left, right))

    fun createAnnotation(name: TypeName, args: MethodArguments): List<Annotation> = listOf(Annotation(name, args))

    fun MethodArguments.rewriteMethodArguments(): List<MethodArguments> = when (this) {
        is EllipsisArgumentPrefix -> rest.rewriteMethodArguments().flatMap(::createEllipsisArgumentPrefix)
        NoArgs -> createNoArgs()
        is PatternArgumentPrefix -> {
            val newArgumentOptions = argument.rewrite()
            val newRestOptions = rest.rewriteMethodArguments()

            newArgumentOptions.flatMap { newArgument ->
                newRestOptions.flatMap { newRest ->
                    createPatternArgumentPrefix(newArgument, newRest)
                }
            }
        }
    }

    fun EllipsisMethodInvocations.rewriteEllipsisMethodInvocations(): List<SemgrepJavaPattern> =
        obj.rewrite().flatMap { newObj ->
            createEllipsisMethodInvocations(newObj)
        }

    fun FieldAccess.rewriteFieldAccess(): List<SemgrepJavaPattern> {
        val newFieldName = fieldName.rewriteName()
        val newObjOptions = obj.rewriteObject()

        return newObjOptions.flatMap { newObj ->
            createFieldAccess(newFieldName, newObj)
        }
    }


    fun StaticFieldAccess.rewriteStaticFieldAccess(): List<SemgrepJavaPattern> =
        createStaticFieldAccess(fieldName.rewriteName(), classTypeName.rewriteTypeName())

    fun createStaticFieldAccess(fieldName: Name, classTypeName: TypeName): List<SemgrepJavaPattern> =
        listOf(StaticFieldAccess(fieldName, classTypeName))

    fun FormalArgument.rewriteFormalArgument(): List<SemgrepJavaPattern> {
        val newName = name.rewriteName()
        val newType = type.rewriteTypeName()
        val newModifiersOptions = modifiers.map { it.rewriteModifier() }.cartesianProductMapTo { it.toList() }

        return newModifiersOptions.flatMap { newModifiers ->
            createFormalArgument(newName, newType, newModifiers)
        }
    }

    fun MethodInvocation.rewriteMethodInvocation(): List<SemgrepJavaPattern> {
        val newMethodName = methodName.rewriteName()
        val newObjOptions = obj?.rewrite() ?: listOf(null)
        val newArgsOptions = args.rewriteMethodArguments()

        return newObjOptions.flatMap { newObj ->
            newArgsOptions.flatMap { newArgs ->
                createMethodInvocation(newMethodName, newObj, newArgs)
            }
        }
    }

    fun ObjectCreation.rewriteObjectCreation(): List<SemgrepJavaPattern> {
        val newType = type.rewriteTypeName()
        val newArgsOptions = args.rewriteMethodArguments()

        return newArgsOptions.flatMap { newArgs ->
            createObjectCreation(newType, newArgs)
        }
    }

    fun PatternSequence.rewritePatternSequence(): List<SemgrepJavaPattern> {
        val newFirstOptions = first.rewrite()
        val newSecondOptions = second.rewrite()

        return newFirstOptions.flatMap { newFirst ->
            newSecondOptions.flatMap { newSecond ->
                createPatternSequence(newFirst, newSecond)
            }
        }
    }

    fun ReturnStmt.rewriteReturnStmt(): List<SemgrepJavaPattern> {
        val newValues = value?.rewrite() ?: listOf(null)

        return newValues.flatMap { newValue ->
            createReturnStmt(newValue)
        }
    }

    fun StringLiteral.rewriteStringLiteral(): List<SemgrepJavaPattern> =
        createStringLiteral(content.rewriteName())

    fun TypedMetavar.rewriteTypedMetavar(): List<SemgrepJavaPattern> =
        createTypedMetavar(name, type.rewriteTypeName())

    fun VariableAssignment.rewriteVariableAssignment(): List<SemgrepJavaPattern> {
        val newType = type?.rewriteTypeName()
        val newVariableOptions = variable.rewrite()
        val newValueOptions = value?.rewrite() ?: listOf(null)

        return newVariableOptions.flatMap { newVariable ->
            newValueOptions.flatMap { newValue ->
                createVariableAssignment(newType, newVariable, newValue)
            }
        }
    }

    fun BoolConstant.rewriteBoolConstant(): List<SemgrepJavaPattern> = listOf(this)
    fun IntLiteral.rewriteIntLiteral(): List<SemgrepJavaPattern> = listOf(this)
    fun NullLiteral.rewriteNullLiteral(): List<SemgrepJavaPattern> = listOf(this)
    fun Identifier.rewriteIdentifier(): List<SemgrepJavaPattern> = listOf(this)
    fun Metavar.rewriteMetavar(): List<SemgrepJavaPattern> = listOf(this)
    fun EllipsisMetavar.rewriteEllipsisMetavar(): List<SemgrepJavaPattern> = listOf(this)
    fun rewriteEllipsis(): List<SemgrepJavaPattern> = listOf(Ellipsis)
    fun rewriteStringEllipsis(): List<SemgrepJavaPattern> = listOf(StringEllipsis)
    fun rewriteThisExpr(): List<SemgrepJavaPattern> = listOf(ThisExpr)
    fun rewriteEmptyPatternSequence(): List<SemgrepJavaPattern> = listOf(EmptyPatternSequence)

    fun TypeName.rewriteTypeName(): TypeName =
        TypeName(
            dotSeparatedParts = dotSeparatedParts.map { it.rewriteName() },
            typeArgs = typeArgs.map { it.rewriteTypeName() }
        )


    fun Name.rewriteName(): Name = this

    fun createEllipsisArgumentPrefix(rest: MethodArguments): List<MethodArguments> =
        listOf(EllipsisArgumentPrefix(rest))

    fun createNoArgs(): List<MethodArguments> = listOf(NoArgs)

    fun createPatternArgumentPrefix(argument: SemgrepJavaPattern, rest: MethodArguments): List<MethodArguments> =
        listOf(PatternArgumentPrefix(argument, rest))

    fun createEllipsisMethodInvocations(obj: SemgrepJavaPattern): List<SemgrepJavaPattern> =
        listOf(EllipsisMethodInvocations(obj))

    fun createFieldAccess(fieldName: Name, obj: FieldAccess.Object): List<SemgrepJavaPattern> =
        listOf(FieldAccess(fieldName, obj))

    fun createFormalArgument(name: Name, type: TypeName, modifiers: List<Modifier>): List<SemgrepJavaPattern> =
        listOf(FormalArgument(name, type, modifiers))

    fun createMethodDeclaration(
        name: Name,
        returnType: TypeName?,
        args: MethodArguments,
        body: SemgrepJavaPattern,
        modifiers: List<Modifier>
    ): List<SemgrepJavaPattern> = listOf(MethodDeclaration(name, returnType, args, body, modifiers))

    fun createClassDeclaration(
        name: Name,
        extends: TypeName?,
        implements: List<TypeName>,
        modifiers: List<Modifier>,
        body: SemgrepJavaPattern
    ): List<SemgrepJavaPattern> = listOf(ClassDeclaration(name, extends, implements, modifiers, body))

    fun createMethodInvocation(
        methodName: Name,
        obj: SemgrepJavaPattern?,
        args: MethodArguments
    ): List<SemgrepJavaPattern> = listOf(MethodInvocation(methodName, obj, args))

    fun createObjectCreation(type: TypeName, args: MethodArguments): List<SemgrepJavaPattern> =
        listOf(ObjectCreation(type, args))

    fun createPatternSequence(first: SemgrepJavaPattern, second: SemgrepJavaPattern): List<SemgrepJavaPattern> =
        listOf(PatternSequence(first, second))

    fun createReturnStmt(value: SemgrepJavaPattern?): List<SemgrepJavaPattern> = listOf(ReturnStmt(value))
    fun createStringLiteral(content: Name): List<SemgrepJavaPattern> = listOf(StringLiteral(content))
    fun createTypedMetavar(name: String, type: TypeName): List<SemgrepJavaPattern> = listOf(TypedMetavar(name, type))

    fun createVariableAssignment(
        type: TypeName?,
        variable: SemgrepJavaPattern,
        value: SemgrepJavaPattern?
    ): List<SemgrepJavaPattern> = listOf(VariableAssignment(type, variable, value))

    fun FieldAccess.Object.rewriteObject(): List<FieldAccess.Object> = when (this) {
        is FieldAccess.ObjectPattern -> rewriteObjectPattern()
        FieldAccess.SuperObject -> rewriteSuperObject()
    }

    fun rewriteSuperObject(): List<FieldAccess.Object> = listOf(FieldAccess.SuperObject)

    fun FieldAccess.ObjectPattern.rewriteObjectPattern(): List<FieldAccess.Object> =
        pattern.rewrite().flatMap { newPattern ->
            createObjectPattern(newPattern)
        }

    fun createObjectPattern(pattern: SemgrepJavaPattern): List<FieldAccess.Object> =
        listOf(FieldAccess.ObjectPattern(pattern))

    fun Modifier.rewriteModifier(): List<Modifier> = when (this) {
        is Annotation -> rewriteAnnotation()
    }

    fun Annotation.rewriteAnnotation(): List<Annotation> {
        val newName = name.rewriteTypeName()
        val newArgsOptions = args.rewriteMethodArguments()

        return newArgsOptions.flatMap { newArgs ->
            createAnnotation(newName, newArgs)
        }
    }

    fun ImportStatement.rewriteImportStatement(): List<SemgrepJavaPattern> =
        createImportStatement(
            dotSeparatedParts = dotSeparatedParts.map { it.rewriteName() },
            isConcrete = isConcrete
        )

    fun createImportStatement(dotSeparatedParts: List<Name>, isConcrete: Boolean): List<SemgrepJavaPattern> =
        listOf(ImportStatement(dotSeparatedParts, isConcrete))

    fun CatchStatement.rewriteCatchStatement(): List<SemgrepJavaPattern> {
        val newExceptionTypes = exceptionTypes.map { it.rewriteTypeName() }
        val newExceptionVariable = exceptionVariable.rewriteName()
        val newHandlerBlockOptions = handlerBlock.rewrite()

        return newHandlerBlockOptions.flatMap { newHandlerBlock ->
            createCatchStatement(newExceptionTypes, newExceptionVariable, newHandlerBlock)
        }
    }

    fun createCatchStatement(
        exceptionTypes: List<TypeName>,
        exceptionVariable: Name,
        handlerBlock: SemgrepJavaPattern
    ): List<SemgrepJavaPattern> = listOf(CatchStatement(exceptionTypes, exceptionVariable, handlerBlock))

    fun DeepExpr.rewriteDeepExpr(): List<SemgrepJavaPattern> = nestedExpr.rewrite().flatMap { newNestedExpr ->
        createDeepExpr(newNestedExpr)
    }

    fun createDeepExpr(nestedExpr: SemgrepJavaPattern): List<SemgrepJavaPattern> = listOf(DeepExpr(nestedExpr))
}

open class RewriteException(message: String) : Exception(message) {
    override fun fillInStackTrace(): Throwable = this
}

inline fun PatternRewriter.safeRewrite(
    pattern: SemgrepJavaPattern,
    onException: (RewriteException) -> Nothing,
): List<SemgrepJavaPattern> = try {
    pattern.rewrite()
} catch (ex: RewriteException) {
    onException(ex)
}

inline fun PatternRewriter.safeRewrite(
    rule: NormalizedSemgrepRule,
    onException: (RewriteException) -> Nothing,
): List<NormalizedSemgrepRule> {
    val newPatternsOptions = rule.patterns
        .map { safeRewrite(it, onException) }
        .cartesianProductMapTo { it.toList() }

    val newPatternInsidesOptions = rule.patternInsides
        .map { safeRewrite(it, onException) }
        .cartesianProductMapTo { it.toList() }

    val newPatternNots = rule.patternNots
        .flatMap { safeRewrite(it, onException) }

    val newPatternNotInsides = rule.patternNotInsides
        .flatMap { safeRewrite(it, onException) }

    return newPatternsOptions.flatMap { newPatterns ->
        newPatternInsidesOptions.map { newPatternInsides ->
            NormalizedSemgrepRule(
                patterns = newPatterns,
                patternNots = newPatternNots,
                patternInsides = newPatternInsides,
                patternNotInsides = newPatternNotInsides
            )
        }
    }
}