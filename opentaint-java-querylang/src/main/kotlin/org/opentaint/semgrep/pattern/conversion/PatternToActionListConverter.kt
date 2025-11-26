package org.opentaint.org.opentaint.semgrep.pattern.conversion

import org.opentaint.org.opentaint.semgrep.pattern.AddExpr
import org.opentaint.org.opentaint.semgrep.pattern.Annotation
import org.opentaint.org.opentaint.semgrep.pattern.BoolConstant
import org.opentaint.org.opentaint.semgrep.pattern.CatchStatement
import org.opentaint.org.opentaint.semgrep.pattern.ClassDeclaration
import org.opentaint.org.opentaint.semgrep.pattern.ConcreteName
import org.opentaint.org.opentaint.semgrep.pattern.DeepExpr
import org.opentaint.org.opentaint.semgrep.pattern.Ellipsis
import org.opentaint.org.opentaint.semgrep.pattern.EllipsisArgumentPrefix
import org.opentaint.org.opentaint.semgrep.pattern.EllipsisMetavar
import org.opentaint.org.opentaint.semgrep.pattern.EllipsisMethodInvocations
import org.opentaint.org.opentaint.semgrep.pattern.EmptyPatternSequence
import org.opentaint.org.opentaint.semgrep.pattern.FieldAccess
import org.opentaint.org.opentaint.semgrep.pattern.FormalArgument
import org.opentaint.org.opentaint.semgrep.pattern.Identifier
import org.opentaint.org.opentaint.semgrep.pattern.ImportStatement
import org.opentaint.org.opentaint.semgrep.pattern.IntLiteral
import org.opentaint.org.opentaint.semgrep.pattern.Metavar
import org.opentaint.org.opentaint.semgrep.pattern.MetavarName
import org.opentaint.org.opentaint.semgrep.pattern.MethodArguments
import org.opentaint.org.opentaint.semgrep.pattern.MethodDeclaration
import org.opentaint.org.opentaint.semgrep.pattern.MethodInvocation
import org.opentaint.org.opentaint.semgrep.pattern.Modifier
import org.opentaint.org.opentaint.semgrep.pattern.NamedValue
import org.opentaint.org.opentaint.semgrep.pattern.NoArgs
import org.opentaint.org.opentaint.semgrep.pattern.NullLiteral
import org.opentaint.org.opentaint.semgrep.pattern.ObjectCreation
import org.opentaint.org.opentaint.semgrep.pattern.PatternArgumentPrefix
import org.opentaint.org.opentaint.semgrep.pattern.PatternSequence
import org.opentaint.org.opentaint.semgrep.pattern.ReturnStmt
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.org.opentaint.semgrep.pattern.StringEllipsis
import org.opentaint.org.opentaint.semgrep.pattern.StringLiteral
import org.opentaint.org.opentaint.semgrep.pattern.ThisExpr
import org.opentaint.org.opentaint.semgrep.pattern.TypeName
import org.opentaint.org.opentaint.semgrep.pattern.TypedMetavar
import org.opentaint.org.opentaint.semgrep.pattern.VariableAssignment
import org.opentaint.org.opentaint.semgrep.pattern.conversion.ParamCondition.StringValueMetaVar
import org.opentaint.org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifier
import org.opentaint.org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifierValue
import org.opentaint.org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureName

class PatternToActionListConverter: ActionListBuilder {
    private var nextArtificialMetavarId = 0

    private fun provideArtificialMetavar(): String {
        return "\$ARTIFICIAL_${nextArtificialMetavarId++}"
    }

    val failedTransformations = mutableMapOf<String, Int>()

    private fun transformationFailed(reason: String): Nothing {
        throw TransformationFailed(reason)
    }

    override fun createActionList(pattern: SemgrepJavaPattern): SemgrepPatternActionList? = try {
        transformPatternToActionList(pattern)
    } catch (ex: TransformationFailed) {
        val reason = ex.message
        val oldValue = failedTransformations.getOrDefault(reason, 0)
        failedTransformations[reason] = oldValue + 1
        null
    }

    private fun transformPatternToActionList(pattern: SemgrepJavaPattern): SemgrepPatternActionList =
        when (pattern) {
            Ellipsis -> SemgrepPatternActionList(emptyList(), hasEllipsisInTheEnd = true, hasEllipsisInTheBeginning = true)
            is PatternSequence -> transformPatternSequence(pattern)
            is MethodInvocation -> transformMethodInvocation(pattern)
            is ObjectCreation -> transformObjectCreation(pattern)
            is VariableAssignment -> transformVariableAssignment(pattern)
            is MethodDeclaration -> transformMethodDeclaration(pattern)
            is ClassDeclaration -> transformClassDeclaration(pattern)
            is EllipsisMethodInvocations,
            is AddExpr,
            is BoolConstant,
            EmptyPatternSequence,
            is FieldAccess,
            is FormalArgument,
            is Identifier,
            is Metavar,
            is MethodArguments,
            is ReturnStmt,
            StringEllipsis,
            is StringLiteral,
            ThisExpr,
            is TypedMetavar,
            is Annotation,
            is NamedValue,
            is NullLiteral,
            is ImportStatement,
            is CatchStatement,
            is DeepExpr,
            is EllipsisMetavar,
            is IntLiteral -> {
                transformationFailed(pattern::class.java.simpleName)
            }
        }

    private fun transformPatternIntoParamCondition(pattern: SemgrepJavaPattern): ParamCondition? {
        return when (pattern) {
            is BoolConstant -> {
                SpecificBoolValue(pattern.value)
            }

            is StringLiteral -> when (val value = pattern.content) {
                is ConcreteName -> SpecificStringValue(value.name)
                is MetavarName -> StringValueMetaVar(value.metavarName)
            }

            is StringEllipsis -> {
                ParamCondition.AnyStringLiteral
            }

            is Metavar -> {
                IsMetavar(pattern.name)
            }

            is TypedMetavar -> {
                val typeName = transformTypeName(pattern.type)
                ParamCondition.And(
                    listOf(
                        IsMetavar(pattern.name), ParamCondition.TypeIs(typeName)
                    )
                )
            }

            Ellipsis,
            is AddExpr,
            is EllipsisMethodInvocations,
            EmptyPatternSequence,
            is FieldAccess,
            is FormalArgument,
            is Identifier,
            is MethodArguments,
            is MethodDeclaration,
            is MethodInvocation,
            is ObjectCreation,
            is PatternSequence,
            is ReturnStmt,
            StringEllipsis,
            ThisExpr,
            is VariableAssignment,
            is Annotation,
            is ClassDeclaration,
            is NamedValue,
            is NullLiteral,
            is ImportStatement,
            is CatchStatement,
            is DeepExpr,
            is EllipsisMetavar,
            is IntLiteral -> null

        }
    }

    private val primitiveTypeNames by lazy {
        hashSetOf("byte", "short", "char", "int", "long", "float", "double", "boolean")
    }

    private fun transformTypeName(typeName: TypeName): TypeNamePattern {
        if (typeName.typeArgs.isNotEmpty()) {
            transformationFailed("TypeName_with_type_args")
        }

        if (typeName.dotSeparatedParts.size == 1) {
            val name = typeName.dotSeparatedParts.single()
            if (name is MetavarName) return TypeNamePattern.MetaVar(name.metavarName)
        }

        val concreteNames = typeName.dotSeparatedParts.filterIsInstance<ConcreteName>()
        if (concreteNames.size == typeName.dotSeparatedParts.size) {
            if (concreteNames.size == 1) {
                val className = concreteNames.single().name
                if (className.first().isUpperCase()) {
                    return TypeNamePattern.ClassName(className)
                }

                if (className in primitiveTypeNames) {
                    return TypeNamePattern.PrimitiveName(className)
                }

                transformationFailed("TypeName_concrete_unexpected")
            }

            val fqn = concreteNames.joinToString(".") { it.name }
            return TypeNamePattern.FullyQualified(fqn)
        }

        transformationFailed("TypeName_non_concrete_unsupported")
    }

    private fun transformPatternSequence(pattern: PatternSequence): SemgrepPatternActionList {
        val first = transformPatternToActionList(pattern.first)
        val second = transformPatternToActionList(pattern.second)
        return SemgrepPatternActionList(
            first.actions + second.actions,
            hasEllipsisInTheEnd = second.hasEllipsisInTheEnd,
            hasEllipsisInTheBeginning = first.hasEllipsisInTheBeginning,
        )
    }

    private fun transformPatternIntoParamConditionWithActions(
        pattern: SemgrepJavaPattern
    ): Pair<List<SemgrepPatternAction>, ParamCondition?>? {
        if (pattern is EllipsisArgumentPrefix) {
            return null
        }

        val objCondition = transformPatternIntoParamCondition(pattern)
        if (objCondition != null) {
            return emptyList<SemgrepPatternAction>() to objCondition
        }
        val objActionList = transformPatternToActionList(pattern)
        if (objActionList.actions.isEmpty()) {
            return emptyList<SemgrepPatternAction>() to null
        }
        val result = objActionList.actions.toMutableList()
        result.removeLast()
        val lastAction = objActionList.actions.last()
        val metavar = provideArtificialMetavar()
        val newLastAction = lastAction.setResultCondition(IsMetavar(metavar))
        result += newLastAction
        return result to IsMetavar(metavar)
    }

    private fun methodArgumentsToPatternList(pattern: MethodArguments): List<SemgrepJavaPattern> {
        return when (pattern) {
            is NoArgs -> {
                emptyList()
            }
            is EllipsisArgumentPrefix -> {
                val rest = methodArgumentsToPatternList(pattern.rest)
                listOf(pattern) + rest
            }
            is PatternArgumentPrefix -> {
                val rest = methodArgumentsToPatternList(pattern.rest)
                listOf(pattern.argument) + rest
            }
        }
    }

    private fun tryConvertPatternIntoTypeName(pattern: SemgrepJavaPattern): TypeNamePattern? {
        val parts = tryExtractPatternDotSeparatedParts(pattern)?.ifEmpty { null }
            ?: return null

        // consider this a field access, not type
        if (parts.last().first().isLowerCase()) {
            return null
        }

        if (parts.size == 1) {
            return TypeNamePattern.ClassName(parts.single())
        }

        return TypeNamePattern.FullyQualified(parts.joinToString(separator = "."))
    }

    private fun transformMethodInvocation(pattern: MethodInvocation): SemgrepPatternActionList {
        val methodName = when (val name = pattern.methodName) {
            is ConcreteName -> SignatureName.Concrete(name.name)
            is MetavarName -> SignatureName.MetaVar(name.metavarName)
        }

        val actionList = mutableListOf<SemgrepPatternAction>()

        val className = pattern.obj?.let { tryConvertPatternIntoTypeName(it) }

        val objCondition = if (className == null) {
            pattern.obj?.let { objPattern ->
                val (actions, cond) = transformPatternIntoParamConditionWithActions(objPattern)
                    ?: transformationFailed("MethodInvocation_obj: ${objPattern::class.simpleName}")

                actionList += actions
                cond
            }
        } else {
            null
        }

        val (argActions, argsConditions) = generateParamConditions(pattern.args)

        actionList += argActions

        val methodInvocationAction = SemgrepPatternAction.MethodCall(
            methodName = methodName,
            result = null,
            params = argsConditions,
            obj = objCondition,
            enclosingClassName = className,
        )
        actionList += methodInvocationAction
        return SemgrepPatternActionList(actionList, hasEllipsisInTheEnd = false, hasEllipsisInTheBeginning = false)
    }

    private fun generateParamConditions(
        args: MethodArguments
    ): Pair<List<SemgrepPatternAction>, ParamConstraint> {
        val parsedArgs = methodArgumentsToPatternList(args)

        val allActions = mutableListOf<SemgrepPatternAction>()
        val patterns = mutableListOf<ParamPattern>()
        var paramIdxConcrete = true
        for ((i, arg) in parsedArgs.withIndex()) {
            if (arg is EllipsisArgumentPrefix) {
                paramIdxConcrete = false
                continue
            }

            val (actions, cond) = transformPatternIntoParamConditionWithActions(arg)
                ?: transformationFailed("ParamCondition: ${arg::class.simpleName}")

            allActions += actions

            val position = if (paramIdxConcrete) {
                ParamPosition.Concrete(i)
            } else {
                ParamPosition.Any(paramClassifier = "*-$i")
            }

            val condition = cond ?: ParamCondition.True

            if (condition is ParamCondition.True && position is ParamPosition.Any) {
                continue
            }

            patterns += ParamPattern(position, condition)
        }

        if (paramIdxConcrete) {
            val concreteConditions = patterns.map { it.condition }
            return allActions to ParamConstraint.Concrete(concreteConditions)
        }

        val anyPatterns = patterns.count { it.position is ParamPosition.Any }
        if (anyPatterns > 1) {
            transformationFailed("Multiple any params")
        }

        return allActions to ParamConstraint.Partial(patterns)
    }

    private fun transformVariableAssignment(pattern: VariableAssignment): SemgrepPatternActionList {
        if (pattern.variable is Ellipsis) {
            transformationFailed("VariableAssignment_ellipsis_variable")
        }

        val conditions = mutableListOf<ParamCondition.Atom>()
        if (pattern.type != null) {
            val typeName = transformTypeName(pattern.type)
            conditions += ParamCondition.TypeIs(typeName)
        }

        when (val v = pattern.variable) {
            is Metavar -> {
                conditions += IsMetavar(v.name)
            }

            is TypedMetavar -> {
                conditions += IsMetavar(v.name)

                val typeName = transformTypeName(v.type)
                conditions += ParamCondition.TypeIs(typeName)
            }

            else -> {
                transformationFailed("VariableAssignment_variable_not_metavar")
            }
        }

        val actions = pattern.value?.let { transformPatternToActionList(it) }?.actions.orEmpty()
        if (actions.isEmpty()) {
            transformationFailed("VariableAssignment_nothing_to_assign")
        }

        val lastAction = actions.last()
        val newLastAction = lastAction.setResultCondition(ParamCondition.And(conditions))

        return SemgrepPatternActionList(
            actions.dropLast(1) + newLastAction,
            hasEllipsisInTheEnd = false,
            hasEllipsisInTheBeginning = false,
        )
    }

    private fun transformObjectCreation(pattern: ObjectCreation): SemgrepPatternActionList {
        val className = transformTypeName(pattern.type)

        val (argActions, argConditions) = generateParamConditions(pattern.args)

        val objectCreationAction = SemgrepPatternAction.ConstructorCall(
            className,
            result = null,
            argConditions,
        )

        return SemgrepPatternActionList(
            argActions + objectCreationAction,
            hasEllipsisInTheEnd = false,
            hasEllipsisInTheBeginning = false,
        )
    }

    private fun transformClassDeclaration(pattern: ClassDeclaration): SemgrepPatternActionList {
        if (pattern.body != Ellipsis) {
            // TODO
            transformationFailed("ClassDeclaration_non-empty_class_declaration")
        }

        if (pattern.extends != null) {
            transformationFailed("ClassDeclaration_non-null_extends")
        }

        if (pattern.implements.isNotEmpty()) {
            transformationFailed("ClassDeclaration_non-empty_implements")
        }

        val nameMetavar = (pattern.name as? MetavarName)?.metavarName
            ?: transformationFailed("ClassDeclaration_name_is_not_metavar")

        val classModifiers = pattern.modifiers.map { transformModifier(it) }

        val methodSignature = SemgrepPatternAction.MethodSignature(
            methodName = SignatureName.AnyName,
            methodReturnTypeMetavar = null,
            ParamConstraint.Partial(emptyList()),
            modifiers = emptyList(),
            enclosingClassMetavar = nameMetavar,
            enclosingClassModifiers = classModifiers,
        )

        return SemgrepPatternActionList(
            listOf(methodSignature),
            hasEllipsisInTheEnd = true,
            hasEllipsisInTheBeginning = false
        )
    }

    private fun transformMethodDeclaration(pattern: MethodDeclaration): SemgrepPatternActionList {
        val bodyPattern = transformPatternToActionList(pattern.body)
        val params = methodArgumentsToPatternList(pattern.args)
        val methodName = (pattern.name as? MetavarName)?.metavarName
            ?: transformationFailed("MethodDeclaration_name_not_metavar")

        val retType = pattern.returnType
        val returnTypeName = if (retType != null) {
            if (retType.typeArgs.isNotEmpty()) {
                transformationFailed("MethodDeclaration_return_type_with_type_args")
            }

            val retTypeMetaVar = (retType.dotSeparatedParts.singleOrNull() as? MetavarName)?.metavarName
                ?: transformationFailed("MethodDeclaration_return_type_not_metavar")

            retTypeMetaVar
        } else {
            null
        }

        val paramConditions = mutableListOf<ParamPattern>()

        var idxIsConcrete = true
        for ((i, param) in params.withIndex()) {
            when (param) {
                is FormalArgument -> {
                    val position = if (idxIsConcrete) {
                        ParamPosition.Concrete(i)
                    } else {
                        ParamPosition.Any(paramClassifier = "*-$i")
                    }

                    val paramModifiers = param.modifiers.map { transformModifier(it) }
                    paramModifiers.mapTo(paramConditions) { modifier ->
                        ParamPattern(position, ParamCondition.ParamModifier(modifier))
                    }

                    val paramName = (param.name as? MetavarName)?.metavarName
                        ?: transformationFailed("MethodDeclaration_param_name_not_metavar")

                    paramConditions += ParamPattern(position, IsMetavar(paramName))

                    val paramType = transformTypeName(param.type)
                    paramConditions += ParamPattern(position, ParamCondition.TypeIs(paramType))
                }

                is EllipsisArgumentPrefix -> {
                    idxIsConcrete = false
                    continue
                }

                else -> {
                    transformationFailed("MethodDeclaration_parameters_not_extracted")
                }
            }
        }

        val modifiers = pattern.modifiers.map { transformModifier(it) }

        val signature = SemgrepPatternAction.MethodSignature(
            SignatureName.MetaVar(methodName),
            returnTypeName,
            ParamConstraint.Partial(paramConditions),
            modifiers = modifiers,
            enclosingClassMetavar = null,
            enclosingClassModifiers = emptyList(),
        )

        return SemgrepPatternActionList(
            listOf(signature) + bodyPattern.actions,
            hasEllipsisInTheEnd = bodyPattern.hasEllipsisInTheEnd,
            hasEllipsisInTheBeginning = false
        )
    }

    private fun transformModifier(modifier: Modifier): SignatureModifier = when (modifier) {
        is Annotation -> transformAnnotation(modifier)
    }

    private fun transformAnnotation(annotation: Annotation): SignatureModifier {
        val annotationType = transformTypeName(annotation.name)
        val args = methodArgumentsToPatternList(annotation.args)
        val annotationValue = when (args.size) {
            0 -> SignatureModifierValue.NoValue
            1 -> when (val arg = args.single()) {
                is NamedValue -> {
                    val paramName = (arg.name as? ConcreteName)?.name
                        ?: transformationFailed("Annotation_argument_parameter_is_not_concrete")

                    tryExtractAnnotationParamValue(arg.value, paramName)
                }

                is EllipsisArgumentPrefix -> SignatureModifierValue.AnyValue

                else -> tryExtractAnnotationParamValue(arg, paramName = "value")
            }

            else -> {
                transformationFailed("Annotation_multiple_args")
            }
        }

        return SignatureModifier(annotationType, annotationValue)
    }

    private fun tryExtractAnnotationParamValue(
        pattern: SemgrepJavaPattern,
        paramName: String
    ): SignatureModifierValue = when (pattern) {
        is StringLiteral -> {
            when (val value = pattern.content) {
                is MetavarName -> {
                    transformationFailed("Annotation_argument_is_string_with_meta_var")
                }

                is ConcreteName -> SignatureModifierValue.StringValue(paramName, value.name)
            }
        }

        is StringEllipsis -> {
            SignatureModifierValue.StringPattern(paramName, pattern = ".*")
        }

        is Metavar -> SignatureModifierValue.MetaVar(paramName, pattern.name)
        else -> {
            transformationFailed("Annotation_argument_is_not_string_or_metavar")
        }
    }

    private class TransformationFailed(override val message: String) : Exception(message)
}
