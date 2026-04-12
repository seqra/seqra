package org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.AddExpr
import org.opentaint.semgrep.pattern.Annotation
import org.opentaint.semgrep.pattern.ArrayAccess
import org.opentaint.semgrep.pattern.BoolConstant
import org.opentaint.semgrep.pattern.CatchStatement
import org.opentaint.semgrep.pattern.ClassDeclaration
import org.opentaint.semgrep.pattern.ConcreteName
import org.opentaint.semgrep.pattern.DeepExpr
import org.opentaint.semgrep.pattern.Ellipsis
import org.opentaint.semgrep.pattern.EllipsisArgumentPrefix
import org.opentaint.semgrep.pattern.EllipsisMetavar
import org.opentaint.semgrep.pattern.EllipsisMethodInvocations
import org.opentaint.semgrep.pattern.EmptyPatternSequence
import org.opentaint.semgrep.pattern.FailedTransformationToActionList
import org.opentaint.semgrep.pattern.FieldAccess
import org.opentaint.semgrep.pattern.FormalArgument
import org.opentaint.semgrep.pattern.Identifier
import org.opentaint.semgrep.pattern.ImportStatement
import org.opentaint.semgrep.pattern.IntLiteral
import org.opentaint.semgrep.pattern.Metavar
import org.opentaint.semgrep.pattern.MetavarName
import org.opentaint.semgrep.pattern.MethodArguments
import org.opentaint.semgrep.pattern.MethodDeclaration
import org.opentaint.semgrep.pattern.MethodInvocation
import org.opentaint.semgrep.pattern.Modifier
import org.opentaint.semgrep.pattern.NamedValue
import org.opentaint.semgrep.pattern.NullLiteral
import org.opentaint.semgrep.pattern.ObjectCreation
import org.opentaint.semgrep.pattern.PatternSequence
import org.opentaint.semgrep.pattern.ReturnStmt
import org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace
import org.opentaint.semgrep.pattern.StaticFieldAccess
import org.opentaint.semgrep.pattern.StringEllipsis
import org.opentaint.semgrep.pattern.StringLiteral
import org.opentaint.semgrep.pattern.ThisExpr
import org.opentaint.semgrep.pattern.TypeName
import org.opentaint.semgrep.pattern.TypedMetavar
import org.opentaint.semgrep.pattern.VariableAssignment
import org.opentaint.semgrep.pattern.conversion.ParamCondition.StringValueMetaVar
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.ClassConstraint
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifier
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifierValue
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureName

class PatternToActionListConverter: ActionListBuilder {
    private var nextArtificialMetavarId = 0

    private fun provideArtificialMetavar(): MetavarAtom {
        return MetavarAtom.createArtificial("${nextArtificialMetavarId++}")
    }

    val failedTransformations = mutableMapOf<String, Int>()

    private fun transformationFailed(reason: String): Nothing {
        throw TransformationFailed(reason)
    }

    override fun createActionList(
        pattern: SemgrepJavaPattern,
        semgrepTrace: SemgrepRuleLoadStepTrace,
    ): SemgrepPatternActionList? = try {
        withTrace(semgrepTrace) {
            transformPatternToActionList(pattern, isRootPattern = true)
        }
    } catch (ex: TransformationFailed) {
        val reason = ex.message
        val oldValue = failedTransformations.getOrDefault(reason, 0)
        failedTransformations[reason] = oldValue + 1

        semgrepTrace.error(FailedTransformationToActionList(ex.message))
        null
    }

    private var semgrepTrace: SemgrepRuleLoadStepTrace? = null
    private fun <T> withTrace(trace: SemgrepRuleLoadStepTrace, body: () -> T): T = try {
        semgrepTrace = trace
        body()
    } finally {
        semgrepTrace = null
    }

    private fun transformPatternToActionList(
        pattern: SemgrepJavaPattern,
        isRootPattern: Boolean = false
    ): SemgrepPatternActionList = when (pattern) {
            is Ellipsis -> SemgrepPatternActionList(emptyList(), hasEllipsisInTheEnd = true, hasEllipsisInTheBeginning = true)
            is EmptyPatternSequence -> SemgrepPatternActionList(emptyList(), hasEllipsisInTheEnd = false , hasEllipsisInTheBeginning = false)
            is PatternSequence -> transformPatternSequence(pattern)
            is MethodInvocation -> transformMethodInvocation(pattern)
            is ObjectCreation -> transformObjectCreation(pattern)
            is VariableAssignment -> transformVariableAssignment(pattern)
            is MethodDeclaration -> transformMethodDeclaration(pattern)
            is ClassDeclaration -> transformClassDeclaration(pattern)
            is EllipsisMethodInvocations -> transformEllipsisMethodInvocations(pattern)
            is ReturnStmt -> transformReturnStmt(pattern)
            is AddExpr,
            is BoolConstant,
            is FieldAccess,
            is ArrayAccess,
            is StaticFieldAccess,
            is FormalArgument,
            is Identifier,
            is Metavar,
            is MethodArguments,
            is StringEllipsis,
            is StringLiteral,
            is ThisExpr,
            is TypedMetavar,
            is Annotation,
            is NamedValue,
            is NullLiteral,
            is ImportStatement,
            is CatchStatement,
            is DeepExpr,
            is EllipsisMetavar,
            is IntLiteral -> {
                val messagePrefix = if (isRootPattern) "Root pattern is: " else ""
                transformationFailed("$messagePrefix${pattern::class.java.simpleName}")
            }
        }

    private fun transformPatternIntoParamCondition(pattern: SemgrepJavaPattern): ParamCondition? {
        return when (pattern) {
            is BoolConstant -> SpecificBoolValue(pattern.value)
            is IntLiteral -> SpecificIntValue(pattern.value)
            is NullLiteral -> SpecificNullValue

            is StringLiteral -> when (val value = pattern.content) {
                is ConcreteName -> SpecificStringValue(value.name)
                is MetavarName -> StringValueMetaVar(MetavarAtom.create(value.metavarName))
            }

            is StringEllipsis -> {
                ParamCondition.AnyStringLiteral
            }

            is Metavar -> {
                IsMetavar(MetavarAtom.create(pattern.name))
            }

            is TypedMetavar -> {
                if (isGeneratedMethodInvocationObjMetaVar(pattern.name)) {
                    return ParamCondition.True
                }

                val typeName = transformTypeName(pattern.type)
                ParamCondition.And(
                    listOf(
                        IsMetavar(MetavarAtom.create(pattern.name)),
                        ParamCondition.TypeIs(typeName)
                    )
                )
            }

            is StaticFieldAccess -> {
                val type = transformTypeName(pattern.classTypeName)

                when (val fn = pattern.fieldName) {
                    is ConcreteName -> {
                        ParamCondition.SpecificStaticFieldValue(fn.name, type)
                    }

                    is MetavarName -> {
                        transformationFailed("Static field name is metavar")
                    }
                }
            }

            is ArrayAccess -> {
                if (pattern.arrayIndex !is Ellipsis) {
                    transformationFailed("Array access index is not ellipsis")
                }

                when (pattern.obj) {
                    is Metavar,
                    is TypedMetavar -> {
                        // todo: dirty hack. We can ignore array access here due to the `hackResultArray` in taint configuration
                        return transformPatternIntoParamCondition(pattern.obj)
                    }
                    else -> transformationFailed("Array access object is not metavar")
                }
            }

            is Ellipsis,
            is AddExpr,
            is EllipsisMethodInvocations,
            is EmptyPatternSequence,
            is FieldAccess,
            is FormalArgument,
            is Identifier,
            is MethodArguments,
            is MethodDeclaration,
            is MethodInvocation,
            is ObjectCreation,
            is PatternSequence,
            is ReturnStmt,
            is ThisExpr,
            is VariableAssignment,
            is Annotation,
            is ClassDeclaration,
            is NamedValue,
            is ImportStatement,
            is CatchStatement,
            is DeepExpr,
            is EllipsisMetavar -> null
        }
    }

    private val primitiveTypeNames by lazy {
        hashSetOf("byte", "short", "char", "int", "long", "float", "double", "boolean")
    }

    private fun transformTypeName(typeName: TypeName): TypeNamePattern = when (typeName) {
        is TypeName.SimpleTypeName -> transformSimpleTypeName(typeName)
        is TypeName.ArrayTypeName -> {
            val elementTypePattern = transformTypeName(typeName.elementType)
            TypeNamePattern.ArrayType(elementTypePattern)
        }
    }

    private fun transformSimpleTypeName(typeName: TypeName.SimpleTypeName): TypeNamePattern {
        val typeArgs = typeName.typeArgs.map { transformTypeName(it) }

        if (typeName.dotSeparatedParts.size == 1) {
            val name = typeName.dotSeparatedParts.single()
            if (name is MetavarName) return TypeNamePattern.MetaVar(name.metavarName)
        }

        val concreteNames = typeName.dotSeparatedParts.filterIsInstance<ConcreteName>()
        if (concreteNames.size == typeName.dotSeparatedParts.size) {
            if (concreteNames.size == 1) {
                val className = concreteNames.single().name
                if (className.first().isUpperCase()) {
                    return TypeNamePattern.ClassName(className, typeArgs)
                }

                if (className in primitiveTypeNames) {
                    return TypeNamePattern.PrimitiveName(className)
                }

                transformationFailed("TypeName_concrete_unexpected")
            }

            val fqn = concreteNames.joinToString(".") { it.name }
            return TypeNamePattern.FullyQualified(fqn, typeArgs)
        }

        transformationFailed("TypeName_non_concrete_unsupported")
    }

    private fun transformPatternSequence(pattern: PatternSequence): SemgrepPatternActionList {
        val first = transformPatternToActionList(pattern.first)
        val second = transformPatternToActionList(pattern.second)

        var endEllipsis = second.hasEllipsisInTheEnd
        if (endEllipsis) {
            if (second.actions.isEmpty() && first.actions.lastOrNull() is SemgrepPatternAction.MethodExit) {
                endEllipsis = false
            }
        }

        return SemgrepPatternActionList(
            first.actions + second.actions,
            hasEllipsisInTheEnd = endEllipsis,
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

    private fun methodArgumentsToPatternList(pattern: MethodArguments): List<SemgrepJavaPattern> =
        parseMethodArgs(pattern)

    private fun tryConvertPatternIntoTypeName(pattern: SemgrepJavaPattern): TypeNamePattern? {
        if (pattern !is TypedMetavar) return null
        return transformTypeName(pattern.type)
    }

    private fun transformMethodInvocation(pattern: MethodInvocation): SemgrepPatternActionList {
        val methodName = when (val name = pattern.methodName) {
            is ConcreteName -> SignatureName.Concrete(name.name)
            is MetavarName -> SignatureName.MetaVar(name.metavarName)
        }

        val actionList = mutableListOf<SemgrepPatternAction>()

        val className = pattern.obj?.let { tryConvertPatternIntoTypeName(it) }

        val objCondition = pattern.obj?.let { objPattern ->
            val (actions, cond) = transformPatternIntoParamConditionWithActions(objPattern)
                ?: transformationFailed("MethodInvocation_obj: ${objPattern::class.simpleName}")

            actionList += actions
            cond
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

    private fun transformEllipsisMethodInvocations(pattern: EllipsisMethodInvocations): SemgrepPatternActionList {
        val actionList = mutableListOf<SemgrepPatternAction>()

        val className = tryConvertPatternIntoTypeName(pattern.obj)

        val (actions, objCondition) = transformPatternIntoParamConditionWithActions(pattern.obj)
                ?: transformationFailed("MethodInvocation_obj: ${pattern.obj::class.simpleName}")
        actionList += actions

        val methodInvocationAction = SemgrepPatternAction.MethodCall(
            methodName = SignatureName.AnyName,
            result = null,
            params = ParamConstraint.Partial(emptyList()),
            obj = objCondition,
            enclosingClassName = className ?: TypeNamePattern.AnyType,
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
                val classifier = when (arg) {
                    is Metavar -> arg.name
                    is TypedMetavar -> arg.name
                    else -> "*->$i"
                }

                ParamPosition.Any(paramClassifier = classifier)
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
                conditions += IsMetavar(MetavarAtom.create(v.name))
            }

            is TypedMetavar -> {
                conditions += IsMetavar(MetavarAtom.create(v.name))

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
        val nameMetavar = (pattern.name as? MetavarName)?.metavarName
            ?: transformationFailed("ClassDeclaration_name_is_not_metavar")

        val classConstraints = mutableListOf<ClassConstraint>()

        if (pattern.extends != null) {
            classConstraints += ClassConstraint.TypeConstraint(transformTypeName(pattern.extends))
        }

        if (pattern.implements.isNotEmpty()) {
            pattern.implements.mapTo(classConstraints) {
                ClassConstraint.TypeConstraint(transformTypeName(it))
            }
        }

        pattern.modifiers.map { transformModifier(it) }
            .mapTo(classConstraints) { ClassConstraint.Signature(it) }

        val bodyActionList = transformPatternToActionList(pattern.body)
        if (bodyActionList.actions.isEmpty()) {
            val methodSignature = SemgrepPatternAction.MethodSignature(
                methodName = SignatureName.AnyName,
                ParamConstraint.Partial(emptyList()),
                modifiers = emptyList(),
                enclosingClassMetavar = nameMetavar,
                enclosingClassConstraints = classConstraints,
            )

            return SemgrepPatternActionList(
                listOf(methodSignature),
                hasEllipsisInTheEnd = true,
                hasEllipsisInTheBeginning = false
            )
        }

        val firstAction = bodyActionList.actions.first()
        if (firstAction !is SemgrepPatternAction.MethodSignature) {
            transformationFailed("Class declaration has body without method signature")
        }

        val signatureWithClass = firstAction.copy(
            enclosingClassMetavar = nameMetavar,
            enclosingClassConstraints = classConstraints,
        )

        return bodyActionList.copy(
            actions = listOf(signatureWithClass) + bodyActionList.actions.drop(1),
            hasEllipsisInTheBeginning = false
        )
    }

    private fun transformReturnStmt(pattern: ReturnStmt): SemgrepPatternActionList {
        val retValue = pattern.value
        val (actions, cond) = if (retValue == null) {
            emptyList<SemgrepPatternAction>() to null
        } else {
            transformPatternIntoParamConditionWithActions(retValue)
                ?: transformationFailed("Return value: ${retValue::class.simpleName}")
        }

        val methodExit = SemgrepPatternAction.MethodExit(cond ?: ParamCondition.True)

        return SemgrepPatternActionList(
            actions + listOf(methodExit),
            hasEllipsisInTheBeginning = false,
            hasEllipsisInTheEnd = false
        )
    }

    private fun transformMethodDeclaration(pattern: MethodDeclaration): SemgrepPatternActionList {
        val bodyPattern = transformPatternToActionList(pattern.body)
        val params = methodArgumentsToPatternList(pattern.args)

        val methodName = when (val name = pattern.name) {
            is ConcreteName -> SignatureName.Concrete(name.name)
            is MetavarName -> SignatureName.MetaVar(name.metavarName)
        }

        val returnTypePattern: TypeNamePattern? = pattern.returnType?.let { transformTypeName(it) }

        val paramConditions = mutableListOf<ParamPattern>()

        var idxIsConcrete = true
        for ((i, param) in params.withIndex()) {
            when (param) {
                is FormalArgument -> {
                    val paramName = (param.name as? MetavarName)?.metavarName
                        ?: transformationFailed("MethodDeclaration_param_name_not_metavar")

                    val position = if (idxIsConcrete) {
                        ParamPosition.Concrete(i)
                    } else {
                        ParamPosition.Any(paramClassifier = paramName)
                    }

                    val paramModifiers = param.modifiers.map { transformModifier(it) }
                    paramModifiers.mapTo(paramConditions) { modifier ->
                        ParamPattern(position, ParamCondition.ParamModifier(modifier))
                    }

                    paramConditions += ParamPattern(position, IsMetavar(MetavarAtom.create(paramName)))

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
            methodName, ParamConstraint.Partial(paramConditions),
            returnType = returnTypePattern,
            modifiers = modifiers,
            enclosingClassMetavar = null,
            enclosingClassConstraints = emptyList(),
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
        val annotationValue = transformAnnotationValue(args)
        return SignatureModifier(annotationType, annotationValue)
    }

    private fun transformAnnotationValue(args: List<SemgrepJavaPattern>): SignatureModifierValue {
        if (args.isEmpty()) return SignatureModifierValue.NoValue

        val nonEllipsisArgs = args.filterNot { it is EllipsisArgumentPrefix }
        if (nonEllipsisArgs.isEmpty()) return SignatureModifierValue.AnyValue

        if (nonEllipsisArgs.size > 1) {
            transformationFailed("Annotation_multiple_args")
        }

        val arg = nonEllipsisArgs.first()
        if (arg !is NamedValue) {
            return tryExtractAnnotationParamValue(arg, paramName = "value")
        }

        val paramName = (arg.name as? ConcreteName)?.name
            ?: transformationFailed("Annotation_argument_parameter_is_not_concrete")

        return tryExtractAnnotationParamValue(arg.value, paramName)
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
