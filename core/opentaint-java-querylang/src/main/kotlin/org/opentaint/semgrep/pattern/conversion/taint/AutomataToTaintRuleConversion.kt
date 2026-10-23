package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationParamMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationParamStringMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.ConstantCmpType
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.ConstantType
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.ConstantValue
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFieldRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFunctionNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedItem
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher.Pattern
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher.Simple
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintAssignAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintCleanAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTypeNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.opentaint.dataflow.configuration.jvm.serialized.SinkRule
import org.opentaint.semgrep.pattern.FailedToConvertToTaintRule
import org.opentaint.semgrep.pattern.IgnoredMetavarConstraint
import org.opentaint.semgrep.pattern.Mark.RuleUniqueMarkPrefix
import org.opentaint.semgrep.pattern.MetaVarConstraint
import org.opentaint.semgrep.pattern.MetaVarConstraintFormula
import org.opentaint.semgrep.pattern.NonMethodCallCleaner
import org.opentaint.semgrep.pattern.PlaceholderAnnotation
import org.opentaint.semgrep.pattern.PlaceholderMethodName
import org.opentaint.semgrep.pattern.PlaceholderStringValue
import org.opentaint.semgrep.pattern.PlaceholderTypeName
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.RuleWithMetaVars
import org.opentaint.semgrep.pattern.SemgrepMatchingRule
import org.opentaint.semgrep.pattern.SemgrepRule
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace
import org.opentaint.semgrep.pattern.SemgrepTaintRule
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.UserRuleFromSemgrepInfo
import org.opentaint.semgrep.pattern.conversion.IsMetavar
import org.opentaint.semgrep.pattern.conversion.JavaConcreteType
import org.opentaint.semgrep.pattern.conversion.JavaTaintRuleGenerationCtx
import org.opentaint.semgrep.pattern.conversion.LanguageStrategy.SinkDiscardMode
import org.opentaint.semgrep.pattern.conversion.MetavarAtom
import org.opentaint.semgrep.pattern.conversion.ParamCondition
import org.opentaint.semgrep.pattern.conversion.ParamCondition.StringValueMetaVar
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.ClassConstraint
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifier
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifierValue
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureName
import org.opentaint.semgrep.pattern.conversion.SpecificBoolValue
import org.opentaint.semgrep.pattern.conversion.SpecificIntValue
import org.opentaint.semgrep.pattern.conversion.SpecificNullValue
import org.opentaint.semgrep.pattern.conversion.SpecificStringValue
import org.opentaint.semgrep.pattern.conversion.TaintRuleStrategy
import org.opentaint.semgrep.pattern.conversion.TypeConstraint
import org.opentaint.semgrep.pattern.conversion.automata.ClassModifierConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodEnclosingClassName
import org.opentaint.semgrep.pattern.conversion.automata.MethodModifierConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodName
import org.opentaint.semgrep.pattern.conversion.automata.MethodSignature
import org.opentaint.semgrep.pattern.conversion.automata.NumberOfArgsConstraint
import org.opentaint.semgrep.pattern.conversion.automata.ParamConstraint
import org.opentaint.semgrep.pattern.conversion.automata.Position
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeCondition
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeEffect
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.State
import org.opentaint.semgrep.pattern.flatMap
import org.opentaint.semgrep.pattern.toDNF
import org.opentaint.semgrep.pattern.transform

fun <Item, Cond, Assign, Clean> RuleConversionCtx.convertTaintAutomataToTaintRules(
    strategy: TaintRuleStrategy<Item, Cond, Assign, Clean>,
    rule: SemgrepRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
): TaintRuleFromSemgrep<Item> = when (rule) {
    is SemgrepMatchingRule -> convertMatchingRuleToTaintRules(strategy, rule)
    is SemgrepTaintRule -> convertTaintRuleToTaintRules(strategy, rule)
}

fun <R> RuleConversionCtx.safeConvertToTaintRules(body: () -> R): R? =
    runCatching {
        body()
    }.onFailure { ex ->
        trace.error(FailedToConvertToTaintRule(ex.message))
    }.getOrNull()

private fun <Item, Cond, Assign, Clean> RuleConversionCtx.convertMatchingRuleToTaintRules(
    strategy: TaintRuleStrategy<Item, Cond, Assign, Clean>,
    rule: SemgrepMatchingRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
): TaintRuleFromSemgrep<Item> {
    val ruleGroups = rule.rules.mapIndexedNotNull { idx, r ->
        val rules = safeConvertToTaintRules {
            convertAutomataToTaintRules(strategy, r.metaVarInfo, r.rule, RuleUniqueMarkPrefix(ruleId, modeModifier, idx))
        }

        rules
    }

    if (ruleGroups.isEmpty()) {
        error("Failed to generate any taintRuleGroup")
    }

    return TaintRuleFromSemgrep(ruleId, TaintRuleFromSemgrep.Structure.Matching(ruleGroups))
}

private fun <Item, Cond, Assign, Clean> RuleConversionCtx.convertAutomataToTaintRules(
    strategy: TaintRuleStrategy<Item, Cond, Assign, Clean>,
    metaVarInfo: ResolvedMetaVarInfo,
    taintAutomata: TaintRegisterStateAutomata,
    markPrefix: RuleUniqueMarkPrefix,
): TaintRuleFromSemgrep.TaintRuleGroup<Item> {
    val automataWithVars = TaintRegisterStateAutomataWithStateVars(
        taintAutomata,
        initialStateVars = emptySet(),
        acceptStateVars = emptySet()
    )
    val taintEdges = generateTaintAutomataEdges(automataWithVars, metaVarInfo)
    val ctx = TaintRuleGenerationCtx(markPrefix, taintEdges, compositionStrategy = null, strategy)

    val rules = strategy.generateTaintRules(ctx, this, SinkDiscardMode.TRIVIAL_CONDITION_WITH_EMPTY_FUNCTION)
    return ctx.createRuleGroup(rules)
}

private data class StarredPosition(val position: PositionBase, val star: Boolean)
private data class RegisterVarPosition(val varName: MetavarAtom, val positions: MutableSet<StarredPosition>)

private data class RuleCondition(
    val enclosingClassPackage: SerializedSimpleNameMatcher,
    val enclosingClassName: SerializedSimpleNameMatcher,
    val name: SerializedSimpleNameMatcher,
    val condition: SerializedCondition,
)

private data class EvaluatedEdgeCondition(
    val ruleCondition: RuleCondition,
    val additionalFieldRules: List<SerializedFieldRule>,
    val accessedVarPosition: Map<MetavarAtom, RegisterVarPosition>
)

private fun generateEndSink(
    cond: SerializedCondition,
    afterSinkActions: List<SerializedTaintAssignAction>,
    id: String,
    meta: SinkMetaData,
): List<SinkRule> {
    val endActions = afterSinkActions.map { it.copy(pos = it.pos.rewriteAsEndPosition()) }
    return generateMethodEndRule(
        cond = cond,
        generateWithoutMatchedEp = { f, endCondition ->
            listOf(
                SerializedRule.MethodExitSink(
                    f, signature = null, overrides = false, endCondition,
                    trackFactsReachAnalysisEnd = endActions,
                    id, meta = meta
                )
            )
        }
    )
}

private inline fun <R: SerializedItem> generateMethodEndRule(
    cond: SerializedCondition,
    generateWithoutMatchedEp: (SerializedFunctionNameMatcher, SerializedCondition) -> List<R>,
): List<R> {
    val endCondition = cond.rewriteAsEndCondition()
    return generateWithoutMatchedEp(anyFunction(),  endCondition)
}

private fun SerializedCondition.rewriteAsEndCondition(): SerializedCondition = when (this) {
    is SerializedCondition.And -> SerializedCondition.and(allOf.map { it.rewriteAsEndCondition() })
    is SerializedCondition.Or -> SerializedCondition.Or(anyOf.map { it.rewriteAsEndCondition() })
    is SerializedCondition.Not -> SerializedCondition.not(not.rewriteAsEndCondition())
    is SerializedCondition.True -> this
    is SerializedCondition.ClassAnnotated -> this
    is SerializedCondition.MethodAnnotated -> this
    is SerializedCondition.MethodNameMatches -> this
    is SerializedCondition.ClassNameMatches -> this
    is SerializedCondition.AnnotationType -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.ConstantCmp -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.ConstantEq -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.ConstantGt -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.ConstantLt -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.IsNull -> copy(isNull = isNull.rewriteAsEndPosition())
    is SerializedCondition.ConstantMatches -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.ContainsMark -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.IsConstant -> copy(isConstant = isConstant.rewriteAsEndPosition())
    is SerializedCondition.IsType -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.ParamAnnotated -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.IsStaticField -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.NumberOfArgs -> SerializedCondition.True
}

private fun PositionBaseWithModifiers.rewriteAsEndPosition() = when (this) {
    is PositionBaseWithModifiers.BaseOnly -> PositionBaseWithModifiers.BaseOnly(
        base.rewriteAsEndPosition()
    )

    is PositionBaseWithModifiers.WithModifiers -> PositionBaseWithModifiers.WithModifiers(
        base.rewriteAsEndPosition(), modifiers
    )
}

private fun PositionBase.rewriteAsEndPosition(): PositionBase = when (this) {
    is PositionBase.AnyArgument -> PositionBase.Result
    is PositionBase.Argument -> PositionBase.Result
    is PositionBase.ClassStatic -> this
    PositionBase.Result -> this
    PositionBase.This -> this
}

private fun generateMethodEndSource(
    cond: SerializedCondition,
    actions: List<SerializedTaintAssignAction>,
    info: UserRuleFromSemgrepInfo,
): List<SerializedRule.MethodExitSource> {
    val endActions = actions.map { it.copy(pos = it.pos.rewriteAsEndPosition()) }
    return generateMethodEndRule(
        cond = cond,
        generateWithoutMatchedEp = { f, endCond ->
            listOf(
                SerializedRule.MethodExitSource(
                    f, signature = null, overrides = false, endCond, endActions, info = info
                )
            )
        }
    )
}

private enum class TaintEdgeKind {
    POSITIVE, CLEANER
}

fun JavaTaintRuleGenerationCtx.emitJavaTaintRules(ctx: RuleConversionCtx): List<SerializedItem> {
    val rules = mutableListOf<Pair<TaintRuleEdge, SerializedItem>>()

    fun evaluateWithStateCheck(edge: TaintRuleEdge, kind: TaintEdgeKind, state: State): List<EvaluatedEdgeCondition> =
        evaluateMethodConditionAndEffect(kind, state, edge.edgeCondition, edge.edgeEffect, ctx.trace)
            .map { it.addStateCheck(this, edge.checkGlobalState, state) }

    for (ruleEdge in edges) {
        val state = ruleEdge.stateFrom

        for (condition in evaluateWithStateCheck(ruleEdge, TaintEdgeKind.POSITIVE, state)) {
            rules += condition.additionalFieldRules.map { ruleEdge to it }

            val actions = buildStateAssignAction(ruleEdge.stateTo, condition)
            if (actions.isEmpty()) continue

            val info = edgeRuleInfo(ruleEdge)
            rules += generateRules(condition.ruleCondition) { function, cond ->
                when (ruleEdge.edgeKind) {
                    TaintRuleEdge.Kind.MethodCall -> listOf(
                        SerializedRule.Source(
                            function, signature = null, overrides = true, cond, actions, info = info,
                        )
                    )

                    TaintRuleEdge.Kind.MethodEnter -> listOf(
                        SerializedRule.EntryPoint(
                            function, signature = null, overrides = false, cond, actions, info = info,
                        )
                    )

                    TaintRuleEdge.Kind.MethodExit -> {
                        generateMethodEndSource(cond, actions, info)
                    }
                }
            }.map { ruleEdge to it }
        }
    }

    for (ruleEdge in edgesToFinalAccept) {
        val state = ruleEdge.stateFrom

        for (condition in evaluateWithStateCheck(ruleEdge, TaintEdgeKind.POSITIVE, state)) {
            rules += condition.additionalFieldRules.map { ruleEdge to it }

            rules += generateRules(condition.ruleCondition) { function, cond ->
                val afterSinkActions = buildStateAssignAction(ruleEdge.stateTo, condition)

                when (ruleEdge.edgeKind) {
                    TaintRuleEdge.Kind.MethodEnter -> listOf(
                        SerializedRule.MethodEntrySink(
                            function, signature = null, overrides = false, cond,
                            trackFactsReachAnalysisEnd = afterSinkActions,
                            ctx.ruleId, meta = ctx.meta
                        )
                    )

                    TaintRuleEdge.Kind.MethodCall -> listOf(
                        SerializedRule.Sink(
                            function, signature = null, overrides = true, cond,
                            trackFactsReachAnalysisEnd = afterSinkActions,
                            ctx.ruleId, meta = ctx.meta
                        )
                    )

                    TaintRuleEdge.Kind.MethodExit -> {
                        generateEndSink(cond, afterSinkActions, ctx.ruleId, ctx.meta)
                    }
                }
            }.map { ruleEdge to it }
        }
    }

    for (ruleEdge in edgesToFinalDead) {
        val state = ruleEdge.stateFrom

        for (condition in evaluateWithStateCheck(ruleEdge, TaintEdgeKind.CLEANER, state)) {
            rules += condition.additionalFieldRules.map { ruleEdge to it }

            val actions = buildStateCleanAction(ruleEdge.stateTo, state, condition)
            if (actions.isEmpty()) continue

            when (ruleEdge.edgeKind) {
                TaintRuleEdge.Kind.MethodEnter, TaintRuleEdge.Kind.MethodExit -> {
                    ctx.trace.error(NonMethodCallCleaner())
                    continue
                }

                TaintRuleEdge.Kind.MethodCall -> {
                    rules += generateRules(condition.ruleCondition) { function, cond ->
                        listOf(
                            SerializedRule.Cleaner(
                                function, signature = null, overrides = true, cond, actions,
                                info = edgeRuleInfo(ruleEdge)
                            )
                        )
                    }.map { ruleEdge to it }
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

private fun SerializedItem.typeLabel(): String = when (this) {
    is SerializedRule.EntryPoint -> "entry-point"
    is SerializedRule.Source -> "source"
    is SerializedRule.Cleaner -> "cleaner"
    is SerializedRule.PassThrough -> "pass-through"
    is SerializedRule.Sink -> "sink"
    is SerializedRule.MethodExitSink -> "method-exit-sink"
    is SerializedRule.MethodEntrySink -> "method-entry-sink"
    is SerializedRule.MethodExitSource -> "method-exit-source"
    is SerializedFieldRule.SerializedStaticFieldSource -> "static-field-source"
}

private fun SerializedItem.withId(id: String): SerializedItem = when (this) {
    is SerializedRule.EntryPoint -> copy(serializedId = id)
    is SerializedRule.Source -> copy(serializedId = id)
    is SerializedRule.Cleaner -> copy(serializedId = id)
    is SerializedRule.PassThrough -> copy(serializedId = id)
    is SerializedRule.Sink -> copy(serializedId = id)
    is SerializedRule.MethodExitSink -> copy(serializedId = id)
    is SerializedRule.MethodEntrySink -> copy(serializedId = id)
    is SerializedRule.MethodExitSource -> copy(serializedId = id)
    is SerializedFieldRule.SerializedStaticFieldSource -> copy(serializedId = id)
}

private fun JavaTaintRuleGenerationCtx.buildStateAssignAction(
    state: State,
    edgeCondition: EvaluatedEdgeCondition
): List<SerializedTaintAssignAction> {
    val requiredVariables = state.register.assignedVars.keys
    val result = requiredVariables.flatMapTo(mutableListOf()) { varName ->
        val varPosition = edgeCondition.accessedVarPosition[varName] ?: return@flatMapTo emptyList()
        varPosition.positions.flatMap { sp ->
            sp.bases().flatMap {
                stateAssignMark(varPosition.varName, state, it)
            }
        }
    }

    if (state in globalStateAssignStates) {
        result += globalStateMarkName(state).mkAssignMark(stateVarPosition)
    }

    return result
}

private fun JavaTaintRuleGenerationCtx.buildStateCleanAction(
    state: State,
    stateBefore: State,
    edgeCondition: EvaluatedEdgeCondition
): List<SerializedTaintCleanAction> {
    val result = edgeCondition.accessedVarPosition.values.flatMapTo(mutableListOf()) { varPosition ->
        varPosition.positions.flatMap { sp ->
            sp.bases().flatMap {
                stateCleanMark(varPosition.varName, state, stateBefore, it)
            }
        }
    }

    result += stateCleanMark(varName = null, state, stateBefore, position = null)

    if (stateBefore in globalStateAssignStates) {
        result += globalStateMarkName(stateBefore).mkCleanMark(stateVarPosition)
    }

    return result
}

private fun EvaluatedEdgeCondition.addStateCheck(
    ctx: JavaTaintRuleGenerationCtx,
    checkGlobalState: Boolean,
    state: State
): EvaluatedEdgeCondition {
    val stateChecks = mutableListOf<SerializedCondition>()
    if (checkGlobalState) {
        stateChecks += ctx.globalStateMarkName(state).mkContainsMark(ctx.stateVarPosition)
    } else {
        for (metaVar in state.register.assignedVars.keys) {
            for (sp in accessedVarPosition[metaVar]?.positions.orEmpty()) {
                sp.bases().forEach {
                    stateChecks += ctx.containsStateMark(metaVar, state, it)
                }
            }
        }
    }

    if (stateChecks.isEmpty()) return this

    val stateCondition = serializedConditionOr(stateChecks)
    val rc = ruleCondition.condition
    return copy(ruleCondition = ruleCondition.copy(condition = SerializedCondition.and(listOf(stateCondition, rc))))
}

private inline fun <T> generateRules(
    condition: RuleCondition,
    body: (SerializedFunctionNameMatcher, SerializedCondition) -> T
): T {
    val functionMatcher = SerializedFunctionNameMatcher.Complex(
        condition.enclosingClassPackage,
        condition.enclosingClassName,
        condition.name
    )

    return body(functionMatcher, condition.condition)
}

private class RuleConditionBuilder {
    var enclosingClassPackage: SerializedSimpleNameMatcher? = null
    var enclosingClassName: SerializedSimpleNameMatcher? = null
    var methodName: SerializedSimpleNameMatcher? = null

    val conditions = hashSetOf<SerializedCondition>()

    fun copy(): RuleConditionBuilder = RuleConditionBuilder().also { n ->
        n.enclosingClassPackage = this.enclosingClassPackage
        n.enclosingClassName = this.enclosingClassName
        n.methodName = this.methodName
        n.conditions.addAll(conditions)
    }

    fun build(): RuleCondition = RuleCondition(
        enclosingClassPackage ?: anyName(),
        enclosingClassName ?: anyName(),
        methodName ?: anyName(),
        SerializedCondition.and(conditions.toList())
    )
}

private fun JavaTaintRuleGenerationCtx.evaluateMethodConditionAndEffect(
    edgeKind: TaintEdgeKind,
    state: State,
    condition: EdgeCondition,
    effect: EdgeEffect,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): List<EvaluatedEdgeCondition> {
    val evaluatedConditions = mutableListOf<EvaluatedEdgeCondition>()

    val (evaluatedSignature, ruleBuilders) = evaluateConditionAndEffectSignatures(effect, condition, semgrepRuleTrace)
    for (ruleBuilder in ruleBuilders) {
        val additionalFieldRules = mutableListOf<SerializedFieldRule>()

        condition.readMetaVar.values.flatten().forEach {
            val signature = it.predicate.signature.notEvaluatedSignature(evaluatedSignature)
            evaluateEdgePredicateConstraint(
                edgeKind, state,
                signature, it.predicate.constraint, it.negated,
                ruleBuilder.conditions, additionalFieldRules, semgrepRuleTrace
            )
        }

        condition.other.forEach {
            val signature = it.predicate.signature.notEvaluatedSignature(evaluatedSignature)
            evaluateEdgePredicateConstraint(
                edgeKind, state,
                signature, it.predicate.constraint, it.negated, ruleBuilder.conditions,
                additionalFieldRules, semgrepRuleTrace
            )
        }

        val varPositions = hashMapOf<MetavarAtom, RegisterVarPosition>()
        effect.assignMetaVar.values.flatten().forEach {
            findMetaVarPosition(it.predicate.constraint, varPositions)
        }

        evaluatedConditions += EvaluatedEdgeCondition(ruleBuilder.build(), additionalFieldRules, varPositions)
    }

    return evaluatedConditions
}

private fun MethodSignature.notEvaluatedSignature(evaluated: MethodSignature): MethodSignature? {
    if (this == evaluated) return null
    return MethodSignature(
        methodName = if (methodName == evaluated.methodName) {
            MethodName(SignatureName.AnyName)
        } else {
            methodName
        },
        enclosingClassName = if (enclosingClassName == evaluated.enclosingClassName) {
            MethodEnclosingClassName.anyClassName
        } else {
            enclosingClassName
        },
        returnType = if (returnType == evaluated.returnType) {
            null
        } else {
            returnType
        }
    )
}

private fun JavaTaintRuleGenerationCtx.evaluateConditionAndEffectSignatures(
    effect: EdgeEffect,
    condition: EdgeCondition,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): Pair<MethodSignature, List<RuleConditionBuilder>> {
    val signatures = mutableListOf<MethodSignature>()

    effect.assignMetaVar.values.flatten().forEach {
        check(!it.negated) { "Negated effect" }
        signatures.add(it.predicate.signature)
    }

    condition.readMetaVar.values.flatten().forEach {
        if (!it.negated) {
            signatures.add(it.predicate.signature)
        }
    }

    condition.other.forEach {
        if (!it.negated) {
            signatures.add(it.predicate.signature)
        }
    }

    return evaluateFormulaSignature(signatures, semgrepRuleTrace)
}

private fun JavaTaintRuleGenerationCtx.evaluateFormulaSignature(
    signatures: List<MethodSignature>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): Pair<MethodSignature, List<RuleConditionBuilder>> {
    val signature = signatures.first()

    if (signatures.any { it != signature }) {
        TODO("Signature mismatch")
    }

    if (signature.isGeneratedAnyValueGenerator()) {
        TODO("Eliminate generated method")
    }

    if (signature.isGeneratedStringConcat()) {
        TODO("Eliminate generated string concat")
    }

    val methodName = signature.methodName.name

    val evaluatedMethodName = evaluateFormulaSignatureMethodName(methodName, semgrepRuleTrace)
    val buildersWithMethodName = evaluatedMethodName.map { (name, methodConds) ->
        RuleConditionBuilder().also { builder ->
            builder.methodName = name
            methodConds?.let { builder.conditions.add(it) }
        }
    }

    signature.returnType?.let { returnType ->
        val condition = evaluateFormulaSignatureReturnType(returnType, semgrepRuleTrace)
        buildersWithMethodName.forEach { builder ->
            builder.conditions += condition
        }
    }

    val classSignatureMatcherFormula = typeMatcher(signature.enclosingClassName.name, semgrepRuleTrace)
    if (classSignatureMatcherFormula == null) return signature to buildersWithMethodName

    val buildersWithClass = mutableListOf<RuleConditionBuilder>()

    val classSignatureMatcherDnf = classSignatureMatcherFormula.toDNF()
    for (cube in classSignatureMatcherDnf) {
        val builders = buildersWithMethodName.map { it.copy() }

        cube.negative.forEach { c ->
            builders.forEach { builder ->
                builder.conditions += SerializedCondition.not(
                    SerializedCondition.ClassNameMatches(c.constraint)
                )
            }
        }

        if (cube.positive.isEmpty()) {
            buildersWithClass.addAll(builders)
            continue
        }

        if (cube.positive.size > 1) {
            TODO("Complex class signature matcher")
        }

        val classSignatureMatcher = cube.positive.first().constraint
        val (cp, cn) = when (classSignatureMatcher) {
            is SerializedTypeNameMatcher.ClassPattern -> {
                classSignatureMatcher.`package` to classSignatureMatcher.`class`
            }

            is Simple -> {
                classNamePartsFromConcreteString(classSignatureMatcher.value)
            }

            is SerializedTypeNameMatcher.Array -> {
                TODO("Signature class is array")
            }

            is Pattern -> {
                TODO("Signature class name pattern")
            }
        }

        builders.mapTo(buildersWithClass) { builder ->
            builder.copy().apply {
                enclosingClassPackage = cp
                enclosingClassName = cn
            }
        }
    }

    return signature to buildersWithClass
}

private fun JavaTaintRuleGenerationCtx.evaluateFormulaSignatureReturnType(
    returnType: TypeConstraint,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): SerializedCondition {
    val returnTypeFormula = typeMatcher(returnType, semgrepRuleTrace)
    return returnTypeFormula.toSerializedCondition { typeNameMatcher ->
        SerializedCondition.IsType(typeNameMatcher, PositionBase.Result)
    }
}

private fun JavaTaintRuleGenerationCtx.evaluateFormulaSignatureMethodName(
    methodName: SignatureName,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): List<Pair<Simple?, SerializedCondition?>> {
    return when (methodName) {
        SignatureName.AnyName -> listOf(null to null)
        is SignatureName.Concrete -> listOf(Simple(methodName.name) to null)
        is SignatureName.MetaVar -> {
            val constraint = when (val constraints = metaVarInfo.constraints[methodName.metaVar]) {
                null -> null
                is MetaVarConstraintOrPlaceHolder.Constraint -> constraints.constraint
                is MetaVarConstraintOrPlaceHolder.PlaceHolder -> {
                    semgrepRuleTrace.error(PlaceholderMethodName())
                    constraints.constraint
                }
            }

            if (constraint == null) return listOf(null to null)

            val conditionsWithConcreteNames = constraint.constraint.toSerializedConditionCubes(
                transformPositive = { c ->
                    when (c) {
                        is MetaVarConstraint.Concrete -> SerializedCondition.True to c.value
                        is MetaVarConstraint.RegExp -> SerializedCondition.MethodNameMatches(Pattern(c.regex)) to null
                    }
                },
                transformNegated = { c ->
                    when (c) {
                        is MetaVarConstraint.Concrete -> methodNameMatcherCondition(c.value) to null
                        is MetaVarConstraint.RegExp -> SerializedCondition.MethodNameMatches(Pattern(c.regex)) to null
                    }
                }
            )

            conditionsWithConcreteNames.map { (cond, concrete) ->
                val concreteNames = concrete.filterNotNull()
                check(concreteNames.size <= 1) { "Multiple concrete names" }
                concreteNames.firstOrNull()?.let { Simple(it) } to cond
            }
        }
    }
}

private fun methodNameMatcherCondition(methodNameConstraint: String): SerializedCondition {
    val methodName = methodNameConstraint.substringAfterLast('.')
    val className = methodNameConstraint.substringBeforeLast('.', "")

    val methodNameMatcher = SerializedCondition.MethodNameMatches(Simple(methodName))
    val classNameMatcher: SerializedCondition.ClassNameMatches? =
        className.takeIf { it.isNotEmpty() }?.let {
            SerializedCondition.ClassNameMatches(classNameMatcherFromConcreteString(it))
        }

    return SerializedCondition.and(listOfNotNull(methodNameMatcher, classNameMatcher))
}

private fun classNamePartsFromConcreteString(name: String): Pair<Simple, Simple> {
    val parts = name.split(".")
    val packageName = parts.dropLast(1).joinToString(separator = ".")
    return Simple(packageName) to Simple(parts.last())
}

private fun classNameMatcherFromConcreteString(name: String): SerializedTypeNameMatcher.ClassPattern {
    val (pkg, cls) = classNamePartsFromConcreteString(name)
    return SerializedTypeNameMatcher.ClassPattern(pkg, cls)
}

private fun anyClassPattern(): SerializedTypeNameMatcher.ClassPattern =
    SerializedTypeNameMatcher.ClassPattern(
        `package` = anyName(),
        `class` = anyName()
    )

private fun JavaTaintRuleGenerationCtx.evaluateEdgePredicateConstraint(
    edgeKind: TaintEdgeKind,
    state: State,
    signature: MethodSignature?,
    constraint: MethodConstraint?,
    negated: Boolean,
    conditions: MutableSet<SerializedCondition>,
    additionalFieldRules: MutableList<SerializedFieldRule>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
) {
    if (!negated) {
        evaluateMethodConstraints(
            edgeKind,
            state,
            signature,
            constraint,
            conditions,
            additionalFieldRules,
            semgrepRuleTrace
        )
    } else {
        val negatedConditions = hashSetOf<SerializedCondition>()
        evaluateMethodConstraints(
            edgeKind,
            state,
            signature,
            constraint,
            negatedConditions,
            additionalFieldRules,
            semgrepRuleTrace
        )
        conditions += SerializedCondition.not(SerializedCondition.and(negatedConditions.toList()))
    }
}

private fun JavaTaintRuleGenerationCtx.evaluateMethodConstraints(
    edgeKind: TaintEdgeKind,
    state: State,
    signature: MethodSignature?,
    constraint: MethodConstraint?,
    conditions: MutableSet<SerializedCondition>,
    additionalFieldRules: MutableList<SerializedFieldRule>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
) {
    if (signature != null) {
        evaluateMethodSignatureCondition(signature, conditions, semgrepRuleTrace)
    }

    when (constraint) {
        null -> {}

        is ClassModifierConstraint -> {
            when (val c = constraint.constraint) {
                is ClassConstraint.Signature -> {
                    val annotations = signatureModifierConstraint(c.modifier, semgrepRuleTrace)
                    conditions += annotations.toSerializedCondition { annotation ->
                        SerializedCondition.ClassAnnotated(annotation)
                    }
                }

                is ClassConstraint.SuperType -> {
                    // note: class type constraint is meaningful only for instance methods
                    conditions += typeMatcher(c.superType, semgrepRuleTrace).toSerializedCondition { typeNameMatcher ->
                        SerializedCondition.IsType(typeNameMatcher, PositionBase.This)
                    }
                }
            }
        }

        is MethodModifierConstraint -> {
            val annotations = signatureModifierConstraint(constraint.modifier, semgrepRuleTrace)
            conditions += annotations.toSerializedCondition { annotation ->
                SerializedCondition.MethodAnnotated(annotation)
            }
        }

        is NumberOfArgsConstraint -> conditions += SerializedCondition.NumberOfArgs(constraint.num)
        is ParamConstraint -> evaluateParamConstraints(
            edgeKind,
            state,
            constraint,
            conditions,
            additionalFieldRules,
            semgrepRuleTrace
        )
    }
}

private fun JavaTaintRuleGenerationCtx.evaluateMethodSignatureCondition(
    signature: MethodSignature,
    conditions: MutableSet<SerializedCondition>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
) {
    val classType = typeMatcher(signature.enclosingClassName.name, semgrepRuleTrace)
    conditions += classType.toSerializedCondition { typeMatcher ->
        SerializedCondition.ClassNameMatches(typeMatcher)
    }

    val evaluatedSignatures = evaluateFormulaSignatureMethodName(signature.methodName.name, semgrepRuleTrace)
    conditions += evaluatedSignatures.toSerializedOr { (methodName, methodCond) ->
        val cond = mutableListOf<SerializedCondition>()
        methodCond?.let { cond += it }

        if (methodName != null) {
            val methodNameRegex = "^${methodName.value}$"
            cond += SerializedCondition.MethodNameMatches(Pattern(methodNameRegex))
        }

        SerializedCondition.and(cond)
    }

    signature.returnType?.let {
        conditions += evaluateFormulaSignatureReturnType(it, semgrepRuleTrace)
    }
}

private fun findMetaVarPosition(
    constraint: MethodConstraint?,
    varPositions: MutableMap<MetavarAtom, RegisterVarPosition>
) {
    if (constraint !is ParamConstraint) return
    findMetaVarPosition(constraint, varPositions)
}

private fun JavaTaintRuleGenerationCtx.typeMatcher(
    typeName: TypeConstraint,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): MetaVarConstraintFormula<SerializedTypeNameMatcher>? = when (typeName) {
    TypeConstraint.Any -> null
    is TypeConstraint.MetaVar -> metaVarTypeMatcher(typeName.metaVar, semgrepRuleTrace)
    is TypeConstraint.Concrete -> when (val t = typeName.type) {
        is JavaConcreteType -> javaConcreteTypeMatcher(t, semgrepRuleTrace)
        else -> MetaVarConstraintFormula.Constraint(Simple(t.toString()))
    }
}

private fun JavaTaintRuleGenerationCtx.javaConcreteTypeMatcher(
    type: JavaConcreteType,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): MetaVarConstraintFormula<SerializedTypeNameMatcher>? {
    return when (type) {
        is JavaConcreteType.ClassName -> {
            val serializedTypeArgs = typeArgsMatcher(type.typeArgs, semgrepRuleTrace)
            MetaVarConstraintFormula.Constraint(
                SerializedTypeNameMatcher.ClassPattern(
                    `package` = anyName(),
                    `class` = Simple(type.name),
                    typeArgs = serializedTypeArgs
                )
            )
        }

        is JavaConcreteType.FullyQualified -> {
            val serializedTypeArgs = typeArgsMatcher(type.typeArgs, semgrepRuleTrace)
            if (serializedTypeArgs == null) {
                MetaVarConstraintFormula.Constraint(
                    Simple(type.name)
                )
            } else {
                val (pkg, cls) = classNamePartsFromConcreteString(type.name)
                MetaVarConstraintFormula.Constraint(
                    SerializedTypeNameMatcher.ClassPattern(
                        `package` = pkg,
                        `class` = cls,
                        typeArgs = serializedTypeArgs
                    )
                )
            }
        }

        is JavaConcreteType.PrimitiveName -> {
            MetaVarConstraintFormula.Constraint(
                Simple(type.name)
            )
        }

        is JavaConcreteType.ArrayType -> {
            typeMatcher(type.element, semgrepRuleTrace)?.transform { matcher ->
                SerializedTypeNameMatcher.Array(matcher)
            }
        }
    }
}

private fun JavaTaintRuleGenerationCtx.metaVarTypeMatcher(
    metaVar: String,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): MetaVarConstraintFormula<SerializedTypeNameMatcher>? {
    val constraints = metaVarInfo.constraints[metaVar]
    val constraint = when (constraints) {
        null -> null
        is MetaVarConstraintOrPlaceHolder.Constraint -> constraints.constraint.constraint
        is MetaVarConstraintOrPlaceHolder.PlaceHolder -> {
            semgrepRuleTrace.error(PlaceholderTypeName())
            constraints.constraint?.constraint
        }
    }

    if (constraint == null) return null

    return constraint.transform { value ->
        // todo hack: here we assume that if name contains '.' then name is fqn
        when (value) {
            is MetaVarConstraint.Concrete -> {
                if (value.value.contains('.')) {
                    Simple(value.value)
                } else {
                    SerializedTypeNameMatcher.ClassPattern(
                        `package` = anyName(),
                        `class` = Simple(value.value)
                    )
                }
            }

            is MetaVarConstraint.RegExp -> {
                val pkgPattern = value.regex.substringBeforeLast("\\.", missingDelimiterValue = "")
                if (pkgPattern.isNotEmpty()) {
                    val clsPattern = value.regex.substringAfterLast("\\.")
                    if (clsPattern.patternCanMatchDot()) {
                        if (value.regex.endsWith('*') && value.regex.let { it.lowercase() == it }) {
                            // consider pattern as package pattern
                            SerializedTypeNameMatcher.ClassPattern(
                                `package` = Pattern(value.regex),
                                `class` = anyName()
                            )
                        } else {
                            Pattern(value.regex)
                        }
                    } else {
                        SerializedTypeNameMatcher.ClassPattern(
                            `package` = Pattern(pkgPattern),
                            `class` = Pattern(clsPattern)
                        )
                    }
                } else {
                    SerializedTypeNameMatcher.ClassPattern(
                        `package` = anyName(),
                        `class` = Pattern(value.regex)
                    )
                }
            }
        }
    }
}

private fun JavaTaintRuleGenerationCtx.typeArgsMatcher(
    typeArgs: List<TypeConstraint>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace
): List<SerializedTypeNameMatcher>? = typeArgs.takeIf { it.isNotEmpty() }?.map {
    (typeMatcher(it, semgrepRuleTrace) as? MetaVarConstraintFormula.Constraint<SerializedTypeNameMatcher>)?.constraint
        ?: anyClassPattern()
}

private fun String.patternCanMatchDot(): Boolean =
    '.' in this || '-' in this // [A-Z]

private fun JavaTaintRuleGenerationCtx.signatureModifierConstraint(
    modifier: SignatureModifier,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace
): MetaVarConstraintFormula<SerializedCondition.AnnotationConstraint> {
    val params = annotationParamMatchers(modifier, metaVarInfo, semgrepRuleTrace)

    val typeMatcherFormula = typeMatcher(modifier.type, semgrepRuleTrace)
    if (typeMatcherFormula == null) {
        val type = anyName()
        return params.transform {
            SerializedCondition.AnnotationConstraint(type, it)
        }
    }

    return typeMatcherFormula.flatMap { typeLit ->
        params.transform { p ->
            if (p != null && typeLit is MetaVarConstraintFormula.NegatedConstraint) {
                TODO("Negated annotation type with param constraints")
            }

            SerializedCondition.AnnotationConstraint(typeLit.constraint, p)
        }
    }
}

private fun annotationParamMatchers(
    modifier: SignatureModifier,
    metaVarInfo: TaintRuleGenerationMetaVarInfo,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace
): MetaVarConstraintFormula<List<AnnotationParamMatcher>?> {
    val simpleParamMatcher = when (val v = modifier.value) {
        SignatureModifierValue.AnyValue -> null
        SignatureModifierValue.NoValue -> emptyList()
        is SignatureModifierValue.StringValue -> listOf(
            AnnotationParamStringMatcher(Simple(v.paramName), Simple(v.value))
        )

        is SignatureModifierValue.StringPattern -> listOf(
            AnnotationParamStringMatcher(Simple(v.paramName), Pattern(v.pattern))
        )

        is SignatureModifierValue.MetaVar -> {
            val constraints = metaVarInfo.constraints[v.metaVar]
            val constraint = when (constraints) {
                null -> null
                is MetaVarConstraintOrPlaceHolder.Constraint -> constraints.constraint.constraint
                is MetaVarConstraintOrPlaceHolder.PlaceHolder -> {
                    semgrepRuleTrace.error(PlaceholderAnnotation())
                    constraints.constraint?.constraint
                }
            }

            if (constraint == null) {
                val anyValue = AnnotationParamStringMatcher(Simple(v.paramName), anyName())
                return MetaVarConstraintFormula.Constraint(listOf(anyValue))
            }

            val constraintCubes = constraint.toDNF()
            val paramMatcherCubes = mutableSetOf<MetaVarConstraintFormula<List<AnnotationParamMatcher>?>>()
            constraintCubes.mapTo(paramMatcherCubes) { cube ->
                if (cube.negative.isNotEmpty()) {
                    TODO("Negated annotation param condition")
                }

                val paramMatchers = cube.positive.map {
                    when (val c = it.constraint) {
                        is MetaVarConstraint.Concrete -> AnnotationParamStringMatcher(Simple(v.paramName), Simple(c.value))
                        is MetaVarConstraint.RegExp -> AnnotationParamStringMatcher(Simple(v.paramName), Pattern(c.regex))
                    }
                }

                MetaVarConstraintFormula.Constraint(paramMatchers)
            }
            return MetaVarConstraintFormula.mkOr(paramMatcherCubes)
        }
    }

    return MetaVarConstraintFormula.Constraint(simpleParamMatcher)
}

private fun Position.toSerializedPosition(): PositionBase = when (this) {
    is Position.Argument -> when (index) {
        is Position.ArgumentIndex.Any -> PositionBase.AnyArgument(index.paramClassifier)
        is Position.ArgumentIndex.Concrete -> PositionBase.Argument(index.idx)
    }

    is Position.Object -> PositionBase.This
    is Position.Result -> PositionBase.Result
}

private fun JavaTaintRuleGenerationCtx.evaluateParamConstraints(
    edgeKind: TaintEdgeKind,
    state: State,
    param: ParamConstraint,
    conditions: MutableSet<SerializedCondition>,
    additionalFieldRules: MutableList<SerializedFieldRule>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
) {
    val position = param.position.toSerializedPosition()
    conditions += evaluateParamCondition(
        edgeKind, state, position, param.condition,
        additionalFieldRules, semgrepRuleTrace
    )
}

private fun findMetaVarPosition(
    param: ParamConstraint,
    varPositions: MutableMap<MetavarAtom, RegisterVarPosition>
) {
    val position = param.position.toSerializedPosition()
    findMetaVarPosition(position, param.condition, varPositions)
}

private fun findMetaVarPosition(
    position: PositionBase,
    condition: ParamCondition.Atom,
    varPositions: MutableMap<MetavarAtom, RegisterVarPosition>
) {
    if (condition !is IsMetavar) return
    val varPosition = varPositions.getOrPut(condition.metavar) {
        RegisterVarPosition(condition.metavar, hashSetOf())
    }
    varPosition.positions.add(StarredPosition(position, condition.star))
}

private fun JavaTaintRuleGenerationCtx.evaluateParamCondition(
    edgeKind: TaintEdgeKind,
    state: State,
    position: PositionBase,
    condition: ParamCondition.Atom,
    additionalFieldRules: MutableList<SerializedFieldRule>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): SerializedCondition {
    when (condition) {
        is IsMetavar -> {
            val constraints = metaVarInfo.constraints[condition.metavar.toString()]
            if (constraints != null) {
                // todo: semantic metavar constraint
                semgrepRuleTrace.error(IgnoredMetavarConstraint(condition.metavar))
            }

            val contains = containsMarkWithAnyStateBefore(state, condition.metavar, position.base())
            if (!condition.star) return contains

            val containsAnyField = containsMarkWithAnyStateBefore(state, condition.metavar, position.base().withAnyField())
            return serializedConditionOr(listOf(contains, containsAnyField))
        }

        is ParamCondition.TypeIs -> {
            return typeMatcher(condition.typeName, semgrepRuleTrace).toSerializedCondition { typeNameMatcher ->
                SerializedCondition.IsType(typeNameMatcher, position)
            }
        }

        is ParamCondition.SpecificStaticFieldValue -> {
            val enclosingClassMatcherFormula = typeMatcher(condition.fieldClass, semgrepRuleTrace)

            val enclosingClassMatcher = when (enclosingClassMatcherFormula) {
                null -> anyName()
                is MetaVarConstraintFormula.Constraint -> enclosingClassMatcherFormula.constraint
                else -> TODO("Complex static field type")
            }

            val fieldNameMatcher = Simple(condition.fieldName)

            return when (edgeKind) {
                TaintEdgeKind.POSITIVE -> {
                    val metaVar = MetavarAtom.createArtificial("__STATIC_FIELD_VALUE__${condition.fieldName}")
                    val mark = prefix.metaVarState(metaVar, state = 0)

                    val action = mark.mkAssignMark(PositionBase.Result.base())
                    additionalFieldRules += SerializedFieldRule.SerializedStaticFieldSource(
                        enclosingClassMatcher, fieldNameMatcher, condition = null, listOf(action)
                    )

                    mark.mkContainsMark(position.base())
                }

                TaintEdgeKind.CLEANER -> {
                    SerializedCondition.IsStaticField(position, enclosingClassMatcher, fieldNameMatcher)
                }
            }
        }

        ParamCondition.AnyStringLiteral -> {
            return SerializedCondition.and(
                listOf(
                    SerializedCondition.IsType(StringTypeName, position),
                    SerializedCondition.ConstantMatches(constantMatches = ".*", position)
                )
            )
        }

        is SpecificBoolValue -> {
            val value = ConstantValue(ConstantType.Bool, condition.value.toString())
            return SerializedCondition.ConstantCmp(position, value, ConstantCmpType.Eq)
        }

        is SpecificIntValue -> {
            val value = ConstantValue(ConstantType.Int, condition.value.toString())
            return SerializedCondition.ConstantCmp(position, value, ConstantCmpType.Eq)
        }

        is SpecificStringValue -> {
            val value = ConstantValue(ConstantType.Str, condition.value)
            return SerializedCondition.ConstantCmp(position, value, ConstantCmpType.Eq)
        }

        is SpecificNullValue -> {
            return SerializedCondition.IsNull(position)
        }

        is StringValueMetaVar -> {
            val constraints = metaVarInfo.constraints[condition.metaVar.toString()]
            val constraint = when (constraints) {
                null -> null
                is MetaVarConstraintOrPlaceHolder.Constraint -> constraints.constraint.constraint
                is MetaVarConstraintOrPlaceHolder.PlaceHolder -> {
                    semgrepRuleTrace.error(PlaceholderStringValue())
                    constraints.constraint?.constraint
                }
            }

            return constraint.toSerializedCondition { c ->
                when (c) {
                    is MetaVarConstraint.Concrete -> {
                        val value = ConstantValue(ConstantType.Str, c.value)
                        SerializedCondition.ConstantCmp(position, value, ConstantCmpType.Eq)
                    }

                    is MetaVarConstraint.RegExp -> {
                        SerializedCondition.and(
                            listOf(
                                SerializedCondition.IsType(StringTypeName, position),
                                SerializedCondition.ConstantMatches(c.regex, position)
                            )
                        )
                    }
                }
            }
        }

        is ParamCondition.ParamModifier -> {
            val annotations = signatureModifierConstraint(condition.modifier, semgrepRuleTrace)
            return annotations.toSerializedCondition { annotation ->
                SerializedCondition.ParamAnnotated(position, annotation)
            }
        }
    }
}

private fun <T> MetaVarConstraintFormula<T>?.toSerializedCondition(
    transform: (T) -> SerializedCondition,
): SerializedCondition {
    if (this == null) return SerializedCondition.True
    return toSerializedConditionWrtLiteral { transform(it.constraint) }
}

private fun <T> MetaVarConstraintFormula<T>.toSerializedConditionWrtLiteral(
    transform: (MetaVarConstraintFormula.Literal<T>) -> SerializedCondition,
): SerializedCondition = toSerializedConditionUtil(transform)

private fun <T> MetaVarConstraintFormula<T>.toSerializedConditionUtil(
    transform: (MetaVarConstraintFormula.Literal<T>) -> SerializedCondition,
): SerializedCondition = when (this) {
    is MetaVarConstraintFormula.Constraint -> {
        transform(this)
    }

    is MetaVarConstraintFormula.NegatedConstraint -> {
        SerializedCondition.not(transform(this))
    }

    is MetaVarConstraintFormula.And -> {
        SerializedCondition.and(args.map { it.toSerializedConditionUtil(transform) })
    }

    is MetaVarConstraintFormula.Or -> {
        serializedConditionOr(args.map { it.toSerializedConditionUtil(transform) })
    }
}

private fun <T, R> MetaVarConstraintFormula<T>.toSerializedConditionCubes(
    transformPositive: (T) -> Pair<SerializedCondition, R>,
    transformNegated: (T) -> Pair<SerializedCondition, R>
): List<Pair<SerializedCondition, List<R>>> {
    val dnf = toDNF()
    return dnf.map { cube ->
        val results = mutableListOf<R>()
        val conds = mutableListOf<SerializedCondition>()
        cube.positive.mapTo(conds) {
            val (c, r) = transformPositive(it.constraint)
            results += r
            c
        }
        cube.negative.mapTo(conds) {
            val (c, r) = transformNegated(it.constraint)
            results += r
            SerializedCondition.not(c)
        }
        SerializedCondition.and(conds) to results
    }
}

private fun StarredPosition.bases(): List<PositionBaseWithModifiers> {
    val pos = position.base()
    return if (!star) listOf(pos) else listOf(pos, pos.withAnyField())
}

private fun <T> List<T>.toSerializedOr(transformer: (T) -> SerializedCondition): SerializedCondition =
    serializedConditionOr(map(transformer))

private val StringTypeName = Simple("java.lang.String")
