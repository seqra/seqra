package org.opentaint.semgrep.go.pattern.conversion.go

import org.opentaint.dataflow.configuration.go.serialized.GoNameMatcher
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedAssignAction
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCleanAction
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCondition
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedFieldSource
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedGlobalSource
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedItem
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedRule
import org.opentaint.dataflow.configuration.go.serialized.GoSinkMetaData
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.opentaint.semgrep.go.pattern.conversion.GoLanguageStrategy
import org.opentaint.semgrep.go.pattern.conversion.GoTaintRuleGenerationCtx
import org.opentaint.semgrep.pattern.FailedToCreateTaintRules
import org.opentaint.semgrep.pattern.IgnoredMetavarConstraint
import org.opentaint.semgrep.pattern.MetaVarConstraint
import org.opentaint.semgrep.pattern.MetaVarConstraintFormula
import org.opentaint.semgrep.pattern.NonMethodCallCleaner
import org.opentaint.semgrep.pattern.PlaceholderMethodName
import org.opentaint.semgrep.pattern.PlaceholderStringValue
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace
import org.opentaint.semgrep.pattern.conversion.IsMetavar
import org.opentaint.semgrep.pattern.conversion.MetavarAtom
import org.opentaint.semgrep.pattern.conversion.ParamCondition
import org.opentaint.semgrep.pattern.conversion.ParamCondition.StringValueMetaVar
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureName
import org.opentaint.semgrep.pattern.conversion.SpecificBoolValue
import org.opentaint.semgrep.pattern.conversion.SpecificIntValue
import org.opentaint.semgrep.pattern.conversion.SpecificNullValue
import org.opentaint.semgrep.pattern.conversion.SpecificStringValue
import org.opentaint.semgrep.pattern.conversion.automata.MethodConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodEnclosingClassName
import org.opentaint.semgrep.pattern.conversion.automata.MethodName
import org.opentaint.semgrep.pattern.conversion.automata.MethodSignature
import org.opentaint.semgrep.pattern.conversion.automata.NumberOfArgsConstraint
import org.opentaint.semgrep.pattern.conversion.automata.ParamConstraint
import org.opentaint.semgrep.pattern.conversion.automata.Position
import org.opentaint.semgrep.pattern.conversion.taint.MetaVarConstraintOrPlaceHolder
import org.opentaint.semgrep.pattern.conversion.taint.RuleConversionCtx
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeCondition
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeEffect
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.MethodPredicate
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.State
import org.opentaint.semgrep.pattern.conversion.taint.TaintRuleEdge
import org.opentaint.semgrep.pattern.conversion.taint.isGeneratedAnyValueGenerator
import org.opentaint.semgrep.pattern.conversion.taint.isGeneratedStringConcat
import org.opentaint.semgrep.pattern.toDNF

private enum class GoTaintEdgeKind { POSITIVE, CLEANER }

private sealed interface GoMethodNamePattern {
    data object AnyName : GoMethodNamePattern
    data class Concrete(val name: String) : GoMethodNamePattern
    data class Regex(val regex: String) : GoMethodNamePattern
}

fun GoTaintRuleGenerationCtx.emitGoTaintRules(ctx: RuleConversionCtx): List<GoSerializedItem> {
    val rules = mutableListOf<Pair<TaintRuleEdge, GoSerializedItem>>()

    fun evaluateWithStateCheck(edge: TaintRuleEdge, kind: GoTaintEdgeKind, stateOfEdge: State): List<GoEvaluatedEdgeCondition> =
        this.evaluateGoMethodConditionAndEffect(kind, stateOfEdge, edge.edgeCondition, edge.edgeEffect, ctx.trace)
            .map { it.addGoStateCheck(this, edge.checkGlobalState, stateOfEdge) }

    for (ruleEdge in edges) {
        val stateOfEdge = ruleEdge.stateFrom

        for (condition in evaluateWithStateCheck(ruleEdge, GoTaintEdgeKind.POSITIVE, stateOfEdge)) {
            val actions = buildGoStateAssignActions(ruleEdge.stateTo, condition)
            if (actions.isEmpty()) continue

            when (ruleEdge.edgeKind) {
                TaintRuleEdge.Kind.MethodEnter,
                TaintRuleEdge.Kind.MethodExit -> {
                    ctx.trace.error(FailedToCreateTaintRules("Non method call sources are not supported yet"))
                    continue
                }

                TaintRuleEdge.Kind.MethodCall -> {
                    val info = edgeRuleInfo(ruleEdge).toGo()
                    val fn = condition.ruleCondition.function
                    fn.handleMethodCall(
                        call = {
                            rules += ruleEdge to GoSerializedRule.Source(
                                pkg = fn.pkgMatcher,
                                function = fn.nameMatcher,
                                condition = condition.ruleCondition.condition,
                                taint = actions,
                                info = info,
                            )
                        },
                        global = { globalField ->
                            rules += ruleEdge to GoSerializedGlobalSource(
                                pkg = fn.pkgMatcher,
                                global = globalField,
                                condition = condition.ruleCondition.condition,
                                taint = actions,
                                info = info,
                            )
                        },
                        field = { fieldName ->
                            rules += ruleEdge to GoSerializedFieldSource(
                                field = fieldName,
                                condition = condition.ruleCondition.condition,
                                taint = actions,
                                info = info,
                            )
                        }
                    )

                }
            }
        }
    }

    for (ruleEdge in edgesToFinalAccept) {
        val stateOfEdge = ruleEdge.stateFrom

        for (condition in evaluateWithStateCheck(ruleEdge, GoTaintEdgeKind.POSITIVE, stateOfEdge)) {
            when (ruleEdge.edgeKind) {
                TaintRuleEdge.Kind.MethodEnter,
                TaintRuleEdge.Kind.MethodExit -> {
                    ctx.trace.error(FailedToCreateTaintRules("Non method call sinks are not supported yet"))
                    continue
                }

                TaintRuleEdge.Kind.MethodCall -> {
                    val fn = condition.ruleCondition.function
                    fn.handleMethodCall(
                        call = {
                            val afterSinkActions = buildGoStateAssignActions(ruleEdge.stateTo, condition)
                            rules += ruleEdge to GoSerializedRule.Sink(
                                pkg = fn.pkgMatcher,
                                function = fn.nameMatcher,
                                condition = condition.ruleCondition.condition,
                                trackFactsReachAnalysisEnd = afterSinkActions.takeIf { it.isNotEmpty() },
                                id = ctx.ruleId,
                                meta = ctx.meta.toGoSinkMeta(),
                                info = null,
                            )
                        },
                        global = { _ ->
                            ctx.trace.error(FailedToCreateTaintRules("Global sinks are not supported yet"))
                        },
                        field = { _ ->
                            ctx.trace.error(FailedToCreateTaintRules("Field-read sinks are not supported yet"))
                        }
                    )
                }
            }
        }
    }

    for (ruleEdge in edgesToFinalDead) {
        val stateOfEdge = ruleEdge.stateFrom

        for (condition in evaluateWithStateCheck(ruleEdge, GoTaintEdgeKind.CLEANER, stateOfEdge)) {
            val actions = buildGoStateCleanActions(ruleEdge.stateTo, stateOfEdge, condition)
            if (actions.isEmpty()) continue

            val info = edgeRuleInfo(ruleEdge).toGo()

            when (ruleEdge.edgeKind) {
                TaintRuleEdge.Kind.MethodEnter, TaintRuleEdge.Kind.MethodExit -> {
                    ctx.trace.error(NonMethodCallCleaner())
                    continue
                }
                TaintRuleEdge.Kind.MethodCall -> {
                    val fn = condition.ruleCondition.function
                    fn.handleMethodCall(
                        call = {
                            rules += ruleEdge to GoSerializedRule.Cleaner(
                                pkg = fn.pkgMatcher,
                                function = fn.nameMatcher,
                                condition = condition.ruleCondition.condition,
                                cleans = actions,
                                info = info,
                            )
                        },
                        global = { _ ->
                            ctx.trace.error(FailedToCreateTaintRules("Global cleaners are not supported yet"))
                        },
                        field = { _ ->
                            ctx.trace.error(FailedToCreateTaintRules("Field-read cleaners are not supported yet"))
                        }
                    )
                }
            }
        }
    }

    return rules.map { (edge, item) ->
        val itemWithId = item.withId(ctx.nextSerializedItemId(item.typeLabel()))
        registerSerializedItem(edge, requireNotNull(itemWithId.serializedId))
        itemWithId
    }
}

private fun GoSerializedItem.typeLabel(): String = when (this) {
    is GoSerializedGlobalSource -> "global-source"
    is GoSerializedFieldSource -> "field-source"
    is GoSerializedRule.Source -> "source"
    is GoSerializedRule.Sink -> "sink"
    is GoSerializedRule.PassThrough -> "pass-through"
    is GoSerializedRule.Cleaner -> "cleaner"
}

private fun GoSerializedItem.withId(id: String): GoSerializedItem = when (this) {
    is GoSerializedGlobalSource -> copy(serializedId = id)
    is GoSerializedFieldSource -> copy(serializedId = id)
    is GoSerializedRule.Source -> copy(serializedId = id)
    is GoSerializedRule.Sink -> copy(serializedId = id)
    is GoSerializedRule.PassThrough -> copy(serializedId = id)
    is GoSerializedRule.Cleaner -> copy(serializedId = id)
}

private inline fun GoFunctionNameMatcher.handleMethodCall(
    call: () -> Unit,
    global: (GoNameMatcher) -> Unit,
    field: (GoNameMatcher) -> Unit,
) {
    if (nameMatcher is GoNameMatcher.Simple) {
        val globalField = GoLanguageStrategy.globalReadFieldOrNull(nameMatcher.name)
        if (globalField != null) {
            global(GoNameMatcher.Simple(globalField))
            return
        }

        val fieldName = GoLanguageStrategy.fieldReadFieldOrNull(nameMatcher.name)
        if (fieldName != null) {
            field(GoNameMatcher.Simple(fieldName))
            return
        }
    }

    call()
}

private fun SinkMetaData.toGoSinkMeta(): GoSinkMetaData =
    GoSinkMetaData(message = note ?: "", severity = severity ?: GoSinkMetaData().severity, cwe = cwe)

private fun GoTaintRuleGenerationCtx.buildGoStateAssignActions(
    stateAfter: State,
    edgeCondition: GoEvaluatedEdgeCondition
): List<GoSerializedAssignAction> {
    val requiredVariables = stateAfter.register.assignedVars.keys
    val result = requiredVariables.flatMapTo(mutableListOf()) { varName ->
        val varPosition = edgeCondition.accessedVarPosition[varName] ?: return@flatMapTo emptyList()
        varPosition.positions.flatMap { sp ->
            sp.bases().flatMap {
                stateAssignMark(varPosition.varName, stateAfter, it)
            }
        }
    }

    if (stateAfter in globalStateAssignStates) {
        result += globalStateMarkName(stateAfter).mkGoAssignMark(goStateVarPosition)
    }

    return result
}

private fun GoTaintRuleGenerationCtx.buildGoStateCleanActions(
    stateAfter: State,
    stateBefore: State,
    edgeCondition: GoEvaluatedEdgeCondition
): List<GoSerializedCleanAction> {
    val result = edgeCondition.accessedVarPosition.values.flatMapTo(mutableListOf()) { varPosition ->
        varPosition.positions.flatMap { sp ->
            sp.bases().flatMap {
                stateCleanMark(varPosition.varName, stateAfter, stateBefore, it)
            }
        }
    }
    result += stateCleanMark(varName = null, stateAfter, stateBefore, position = null)

    if (stateBefore in globalStateAssignStates) {
        result += globalStateMarkName(stateBefore).mkGoCleanMark(goStateVarPosition)
    }

    return result
}

private val GoTaintRuleGenerationCtx.goStateVarPosition: PositionBaseWithModifiers
    get() = PositionBase.ClassStatic(prefix.artificialState("pos").taintMarkStr()).baseGo()

private fun GoEvaluatedEdgeCondition.addGoStateCheck(
    ctx: GoTaintRuleGenerationCtx,
    checkGlobalState: Boolean,
    stateOfEdge: State,
): GoEvaluatedEdgeCondition {
    val stateChecks = mutableListOf<GoSerializedCondition>()
    if (checkGlobalState) {
        stateChecks += ctx.globalStateMarkName(stateOfEdge).mkGoContainsMark(ctx.goStateVarPosition)
    } else {
        for (metaVar in stateOfEdge.register.assignedVars.keys) {
            for (sp in accessedVarPosition[metaVar]?.positions.orEmpty()) {
                sp.bases().forEach {
                    stateChecks += ctx.containsStateMark(metaVar, stateOfEdge, it)
                }
            }
        }
    }

    if (stateChecks.isEmpty()) return this

    val stateCondition = GoSerializedCondition.or(stateChecks)
    val combined = GoSerializedCondition.and(listOf(stateCondition, ruleCondition.condition))
    return copy(ruleCondition = ruleCondition.copy(condition = combined))
}

private fun GoTaintRuleGenerationCtx.evaluateGoMethodConditionAndEffect(
    edgeKind: GoTaintEdgeKind,
    edgeState: State,
    condition: EdgeCondition,
    effect: EdgeEffect,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): List<GoEvaluatedEdgeCondition>{
    val resultFieldChains = mutableListOf<String>()
    val normalizedCondition = condition.dropFieldResultModifier(resultFieldChains)
    val normalizedEffect = effect.dropFieldResultModifier(resultFieldChains)
    val fieldCtx = createFieldCtx(resultFieldChains)
    return evaluateGoMethodConditionAndEffect(fieldCtx, edgeKind, edgeState, normalizedCondition, normalizedEffect, semgrepRuleTrace)
}

private fun GoTaintRuleGenerationCtx.evaluateGoMethodConditionAndEffect(
    fieldCtx: FieldModifierCtx,
    edgeKind: GoTaintEdgeKind,
    edgeState: State,
    condition: EdgeCondition,
    effect: EdgeEffect,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): List<GoEvaluatedEdgeCondition> {
    val evaluatedConditions = mutableListOf<GoEvaluatedEdgeCondition>()

    val (evaluatedSignature, ruleBuilders) = evaluateGoConditionAndEffectSignatures(this, effect, condition, semgrepRuleTrace)
    for (ruleBuilder in ruleBuilders) {
        condition.readMetaVar.values.flatten().forEach {
            val signature = it.predicate.signature.notEvaluatedSignature(evaluatedSignature)
            evaluateGoEdgePredicateConstraint(this, fieldCtx, edgeKind, edgeState, signature, it.predicate.constraint, it.negated, ruleBuilder.conditions, semgrepRuleTrace)
        }
        condition.other.forEach {
            val signature = it.predicate.signature.notEvaluatedSignature(evaluatedSignature)
            evaluateGoEdgePredicateConstraint(this, fieldCtx, edgeKind, edgeState, signature, it.predicate.constraint, it.negated, ruleBuilder.conditions, semgrepRuleTrace)
        }

        val varPositions = hashMapOf<MetavarAtom, GoRegisterVarPosition>()
        effect.assignMetaVar.values.flatten().forEach { findGoMetaVarPosition(fieldCtx, it.predicate.constraint, varPositions) }

        evaluatedConditions += GoEvaluatedEdgeCondition(ruleBuilder.build(), varPositions)
    }

    return evaluatedConditions
}

private fun EdgeEffect.dropFieldResultModifier(fieldChains: MutableList<String>) =
    EdgeEffect(assignMetaVar.dropFieldResultModifier(fieldChains))

private fun EdgeCondition.dropFieldResultModifier(fieldChains: MutableList<String>) =
    EdgeCondition(readMetaVar.dropFieldResultModifier(fieldChains), other.dropFieldResultModifier(fieldChains))

private fun Map<MetavarAtom, List<MethodPredicate>>.dropFieldResultModifier(fieldChains: MutableList<String>) =
    mapValues { (_, v) -> v.dropFieldResultModifier(fieldChains) }

private fun List<MethodPredicate>.dropFieldResultModifier(fieldChains: MutableList<String>): List<MethodPredicate> =
    mapNotNull { it.dropFieldResultModifier(fieldChains) }

private fun MethodPredicate.dropFieldResultModifier(fieldChains: MutableList<String>): MethodPredicate? {
    val constraint = predicate.constraint as? ParamConstraint ?: return this
    val paramModifier = constraint.condition as? ParamCondition.ParamModifier ?: return this
    val remainingModifier = paramModifier.dropFieldResultModifier { field ->
        fieldChains += field
        check(constraint.position is Position.Result) { "Field modifier on non-result position" }
        check(!negated) { "Negated field modifier" }
    }
    return if (remainingModifier != null) this else null
}

private fun createFieldCtx(resultFieldChains: List<String>): FieldModifierCtx {
    if (resultFieldChains.isEmpty()) return FieldModifierCtx(resultModifiers = null)

    val uniqueChains = resultFieldChains.distinct()
    if (uniqueChains.size > 1) {
        error("Multiple result field chains")
    }

    val resultFieldChain = uniqueChains.first()
    val fields = GoLanguageStrategy.splitFieldNames(resultFieldChain)
    return FieldModifierCtx(fields.toSerializedPosModifiers())
}

private fun MethodSignature.notEvaluatedSignature(evaluated: MethodSignature): MethodSignature? {
    if (this == evaluated) return null
    return MethodSignature(
        methodName = if (methodName == evaluated.methodName) MethodName(SignatureName.AnyName) else methodName,
        enclosingClassName = if (enclosingClassName == evaluated.enclosingClassName) MethodEnclosingClassName.anyClassName else enclosingClassName,
        returnType = if (returnType == evaluated.returnType) null else returnType,
    )
}

private fun evaluateGoConditionAndEffectSignatures(
    ctx: GoTaintRuleGenerationCtx,
    effect: EdgeEffect,
    condition: EdgeCondition,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): Pair<MethodSignature, List<GoRuleConditionBuilder>> {
    val signatures = mutableListOf<MethodSignature>()
    effect.assignMetaVar.values.flatten().forEach {
        check(!it.negated) { "Negated effect" }
        signatures.add(it.predicate.signature)
    }
    condition.readMetaVar.values.flatten().forEach {
        if (!it.negated) signatures.add(it.predicate.signature)
    }
    condition.other.forEach {
        if (!it.negated) signatures.add(it.predicate.signature)
    }
    return evaluateGoFormulaSignature(ctx, signatures, semgrepRuleTrace)
}

private fun evaluateGoFormulaSignature(
    ctx: GoTaintRuleGenerationCtx,
    signatures: List<MethodSignature>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): Pair<MethodSignature, List<GoRuleConditionBuilder>> {
    val signature = signatures.first()
    if (signatures.any { it != signature }) TODO("Signature mismatch")
    if (signature.isGeneratedAnyValueGenerator()) TODO("Eliminate generated method")
    if (signature.isGeneratedStringConcat()) TODO("Eliminate generated string concat")

    val methodName = signature.methodName.name
    val evaluatedMethodName = evaluateGoFormulaSignatureMethodName(ctx, methodName, semgrepRuleTrace)

    // A non-null Go function matcher means we have a concrete name; otherwise we leave the
    // builder.function null and the build() default kicks in (matches anything).
    val classMatcherSource = signature.enclosingClassName.name
    val classFormula = ctx.goTypeMatcher(classMatcherSource, semgrepRuleTrace)

    val builders = evaluatedMethodName.flatMap { namePattern ->
        if (classFormula == null) {
            return@flatMap listOf(
                GoRuleConditionBuilder().also { b ->
                    b.function = buildGoFunctionMatcher(namePattern, positiveClass = null, negativeClasses = emptyList())
                }
            )
        }

        val classDnf = classFormula.toDNF()
        classDnf.flatMap { cube ->
            if (cube.positive.size > 1) TODO("Complex class signature matcher")
            val positiveClass = cube.positive.firstOrNull()?.constraint
            val negativeClasses = cube.negative.map { it.constraint }
            listOf(
                GoRuleConditionBuilder().also { b ->
                    b.function = buildGoFunctionMatcher(namePattern, positiveClass, negativeClasses)
                }
            )
        }.ifEmpty {
            // Empty DNF (no cubes at all): fall back to name-only matcher.
            listOf(
                GoRuleConditionBuilder().also { b ->
                    b.function = buildGoFunctionMatcher(namePattern, positiveClass = null, negativeClasses = emptyList())
                }
            )
        }
    }

    // Return type — emit IsType(Result) condition.
    signature.returnType?.let { returnType ->
        val rt = ctx.goTypeMatcher(returnType, semgrepRuleTrace)
        val cond = rt.toGoSerializedConditionIsType(PositionBase.Result)
        builders.forEach { it.conditions += cond }
    }

    return signature to builders
}

private fun buildGoFunctionMatcher(
    namePattern: GoMethodNamePattern,
    positiveClass: String?,
    negativeClasses: List<String>,
): GoFunctionNameMatcher? {
    if (negativeClasses.isEmpty()) {
        return buildGoFunctionMatcherPositive(namePattern, positiveClass)
    }

    TODO("Negative pkg patterns")
}

private fun buildGoFunctionMatcherPositive(
    namePattern: GoMethodNamePattern,
    positiveClass: String?,
): GoFunctionNameMatcher? {
    val functionNameMatcher = when (namePattern) {
        is GoMethodNamePattern.AnyName -> null
        is GoMethodNamePattern.Concrete -> GoNameMatcher.Simple(namePattern.name)
        is GoMethodNamePattern.Regex -> GoNameMatcher.Pattern(namePattern.regex)
    }

    if (positiveClass == null) {
        return functionNameMatcher?.let { GoFunctionNameMatcher(goAnyNameMatcher(), it) }
    }

    val pkgMatcher = GoNameMatcher.Simple(positiveClass)
    return GoFunctionNameMatcher(pkgMatcher, functionNameMatcher ?: goAnyNameMatcher())
}

private fun evaluateGoFormulaSignatureMethodName(
    ctx: GoTaintRuleGenerationCtx,
    methodName: SignatureName,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): List<GoMethodNamePattern> = when (methodName) {
    SignatureName.AnyName -> listOf(GoMethodNamePattern.AnyName)
    is SignatureName.Concrete -> listOf(GoMethodNamePattern.Concrete(methodName.name))
    is SignatureName.MetaVar -> {
        val constraint = when (val c = ctx.metaVarInfo.constraints[methodName.metaVar]) {
            null -> null
            is MetaVarConstraintOrPlaceHolder.Constraint -> c.constraint
            is MetaVarConstraintOrPlaceHolder.PlaceHolder -> {
                semgrepRuleTrace.error(PlaceholderMethodName())
                c.constraint
            }
        }
        if (constraint == null) listOf(GoMethodNamePattern.AnyName) else {
            val cubes = constraint.constraint.toDNF()
            cubes.mapNotNull { cube ->
                // Negated method names are still dropped — Go's function-matcher matches
                // by qualified name, and a single name-only negation would require the
                // matcher to match anything-except, which is rule-specific (and the surrounding
                // pipeline emits this as a `Not` condition where it can).
                if (cube.negative.isNotEmpty()) null
                else if (cube.positive.isEmpty()) null
                else if (cube.positive.size > 1) null
                else when (val c = cube.positive.first().constraint) {
                    is MetaVarConstraint.Concrete -> GoMethodNamePattern.Concrete(c.value)
                    is MetaVarConstraint.RegExp -> GoMethodNamePattern.Regex(c.regex)
                }
            }.ifEmpty { listOf(GoMethodNamePattern.AnyName) }
        }
    }
}

private fun evaluateGoEdgePredicateConstraint(
    ctx: GoTaintRuleGenerationCtx,
    fieldCtx: FieldModifierCtx,
    edgeKind: GoTaintEdgeKind,
    edgeState: State,
    signature: MethodSignature?,
    constraint: MethodConstraint?,
    negated: Boolean,
    conditions: MutableSet<GoSerializedCondition>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
) {
    if (!negated) {
        evaluateGoMethodConstraints(ctx, fieldCtx, edgeKind, edgeState, signature, constraint, conditions, semgrepRuleTrace)
    } else {
        val negatedConds = hashSetOf<GoSerializedCondition>()
        evaluateGoMethodConstraints(ctx, fieldCtx, edgeKind, edgeState, signature, constraint, negatedConds, semgrepRuleTrace)
        conditions += GoSerializedCondition.not(GoSerializedCondition.and(negatedConds.toList()))
    }
}

private fun evaluateGoMethodConstraints(
    ctx: GoTaintRuleGenerationCtx,
    fieldCtx: FieldModifierCtx,
    edgeKind: GoTaintEdgeKind,
    edgeState: State,
    signature: MethodSignature?,
    constraint: MethodConstraint?,
    conditions: MutableSet<GoSerializedCondition>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
) {
    if (signature != null) {
        evaluateGoMethodSignatureCondition(ctx, signature, conditions, semgrepRuleTrace)
    }
    when (constraint) {
        null -> {}
        is NumberOfArgsConstraint -> conditions += GoSerializedCondition.NumberOfArgs(constraint.num)
        is ParamConstraint -> evaluateGoParamConstraints(ctx, fieldCtx, edgeKind, edgeState, constraint, conditions, semgrepRuleTrace)
        else -> error("Unsupported constraint: $constraint")
    }
}

private fun evaluateGoMethodSignatureCondition(
    ctx: GoTaintRuleGenerationCtx,
    signature: MethodSignature,
    conditions: MutableSet<GoSerializedCondition>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
) {
    // Class-name matching is not representable at Go condition level (only at function-matcher level);
    // skip the class part. Method-name matching is also not representable; skip too.
    signature.returnType?.let {
        val rt = ctx.goTypeMatcher(it, semgrepRuleTrace)
        conditions += rt.toGoSerializedConditionIsType(PositionBase.Result)
    }
}

private fun evaluateGoParamConstraints(
    ctx: GoTaintRuleGenerationCtx,
    fieldCtx: FieldModifierCtx,
    edgeKind: GoTaintEdgeKind,
    edgeState: State,
    param: ParamConstraint,
    conditions: MutableSet<GoSerializedCondition>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
) {
    val position = param.position.toGoSerializedPosition(fieldCtx)
    conditions += evaluateGoParamCondition(ctx, edgeKind, edgeState, position, param.condition, semgrepRuleTrace)
}

private fun findGoMetaVarPosition(
    fieldCtx: FieldModifierCtx,
    constraint: MethodConstraint?,
    varPositions: MutableMap<MetavarAtom, GoRegisterVarPosition>,
) {
    if (constraint !is ParamConstraint) return
    val position = constraint.position.toGoSerializedPosition(fieldCtx)
    findGoMetaVarPositionUtil(position, constraint.condition, varPositions)
}

private fun findGoMetaVarPositionUtil(
    position: PositionBaseWithModifiers,
    condition: ParamCondition.Atom,
    varPositions: MutableMap<MetavarAtom, GoRegisterVarPosition>,
) {
    if (condition !is IsMetavar) return
    val varPosition = varPositions.getOrPut(condition.metavar) {
        GoRegisterVarPosition(condition.metavar, hashSetOf())
    }
    varPosition.positions.add(GoStarredPosition(position, condition.star))
}

private fun evaluateGoParamCondition(
    ctx: GoTaintRuleGenerationCtx,
    edgeKind: GoTaintEdgeKind,
    edgeState: State,
    position: PositionBaseWithModifiers,
    condition: ParamCondition.Atom,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): GoSerializedCondition {
    when (condition) {
        is IsMetavar -> {
            val constraints = ctx.metaVarInfo.constraints[condition.metavar.toString()]
            if (constraints != null) {
                // todo: semantic metavar constraint
                semgrepRuleTrace.error(IgnoredMetavarConstraint(condition.metavar))
            }
            val contains = ctx.containsMarkWithAnyStateBefore(edgeState, condition.metavar, position)
            if (!condition.star) return contains

            val containsAnyField = ctx.containsMarkWithAnyStateBefore(edgeState, condition.metavar, position.withAnyField())
            return GoSerializedCondition.or(listOf(contains, containsAnyField))
        }
        is ParamCondition.TypeIs -> {
            return ctx.goTypeMatcher(condition.typeName, semgrepRuleTrace)
                .toGoSerializedConditionIsType(position.base)
        }
        is ParamCondition.SpecificStaticFieldValue -> {
            // Go has no static-field source rules in v1; drop the condition entirely.
            return GoSerializedCondition.True
        }
        ParamCondition.AnyStringLiteral -> {
            return GoSerializedCondition.and(
                listOf(
                    GoSerializedCondition.IsType("string", position.base),
                    GoSerializedCondition.ConstantMatches(position.base, ".*"),
                )
            )
        }
        is SpecificBoolValue -> {
            val v = GoSerializedCondition.ConstantValue(
                GoSerializedCondition.ConstantType.Bool, condition.value.toString()
            )
            return GoSerializedCondition.ConstantCmp(position.base, v, GoSerializedCondition.ConstantCmpType.Eq)
        }
        is SpecificIntValue -> {
            val v = GoSerializedCondition.ConstantValue(
                GoSerializedCondition.ConstantType.Int, condition.value.toString()
            )
            return GoSerializedCondition.ConstantCmp(position.base, v, GoSerializedCondition.ConstantCmpType.Eq)
        }
        is SpecificStringValue -> {
            val v = GoSerializedCondition.ConstantValue(GoSerializedCondition.ConstantType.Str, condition.value)
            return GoSerializedCondition.ConstantCmp(position.base, v, GoSerializedCondition.ConstantCmpType.Eq)
        }
        is SpecificNullValue -> {
            return GoSerializedCondition.IsNull(position.base)
        }
        is StringValueMetaVar -> {
            val constraints = ctx.metaVarInfo.constraints[condition.metaVar.toString()]
            val constraint = when (constraints) {
                null -> null
                is MetaVarConstraintOrPlaceHolder.Constraint -> constraints.constraint.constraint
                is MetaVarConstraintOrPlaceHolder.PlaceHolder -> {
                    semgrepRuleTrace.error(PlaceholderStringValue())
                    constraints.constraint?.constraint
                }
            }
            return constraint.toGoSerializedConditionFromMetaVar(position.base)
        }
        is ParamCondition.ParamModifier -> {
            // Annotations — Java-only. Omit.
            return GoSerializedCondition.True
        }
    }
}

private fun MetaVarConstraintFormula<String>?.toGoSerializedConditionIsType(pos: PositionBase): GoSerializedCondition {
    if (this == null) return GoSerializedCondition.True
    return toGoSerializedCondition { GoSerializedCondition.IsType(it, pos) }
}

private fun <T> MetaVarConstraintFormula<T>?.toGoSerializedCondition(
    transform: (T) -> GoSerializedCondition,
): GoSerializedCondition {
    if (this == null) return GoSerializedCondition.True
    return toGoSerializedConditionWrtLiteral { transform(it.constraint) }
}

private fun <T> MetaVarConstraintFormula<T>.toGoSerializedConditionWrtLiteral(
    transform: (MetaVarConstraintFormula.Literal<T>) -> GoSerializedCondition,
): GoSerializedCondition = when (this) {
    is MetaVarConstraintFormula.Constraint -> transform(this)
    is MetaVarConstraintFormula.NegatedConstraint -> GoSerializedCondition.not(transform(this))
    is MetaVarConstraintFormula.And -> GoSerializedCondition.and(args.map { it.toGoSerializedConditionWrtLiteral(transform) })
    is MetaVarConstraintFormula.Or -> GoSerializedCondition.or(args.map { it.toGoSerializedConditionWrtLiteral(transform) })
}

private fun MetaVarConstraintFormula<MetaVarConstraint>?.toGoSerializedConditionFromMetaVar(pos: PositionBase): GoSerializedCondition {
    if (this == null) return GoSerializedCondition.True
    return toGoSerializedCondition { c ->
        when (c) {
            is MetaVarConstraint.Concrete -> {
                val v = GoSerializedCondition.ConstantValue(GoSerializedCondition.ConstantType.Str, c.value)
                GoSerializedCondition.ConstantCmp(pos, v, GoSerializedCondition.ConstantCmpType.Eq)
            }
            is MetaVarConstraint.RegExp -> GoSerializedCondition.and(
                listOf(
                    GoSerializedCondition.IsType("string", pos),
                    GoSerializedCondition.ConstantMatches(pos, c.regex),
                )
            )
        }
    }
}

private fun Position.toGoSerializedPosition(ctx: FieldModifierCtx): PositionBaseWithModifiers {
    return when (this) {
        is Position.Argument -> when (val idx = index) {
            is Position.ArgumentIndex.Any -> PositionBase.AnyArgument(idx.paramClassifier).baseGo()
            is Position.ArgumentIndex.Concrete -> PositionBase.Argument(idx.idx).baseGo()
        }

        is Position.Object -> PositionBase.This.baseGo()
        is Position.Result -> {
            val baseResult = PositionBase.Result.baseGo()
            val resMod = ctx.resultModifiers ?: return baseResult
            PositionBaseWithModifiers.WithModifiers(baseResult.base, resMod)
        }
    }
}

private fun List<String>.toSerializedPosModifiers(): List<PositionModifier> = map {
    when (it) {
        GoLanguageStrategy.INDEX_AUX_FIELD_NAME -> PositionModifier.ArrayElement
        else -> PositionModifier.Field("", it, "")
    }
}

private fun GoStarredPosition.bases(): List<PositionBaseWithModifiers> =
    if (star) listOf(position, position.withAnyField()) else listOf(position)
