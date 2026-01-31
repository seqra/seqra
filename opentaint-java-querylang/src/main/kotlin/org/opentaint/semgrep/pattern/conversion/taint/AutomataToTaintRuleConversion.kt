package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationParamMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationParamPatternMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationParamStringMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.ConstantCmpType
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.ConstantType
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.ConstantValue
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFieldRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFunctionNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedItem
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintAssignAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintCleanAction
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.opentaint.dataflow.configuration.jvm.serialized.SinkRule
import org.opentaint.semgrep.pattern.Mark.RuleUniqueMarkPrefix
import org.opentaint.semgrep.pattern.MetaVarConstraint
import org.opentaint.semgrep.pattern.MetaVarConstraintFormula
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.RuleWithMetaVars
import org.opentaint.semgrep.pattern.SemgrepErrorEntry.Reason
import org.opentaint.semgrep.pattern.SemgrepMatchingRule
import org.opentaint.semgrep.pattern.SemgrepRule
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace
import org.opentaint.semgrep.pattern.SemgrepTaintRule
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.UserRuleFromSemgrepInfo
import org.opentaint.semgrep.pattern.conversion.IsMetavar
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
import org.opentaint.semgrep.pattern.conversion.TypeNamePattern
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

fun RuleConversionCtx.convertTaintAutomataToTaintRules(
    rule: SemgrepRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
): TaintRuleFromSemgrep = when (rule) {
    is SemgrepMatchingRule -> convertMatchingRuleToTaintRules(rule)
    is SemgrepTaintRule -> convertTaintRuleToTaintRules(rule)
}

fun <R> RuleConversionCtx.safeConvertToTaintRules(body: () -> R): R? =
    runCatching {
        body()
    }.onFailure { ex ->
        trace.error("Failed to convert to taint rule for: ${ex.message}", Reason.ERROR)
    }.getOrNull()

private fun RuleConversionCtx.convertMatchingRuleToTaintRules(
    rule: SemgrepMatchingRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
): TaintRuleFromSemgrep {
    val ruleGroups = rule.rules.mapIndexedNotNull { idx, r ->
        val rules = safeConvertToTaintRules {
            convertAutomataToTaintRules(r.metaVarInfo, r.rule, RuleUniqueMarkPrefix(ruleId, idx))
        }

        rules?.let(TaintRuleFromSemgrep::TaintRuleGroup)
    }

    if (ruleGroups.isEmpty()) {
        error("Failed to generate any taintRuleGroup")
    }

    return TaintRuleFromSemgrep(ruleId, ruleGroups)
}

private fun RuleConversionCtx.convertAutomataToTaintRules(
    metaVarInfo: ResolvedMetaVarInfo,
    taintAutomata: TaintRegisterStateAutomata,
    markPrefix: RuleUniqueMarkPrefix,
): List<SerializedItem> {
    val automataWithVars = TaintRegisterStateAutomataWithStateVars(
        taintAutomata,
        initialStateVars = emptySet(),
        acceptStateVars = emptySet()
    )
    val taintEdges = generateTaintAutomataEdges(automataWithVars, metaVarInfo)
    val ctx = TaintRuleGenerationCtx(markPrefix, taintEdges, compositionStrategy = null)

    val rules = ctx.generateTaintRules(this)
    val filteredRules = rules.filter { r ->
        if (r !is SinkRule) return@filter true
        if (r.condition != null && r.condition !is SerializedCondition.True) return@filter true

        val function = when (r) {
            is SerializedRule.MethodEntrySink -> r.function
            is SerializedRule.MethodExitSink -> r.function
            is SerializedRule.Sink -> r.function
        }

        if (!function.matchAnything()) return@filter true

        trace.error("Taint rule match anything", Reason.WARNING)
        false
    }

    return filteredRules
}

private data class RegisterVarPosition(val varName: MetavarAtom, val positions: MutableSet<PositionBase>)

private data class RuleCondition(
    val enclosingClassPackage: SerializedNameMatcher,
    val enclosingClassName: SerializedNameMatcher,
    val name: SerializedNameMatcher,
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

fun TaintRuleGenerationCtx.generateTaintRules(ctx: RuleConversionCtx): List<SerializedItem> {
    val rules = mutableListOf<SerializedItem>()

    val evaluatedConditions = hashMapOf<TaintRuleEdge, List<EvaluatedEdgeCondition>>()

    fun evaluate(edge: TaintRuleEdge): List<EvaluatedEdgeCondition> =
        evaluatedConditions.getOrPut(edge) {
            evaluateMethodConditionAndEffect(edge.edgeCondition, edge.edgeEffect, ctx.trace)
        }

    fun evaluateWithStateCheck(edge: TaintRuleEdge, state: State): List<EvaluatedEdgeCondition> =
        evaluate(edge).map { it.addStateCheck(this, edge.checkGlobalState, state) }

    for (ruleEdge in edges) {
        val state = ruleEdge.stateFrom

        for (condition in evaluateWithStateCheck(ruleEdge, state)) {
            rules += condition.additionalFieldRules

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
            }
        }
    }

    for (ruleEdge in edgesToFinalAccept) {
        val state = ruleEdge.stateFrom

        for (condition in evaluateWithStateCheck(ruleEdge, state)) {
            rules += condition.additionalFieldRules

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
            }
        }
    }

    for (ruleEdge in edgesToFinalDead) {
        val state = ruleEdge.stateFrom

        for (condition in evaluateWithStateCheck(ruleEdge, state)) {
            rules += condition.additionalFieldRules

            val actions = buildStateCleanAction(ruleEdge.stateTo, state, condition)
            if (actions.isEmpty()) continue

            when (ruleEdge.edgeKind) {
                TaintRuleEdge.Kind.MethodEnter, TaintRuleEdge.Kind.MethodExit -> {
                    ctx.trace.error("Non method call cleaner", Reason.NOT_IMPLEMENTED)
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
                    }
                }
            }
        }
    }

    return rules
}

private fun TaintRuleGenerationCtx.buildStateAssignAction(
    state: State,
    edgeCondition: EvaluatedEdgeCondition
): List<SerializedTaintAssignAction> {
    val requiredVariables = state.register.assignedVars.keys
    val result = requiredVariables.flatMapTo(mutableListOf()) { varName ->
        val varPosition = edgeCondition.accessedVarPosition[varName] ?: return@flatMapTo emptyList()
        varPosition.positions.flatMap {
            stateAssignMark(varPosition.varName, state, it.base())
        }
    }

    if (state in globalStateAssignStates) {
        result += globalStateMarkName(state).mkAssignMark(stateVarPosition)
    }

    return result
}

private fun TaintRuleGenerationCtx.buildStateCleanAction(
    state: State,
    stateBefore: State,
    edgeCondition: EvaluatedEdgeCondition
): List<SerializedTaintCleanAction> {
    val result = edgeCondition.accessedVarPosition.values.flatMapTo(mutableListOf()) { varPosition ->
        varPosition.positions.flatMap {
            stateCleanMark(varPosition.varName, state, stateBefore, it.base())
        }
    }

    result += stateCleanMark(varName = null, state, stateBefore, position = null)

    if (stateBefore in globalStateAssignStates) {
        result += globalStateMarkName(stateBefore).mkCleanMark(stateVarPosition)
    }

    return result
}

private fun EvaluatedEdgeCondition.addStateCheck(
    ctx: TaintRuleGenerationCtx,
    checkGlobalState: Boolean,
    state: State
): EvaluatedEdgeCondition {
    val stateChecks = mutableListOf<SerializedCondition>()
    if (checkGlobalState) {
        stateChecks += ctx.globalStateMarkName(state).mkContainsMark(ctx.stateVarPosition)
    } else {
        for (metaVar in state.register.assignedVars.keys) {
            for (pos in accessedVarPosition[metaVar]?.positions.orEmpty()) {
                stateChecks += ctx.containsStateMark(metaVar, state, pos.base())
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
    var enclosingClassPackage: SerializedNameMatcher? = null
    var enclosingClassName: SerializedNameMatcher? = null
    var methodName: SerializedNameMatcher? = null

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

private fun TaintRuleGenerationCtx.evaluateMethodConditionAndEffect(
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
                signature, it.predicate.constraint, it.negated,
                ruleBuilder.conditions, additionalFieldRules, semgrepRuleTrace
            )
        }

        condition.other.forEach {
            val signature = it.predicate.signature.notEvaluatedSignature(evaluatedSignature)
            evaluateEdgePredicateConstraint(
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
        }
    )
}

private fun TaintRuleGenerationCtx.evaluateConditionAndEffectSignatures(
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

private fun TaintRuleGenerationCtx.evaluateFormulaSignature(
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
            is SerializedNameMatcher.ClassPattern -> {
                classSignatureMatcher.`package` to classSignatureMatcher.`class`
            }

            is SerializedNameMatcher.Simple -> {
                val parts = classSignatureMatcher.value.split(".")
                val packageName = parts.dropLast(1).joinToString(separator = ".")
                SerializedNameMatcher.Simple(packageName) to SerializedNameMatcher.Simple(parts.last())
            }

            is SerializedNameMatcher.Array -> {
                TODO("Signature class is array")
            }

            is SerializedNameMatcher.Pattern -> {
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

private fun TaintRuleGenerationCtx.evaluateFormulaSignatureMethodName(
    methodName: SignatureName,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): List<Pair<SerializedNameMatcher.Simple?, SerializedCondition?>> {
    return when (methodName) {
        SignatureName.AnyName -> listOf(null to null)
        is SignatureName.Concrete -> listOf(SerializedNameMatcher.Simple(methodName.name) to null)
        is SignatureName.MetaVar -> {
            val constraint = when (val constraints = metaVarInfo.constraints[methodName.metaVar]) {
                null -> null
                is MetaVarConstraintOrPlaceHolder.Constraint -> constraints.constraint
                is MetaVarConstraintOrPlaceHolder.PlaceHolder -> {
                    semgrepRuleTrace.error("Placeholder: method name", Reason.NOT_IMPLEMENTED)
                    constraints.constraint
                }
            }

            if (constraint == null) return listOf(null to null)

            val conditionsWithConcreteNames = constraint.constraint.toSerializedConditionCubes(
                transformPositive = { c ->
                    when (c) {
                        is MetaVarConstraint.Concrete -> SerializedCondition.True to c.value
                        is MetaVarConstraint.RegExp -> SerializedCondition.MethodNameMatches(c.regex) to null
                    }
                },
                transformNegated = { c ->
                    when (c) {
                        is MetaVarConstraint.Concrete -> methodNameMatcherCondition(c.value) to null
                        is MetaVarConstraint.RegExp -> SerializedCondition.MethodNameMatches(c.regex) to null
                    }
                }
            )

            return conditionsWithConcreteNames.map { (cond, concrete) ->
                val concreteNames = concrete.filterNotNull()
                check(concreteNames.size <= 1) { "Multiple concrete names" }
                concreteNames.firstOrNull()?.let { SerializedNameMatcher.Simple(it) } to cond
            }
        }
    }
}

private fun methodNameMatcherCondition(methodNameConstraint: String): SerializedCondition {
    val methodName = methodNameConstraint.substringAfterLast('.')
    val className = methodNameConstraint.substringBeforeLast('.', "")

    val methodNameMatcher = SerializedCondition.MethodNameMatches(methodName)
    val classNameMatcher: SerializedCondition.ClassNameMatches? =
        className.takeIf { it.isNotEmpty() }?.let {
            SerializedCondition.ClassNameMatches(classNameMatcherFromConcreteString(it))
        }

    return SerializedCondition.and(listOfNotNull(methodNameMatcher, classNameMatcher))
}

private fun classNameMatcherFromConcreteString(name: String): SerializedNameMatcher {
    val parts = name.split(".")
    val packageName = parts.dropLast(1).joinToString(separator = ".")
    return SerializedNameMatcher.ClassPattern(
        SerializedNameMatcher.Simple(packageName),
        SerializedNameMatcher.Simple(parts.last())
    )
}

private fun TaintRuleGenerationCtx.evaluateEdgePredicateConstraint(
    signature: MethodSignature?,
    constraint: MethodConstraint?,
    negated: Boolean,
    conditions: MutableSet<SerializedCondition>,
    additionalFieldRules: MutableList<SerializedFieldRule>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
) {
    if (!negated) {
        evaluateMethodConstraints(
            signature,
            constraint,
            conditions,
            additionalFieldRules,
            semgrepRuleTrace
        )
    } else {
        val negatedConditions = hashSetOf<SerializedCondition>()
        evaluateMethodConstraints(
            signature,
            constraint,
            negatedConditions,
            additionalFieldRules,
            semgrepRuleTrace
        )
        conditions += SerializedCondition.not(SerializedCondition.and(negatedConditions.toList()))
    }
}

private fun TaintRuleGenerationCtx.evaluateMethodConstraints(
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

                is ClassConstraint.TypeConstraint -> {
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
            constraint,
            conditions,
            additionalFieldRules,
            semgrepRuleTrace
        )
    }
}

private fun TaintRuleGenerationCtx.evaluateMethodSignatureCondition(
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
            val methodNameRegex = "^${methodName.value}\$"
            cond += SerializedCondition.MethodNameMatches(methodNameRegex)
        }

        SerializedCondition.and(cond)
    }
}

private fun findMetaVarPosition(
    constraint: MethodConstraint?,
    varPositions: MutableMap<MetavarAtom, RegisterVarPosition>
) {
    if (constraint !is ParamConstraint) return
    findMetaVarPosition(constraint, varPositions)
}

private fun TaintRuleGenerationCtx.typeMatcher(
    typeName: TypeNamePattern,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace
): MetaVarConstraintFormula<SerializedNameMatcher>? {
    return when (typeName) {
        is TypeNamePattern.ClassName -> MetaVarConstraintFormula.Constraint(
            SerializedNameMatcher.ClassPattern(
                `package` = anyName(),
                `class` = SerializedNameMatcher.Simple(typeName.name)
            )
        )

        is TypeNamePattern.FullyQualified -> {
            MetaVarConstraintFormula.Constraint(
                SerializedNameMatcher.Simple(typeName.name)
            )
        }

        is TypeNamePattern.PrimitiveName -> {
            MetaVarConstraintFormula.Constraint(
                SerializedNameMatcher.Simple(typeName.name)
            )
        }

        is TypeNamePattern.ArrayType -> {
            typeMatcher(typeName.element, semgrepRuleTrace)?.transform { matcher ->
                SerializedNameMatcher.Array(matcher)
            }
        }

        TypeNamePattern.AnyType -> return null

        is TypeNamePattern.MetaVar -> {
            val constraints = metaVarInfo.constraints[typeName.metaVar]
            val constraint = when (constraints) {
                null -> null
                is MetaVarConstraintOrPlaceHolder.Constraint -> constraints.constraint.constraint
                is MetaVarConstraintOrPlaceHolder.PlaceHolder -> {
                    semgrepRuleTrace.error("Placeholder: type name", Reason.NOT_IMPLEMENTED)
                    constraints.constraint?.constraint
                }
            }

            if (constraint == null) return null

            constraint.transform { value ->
                // todo hack: here we assume that if name contains '.' then name is fqn
                when (value) {
                    is MetaVarConstraint.Concrete -> {
                        if (value.value.contains('.')) {
                            SerializedNameMatcher.Simple(value.value)
                        } else {
                            SerializedNameMatcher.ClassPattern(
                                `package` = anyName(),
                                `class` = SerializedNameMatcher.Simple(value.value)
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
                                    SerializedNameMatcher.ClassPattern(
                                        `package` = SerializedNameMatcher.Pattern(value.regex),
                                        `class` = anyName()
                                    )
                                } else {
                                    SerializedNameMatcher.Pattern(value.regex)
                                }
                            } else {
                                SerializedNameMatcher.ClassPattern(
                                    `package` = SerializedNameMatcher.Pattern(pkgPattern),
                                    `class` = SerializedNameMatcher.Pattern(clsPattern)
                                )
                            }
                        } else {
                            SerializedNameMatcher.ClassPattern(
                                `package` = anyName(),
                                `class` = SerializedNameMatcher.Pattern(value.regex)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun String.patternCanMatchDot(): Boolean =
    '.' in this || '-' in this // [A-Z]

private fun TaintRuleGenerationCtx.signatureModifierConstraint(
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
            AnnotationParamStringMatcher(v.paramName, v.value)
        )

        is SignatureModifierValue.StringPattern -> listOf(
            AnnotationParamPatternMatcher(v.paramName, v.pattern)
        )

        is SignatureModifierValue.MetaVar -> {
            val constraints = metaVarInfo.constraints[v.metaVar]
            val constraint = when (constraints) {
                null -> null
                is MetaVarConstraintOrPlaceHolder.Constraint -> constraints.constraint.constraint
                is MetaVarConstraintOrPlaceHolder.PlaceHolder -> {
                    semgrepRuleTrace.error("Placeholder: annotation", Reason.NOT_IMPLEMENTED)
                    constraints.constraint?.constraint
                }
            }

            if (constraint == null) {
                val anyValue = AnnotationParamPatternMatcher(v.paramName, ".*")
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
                        is MetaVarConstraint.Concrete -> AnnotationParamStringMatcher(v.paramName, c.value)
                        is MetaVarConstraint.RegExp -> AnnotationParamPatternMatcher(v.paramName, c.regex)
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

private fun TaintRuleGenerationCtx.evaluateParamConstraints(
    param: ParamConstraint,
    conditions: MutableSet<SerializedCondition>,
    additionalFieldRules: MutableList<SerializedFieldRule>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
) {
    val position = param.position.toSerializedPosition()
    conditions += evaluateParamCondition(position, param.condition, additionalFieldRules, semgrepRuleTrace)
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
    varPosition.positions.add(position)
}

private fun TaintRuleGenerationCtx.evaluateParamCondition(
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
                semgrepRuleTrace.error("metavar ${condition.metavar} constraint ignored", Reason.NOT_IMPLEMENTED)
            }

            return containsMarkWithAnyState(condition.metavar, position.base())
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

            val metaVar = MetavarAtom.createArtificial("__STATIC_FIELD_VALUE__${condition.fieldName}")
            val mark = prefix.metaVarState(metaVar, state = 0)

            val action = mark.mkAssignMark(PositionBase.Result.base())
            additionalFieldRules += SerializedFieldRule.SerializedStaticFieldSource(
                enclosingClassMatcher, condition.fieldName, condition = null, listOf(action)
            )

            return mark.mkContainsMark(position.base())
        }

        ParamCondition.AnyStringLiteral -> {
            return SerializedCondition.and(
                listOf(
                    SerializedCondition.IsType(StringTypeName, position),
                    SerializedCondition.IsConstant(position)
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
                    semgrepRuleTrace.error("Placeholder: string value", Reason.NOT_IMPLEMENTED)
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

private fun <T> List<T>.toSerializedOr(transformer: (T) -> SerializedCondition): SerializedCondition =
    serializedConditionOr(map(transformer))

private val StringTypeName = SerializedNameMatcher.Simple("java.lang.String")
