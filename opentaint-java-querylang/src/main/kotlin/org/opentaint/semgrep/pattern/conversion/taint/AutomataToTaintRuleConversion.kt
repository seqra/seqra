package org.opentaint.semgrep.pattern.conversion.taint

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.opentaint.dataflow.configuration.jvm.serialized.AnalysisEndSink
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationParamPatternMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationParamStringMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.Companion.mkFalse
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
import org.opentaint.dataflow.util.PersistentBitSet
import org.opentaint.dataflow.util.PersistentBitSet.Companion.emptyPersistentBitSet
import org.opentaint.dataflow.util.contains
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.toBitSet
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.OperationCancelation
import org.opentaint.semgrep.pattern.MetaVarConstraint
import org.opentaint.semgrep.pattern.MetaVarConstraintFormula
import org.opentaint.semgrep.pattern.MetaVarConstraints
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.RuleWithMetaVars
import org.opentaint.semgrep.pattern.SemgrepErrorEntry.Reason
import org.opentaint.semgrep.pattern.SemgrepMatchingRule
import org.opentaint.semgrep.pattern.SemgrepRule
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace
import org.opentaint.semgrep.pattern.SemgrepSinkTaintRequirement
import org.opentaint.semgrep.pattern.SemgrepTaintLabel
import org.opentaint.semgrep.pattern.SemgrepTaintRule
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.conversion.IsMetavar
import org.opentaint.semgrep.pattern.conversion.MetavarAtom
import org.opentaint.semgrep.pattern.conversion.ParamCondition
import org.opentaint.semgrep.pattern.conversion.ParamCondition.StringValueMetaVar
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifier
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifierValue
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureName
import org.opentaint.semgrep.pattern.conversion.SpecificBoolValue
import org.opentaint.semgrep.pattern.conversion.SpecificStringValue
import org.opentaint.semgrep.pattern.conversion.TypeNamePattern
import org.opentaint.semgrep.pattern.conversion.automata.AutomataBuilderCtx
import org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.semgrep.pattern.conversion.automata.ClassModifierConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodEnclosingClassName
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula.Cube
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.semgrep.pattern.conversion.automata.MethodModifierConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodName
import org.opentaint.semgrep.pattern.conversion.automata.MethodSignature
import org.opentaint.semgrep.pattern.conversion.automata.NumberOfArgsConstraint
import org.opentaint.semgrep.pattern.conversion.automata.ParamConstraint
import org.opentaint.semgrep.pattern.conversion.automata.Position
import org.opentaint.semgrep.pattern.conversion.automata.Predicate
import org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import org.opentaint.semgrep.pattern.conversion.automata.operations.brzozowskiAlgorithm
import org.opentaint.semgrep.pattern.conversion.generatedAnyValueGeneratorMethodName
import org.opentaint.semgrep.pattern.conversion.generatedReturnValueMethod
import org.opentaint.semgrep.pattern.conversion.generatedStringConcatMethodName
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.Edge
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeCondition
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeEffect
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.MethodPredicate
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.State
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.StateRegister
import org.opentaint.semgrep.pattern.transform
import java.util.BitSet
import java.util.IdentityHashMap
import kotlin.time.Duration.Companion.seconds

fun convertToTaintRules(
    rule: SemgrepRule<RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>>,
    ruleId: String,
    meta: SinkMetaData,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace
): TaintRuleFromSemgrep = when (rule) {
    is SemgrepMatchingRule -> RuleConversionCtx(ruleId, meta, semgrepRuleTrace).convertMatchingRuleToTaintRules(rule)
    is SemgrepTaintRule -> RuleConversionCtx(ruleId, meta, semgrepRuleTrace).convertTaintRuleToTaintRules(rule)
}

private class RuleConversionCtx(
    val ruleId: String,
    val meta: SinkMetaData,
    val semgrepRuleTrace: SemgrepRuleLoadStepTrace
)

private fun RuleConversionCtx.safeConvertToTaintRules(
    name: String,
    rule: RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>,
    convertToTaintRules: (RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>) -> List<SerializedItem>,
): List<SerializedItem>? =
    runCatching {
        try {
            convertToTaintRules(rule)
        } catch (_: LoopAssignVarsException) {
            val builderCtx = AutomataBuilderCtx(
                cancelation = OperationCancelation(automataCreationTimeout),
                formulaManager = rule.rule.formulaManager,
                metaVarInfo = rule.metaVarInfo
            )

            val minimized = rule.map {
                with (builderCtx) {
                    brzozowskiAlgorithm(it)
                }
            }
            convertToTaintRules(minimized)
        }
    }.onFailure { ex ->
        semgrepRuleTrace.error(
            "Failed to convert to taint rule for $name: ${ex.message}",
            Reason.ERROR,
        )
    }.getOrNull()

private fun RuleConversionCtx.convertMatchingRuleToTaintRules(
    rule: SemgrepMatchingRule<RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>>,
): TaintRuleFromSemgrep {
    if (rule.rules.isEmpty()) {
        error("No SemgrepRuleAutomatas received")
    }

    val ruleGroups = rule.rules.mapIndexedNotNull { idx, r ->
        val automataId = "$ruleId#$idx"

        val rules = safeConvertToTaintRules(automataId, r) { rule ->
            convertAutomataToTaintRules(rule.metaVarInfo, rule.rule, automataId)
        }

        rules?.let(TaintRuleFromSemgrep::TaintRuleGroup)
    }

    if (ruleGroups.isEmpty()) {
        error("Failed to generate any taintRuleGroup")
    }
    return TaintRuleFromSemgrep(ruleId, ruleGroups)
}

private fun RuleConversionCtx.convertTaintRuleToTaintRules(
    rule: SemgrepTaintRule<RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>>,
): TaintRuleFromSemgrep {
    val taintMarks = mutableSetOf<String>()
    val generatedRules = mutableListOf<SerializedItem>()

    fun taintMark(label: SemgrepTaintLabel?): String {
        val labelSuffix = label?.label?.let { "_$it" } ?: ""
        return "$ruleId#taint$labelSuffix"
    }

    for ((i, source) in rule.sources.withIndex()) {
        val taintMarkName = taintMark(source.label).also { taintMarks.add(it) }

        val requiresVarName = when (val r = source.requires) {
            null -> "dummy_unused_name"
            is SemgrepTaintLabel -> taintMark(r)
        }

        generatedRules += safeConvertToTaintRules("$ruleId: source #$i", source.pattern) { pattern ->
            val sourceCtx = convertTaintSourceRule(i, pattern, generateRequires = source.requires != null)

            sourceCtx.flatMap {
                val ctx = SinkRuleGenerationCtx(it.requirementVars, it.requirementStateId, requiresVarName, it.ctx)
                ctx.generateTaintSourceRules(it.stateVars, taintMarkName, semgrepRuleTrace)
            }
        }.orEmpty()
    }

    for ((i, sink) in rule.sinks.withIndex()) {
        val sinkRequiresMarks = when (sink.requires) {
            null -> taintMarks

            is SemgrepSinkTaintRequirement.Simple -> when (val r = sink.requires.requirement) {
                is SemgrepTaintLabel -> listOf(taintMark(r))
            }

            is SemgrepSinkTaintRequirement.MetaVarRequirement -> {
                semgrepRuleTrace.error("Rule $ruleId: sink requires ignored", Reason.NOT_IMPLEMENTED)
                taintMarks
            }
        }

        generatedRules += safeConvertToTaintRules("$ruleId: sink #$i", sink.pattern) { pattern ->
            val sinkContexts = convertTaintSinkRule(i, pattern)

            sinkContexts.flatMap { (ctx, stateVars, stateId) ->
                sinkRequiresMarks.flatMap { taintMarkName ->
                    val sinkCtx = SinkRuleGenerationCtx(stateVars, stateId, taintMarkName, ctx)
                    sinkCtx.generateTaintSinkRules(ruleId, meta, semgrepRuleTrace) { _, cond ->
                        if (cond is SerializedCondition.True) {
                            semgrepRuleTrace.error(
                                "Taint rule $ruleId match anything",
                                Reason.WARNING,
                            )
                            return@generateTaintSinkRules false
                        }

                        true
                    }
                }
            }
        }.orEmpty()
    }

    for ((i, pass) in rule.propagators.withIndex()) {
        generatedRules += safeConvertToTaintRules("$ruleId: pass #$i", pass.pattern) { pattern ->
            val fromVar = MetavarAtom.create(pass.from)
            val toVar = MetavarAtom.create(pass.to)

            val passCtx = generatePassRule(i, pattern, fromVar, toVar)
            passCtx.flatMap { (ctx, stateId) ->
                taintMarks.flatMap { taintMarkName ->
                    val sinkCtx = SinkRuleGenerationCtx(setOf(fromVar), stateId, taintMarkName, ctx)
                    sinkCtx.generateTaintPassRules(fromVar, toVar, taintMarkName, semgrepRuleTrace)
                }
            }
        }.orEmpty()
    }

    for ((i, sanitizer) in rule.sanitizers.withIndex()) {
        // todo: sanitizer by side effect
        // todo: sanitizer focus metavar
        generatedRules += safeConvertToTaintRules("$ruleId: sanitizer #$i", sanitizer.pattern) {
            val sanitizerCtx = convertTaintSourceRule(i, sanitizer.pattern, generateRequires = false)
            sanitizerCtx.flatMap {
                taintMarks.flatMap { taintMarkName ->
                    it.ctx.generateTaintSanitizerRules(taintMarkName, semgrepRuleTrace)
                }
            }
        }.orEmpty()
    }

    val ruleGroup = TaintRuleFromSemgrep.TaintRuleGroup(generatedRules)
    return TaintRuleFromSemgrep(ruleId, listOf(ruleGroup))
}

private fun RuleConversionCtx.generatePassRule(
    passIdx: Int,
    rule: RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>,
    fromMetaVar: MetavarAtom,
    toMetaVar: MetavarAtom
): List<Pair<TaintRuleGenerationCtx, Int>> {
    val automata = rule.rule

    val taintAutomatas = createAutomataWithEdgeElimination(
        automata.formulaManager, rule.metaVarInfo, automata.initialNode
    )

    return taintAutomatas.map { taintAutomata ->
        val initialStateId = taintAutomata.stateId(taintAutomata.initial)
        val initialRegister = StateRegister(mapOf(fromMetaVar to initialStateId))
        val newInitial = State(taintAutomata.initial.node, initialRegister)
        val taintAutomataWithState = taintAutomata.replaceInitialState(newInitial)

        val taintEdges = generateAutomataWithTaintEdges(
            taintAutomataWithState, rule.metaVarInfo,
            automataId = "$ruleId#pass_$passIdx", acceptStateVars = setOf(toMetaVar)
        )

        taintEdges to initialStateId
    }
}

// todo: check sink behaviour with multiple focus meta vars
private fun RuleConversionCtx.convertTaintSinkRule(
    sinkIdx: Int,
    rule: RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>
): List<Triple<TaintRuleGenerationCtx, Set<MetavarAtom>, Int>> {
    val automata = rule.rule

    val taintAutomatas = createAutomataWithEdgeElimination(
        automata.formulaManager, rule.metaVarInfo, automata.initialNode
    )

    return taintAutomatas.map { taintAutomata ->
        val (sinkAutomata, stateMetaVars) = ensureSinkStateVars(
            taintAutomata,
            rule.metaVarInfo.focusMetaVars.map { MetavarAtom.create(it) }.toSet()
        )

        val initialStateId = sinkAutomata.stateId(sinkAutomata.initial)
        val initialRegister = StateRegister(stateMetaVars.associateWith { initialStateId })
        val newInitial = State(sinkAutomata.initial.node, initialRegister)
        val sinkAutomataWithState = sinkAutomata.replaceInitialState(newInitial)

        val taintEdges = generateAutomataWithTaintEdges(
            sinkAutomataWithState, rule.metaVarInfo,
            automataId = "$ruleId#sink_$sinkIdx", acceptStateVars = emptySet()
        )

        Triple(taintEdges, stateMetaVars, initialStateId)
    }
}

private data class SourceRuleGenerationCtx(
    val ctx: TaintRuleGenerationCtx,
    val stateVars: Set<MetavarAtom>,
    val requirementVars: Set<MetavarAtom>,
    val requirementStateId: Int
)

private fun RuleConversionCtx.convertTaintSourceRule(
    sourceIdx: Int,
    rule: RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>,
    generateRequires: Boolean
): List<SourceRuleGenerationCtx> {
    val automata = rule.rule

    val taintAutomatas = createAutomataWithEdgeElimination(
        automata.formulaManager, rule.metaVarInfo, automata.initialNode
    )

    return taintAutomatas.map { taintAutomata ->
        val (rawSourceAutomata, stateMetaVars) = ensureSourceStateVars(
            taintAutomata,
            rule.metaVarInfo.focusMetaVars.map { MetavarAtom.create(it) }.toSet()
        )

        val (sourceAutomata, requirementVars, requirementStateId) = if (generateRequires) {
            val (sourceAutomataWithReq, requirementVars) = ensureSinkStateVars(rawSourceAutomata, emptySet())

            val initialStateId = sourceAutomataWithReq.stateId(sourceAutomataWithReq.initial)
            val initialRegister = StateRegister(requirementVars.associateWith { initialStateId })
            val newInitial = State(sourceAutomataWithReq.initial.node, initialRegister)
            val sourceAutomataWithState = sourceAutomataWithReq.replaceInitialState(newInitial)
            Triple(sourceAutomataWithState, requirementVars, initialStateId)
        } else {
            Triple(rawSourceAutomata, emptySet(), -1)
        }

        val taintEdges = generateAutomataWithTaintEdges(
            sourceAutomata, rule.metaVarInfo,
            automataId = "$ruleId#source_$sourceIdx", acceptStateVars = stateMetaVars
        )

        val finalAcceptEdges = taintEdges.finalEdges.filter { it.stateTo.node.accept }
        val assignedStateVars = finalAcceptEdges.flatMapTo(hashSetOf()) { it.stateTo.register.assignedVars.keys }
        assignedStateVars.retainAll(stateMetaVars)

        SourceRuleGenerationCtx(taintEdges, assignedStateVars, requirementVars, requirementStateId)
    }
}

private fun ensureSinkStateVars(
    automata: TaintRegisterStateAutomata,
    focusMetaVars: Set<MetavarAtom>
): Pair<TaintRegisterStateAutomata, Set<MetavarAtom>> {
    if (focusMetaVars.isNotEmpty()) return automata to focusMetaVars

    val freshVar = MetavarAtom.create("generated_sink_requirement")

    val newAutomata = TaintRegisterStateAutomataBuilder()
    val newInitialState = ensureSinkStateVars(freshVar, automata.initial, hashSetOf(), automata, newAutomata)

    check(newInitialState != null) { "unable to insert taint check" }

    val resultAutomata = newAutomata.build(automata.formulaManager, newInitialState)
    return resultAutomata to setOf(freshVar)
}

private class TaintRegisterStateAutomataBuilder {
    val successors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()
    val final = hashSetOf<State>()
    val nodeIndex = hashMapOf<AutomataNode, Int>()

    fun build(manager: MethodFormulaManager, initial: State) =
        TaintRegisterStateAutomata(manager, initial, final, successors, nodeIndex)
}

private fun ensureSinkStateVars(
    taintVar: MetavarAtom,
    state: State,
    processedStates: MutableSet<State>,
    current: TaintRegisterStateAutomata,
    newAutomata: TaintRegisterStateAutomataBuilder,
): State? {
    if (!processedStates.add(state)) return null

    if (state in current.final) {
        return null
    }

    val currentStateSucc = current.successors[state] ?: return null

    val argumentIndex = Position.ArgumentIndex.Any(paramClassifier = "tainted")
    val expandPositions = listOf(
        Position.Argument(argumentIndex), Position.Object
    )

    val newSucc = hashSetOf<Pair<Edge, State>>()
    for ((edge, dst) in currentStateSucc) {
        ensureSinkStateVars(taintVar, dst, processedStates.toMutableSet(), current, newAutomata)?.let { newDst ->
            newSucc.add(edge to newDst)
        }

        when (edge) {
            is Edge.MethodCall -> {
                val positivePredicate = edge.condition.findPositivePredicate()
                    ?: continue

                for (pos in expandPositions) {
                    val conditionVars = edge.condition.readMetaVar.toMutableMap()
                    val condition = ParamConstraint(pos, IsMetavar(taintVar))
                    val predicate = Predicate(positivePredicate.signature, condition)

                    conditionVars[taintVar] = listOf(MethodPredicate(predicate, negated = false))
                    val edgeCondition = EdgeCondition(conditionVars, edge.condition.other)

                    val modifiedEdge = Edge.MethodCall(edgeCondition, edge.effect)
                    val dstWithTaint = forkState(dst, current, hashMapOf(), newAutomata)

                    newSucc.add(modifiedEdge to dstWithTaint)
                }
            }

            Edge.AnalysisEnd,
            is Edge.MethodEnter -> continue
        }
    }

    newAutomata.successors[state] = newSucc
    newAutomata.nodeIndex[state.node] = newAutomata.nodeIndex.size

    return state
}

private fun forkState(
    state: State,
    current: TaintRegisterStateAutomata,
    forkedStates: MutableMap<State, State>,
    newAutomata: TaintRegisterStateAutomataBuilder,
): State {
    val forked = forkedStates[state]
    if (forked != null) return forked

    val newNode = AutomataNode()
    if (state.node.accept) {
        newNode.accept = true
    }

    newAutomata.nodeIndex[newNode] = newAutomata.nodeIndex.size

    val newState = State(newNode, state.register)
    forkedStates[state] = newState

    if (state in current.final) {
        newAutomata.final.add(newState)
    }

    val currentStateSucc = current.successors[state]
        ?: return newState

    val newSucc = hashSetOf<Pair<Edge, State>>()
    for ((edge, dst) in currentStateSucc) {
        val forkedDst = forkState(dst, current, forkedStates, newAutomata)
        newSucc.add(edge to forkedDst)
    }

    newAutomata.successors[newState] = newSucc
    return newState
}

private fun ensureSourceStateVars(
    automata: TaintRegisterStateAutomata,
    focusMetaVars: Set<MetavarAtom>
): Pair<TaintRegisterStateAutomata, Set<MetavarAtom>> {
    if (focusMetaVars.isNotEmpty()) return automata to focusMetaVars

    val freshVar = MetavarAtom.create("generated_source")
    val edgeReplacement = mutableListOf<EdgeReplacement>()

    val predecessors = automataPredecessors(automata)
    val acceptStates = automata.final.filter { it.node.accept }
    for (dstState in acceptStates) {
        for ((edge, srcState) in predecessors[dstState].orEmpty()) {
            when (edge) {
                is Edge.MethodCall -> {
                    val positivePredicate = edge.condition.findPositivePredicate() ?: continue
                    val effectVars = edge.effect.assignMetaVar.toMutableMap()

                    // todo: currently we taint only result, but semgrep taint all subexpr by default
                    val condition = ParamConstraint(Position.Result, IsMetavar(freshVar))
                    val predicate = Predicate(positivePredicate.signature, condition)
                    effectVars[freshVar] = listOf(MethodPredicate(predicate, negated = false))
                    val effect = EdgeEffect(effectVars)
                    val modifiedEdge = Edge.MethodCall(edge.condition, effect)

                    edgeReplacement += EdgeReplacement(srcState, dstState, edge, modifiedEdge)
                }

                Edge.AnalysisEnd,
                is Edge.MethodEnter -> continue
            }
        }
    }

    val resultAutomata = automata.replaceEdges(edgeReplacement)
    return resultAutomata to setOf(freshVar)
}

private data class EdgeReplacement(
    val stateFrom: State,
    val stateTo: State,
    val originalEdge: Edge,
    val newEdge: Edge
)

private fun TaintRegisterStateAutomata.replaceEdges(replacements: List<EdgeReplacement>): TaintRegisterStateAutomata {
    if (replacements.isEmpty()) return this

    val mutableSuccessors = successors.toMutableMap()
    for (replacement in replacements) {
        val currentSuccessors = mutableSuccessors[replacement.stateFrom] ?: continue
        val newSuccessors = currentSuccessors.toHashSet()
        newSuccessors.remove(replacement.originalEdge to replacement.stateTo)
        newSuccessors.add(replacement.newEdge to replacement.stateTo)
        mutableSuccessors[replacement.stateFrom] = newSuccessors
    }

    return TaintRegisterStateAutomata(
        formulaManager, initial, final, mutableSuccessors, nodeIndex
    )
}

private fun TaintRegisterStateAutomata.replaceInitialState(newInitial: State): TaintRegisterStateAutomata {
    val newFinal = final.toHashSet()
    if (newFinal.remove(initial)) {
        newFinal.add(newInitial)
    }

    val successors = hashMapOf<State, Set<Pair<Edge, State>>>()
    for ((state, stateSuccessors) in this.successors) {
        val newSuccessors = stateSuccessors.mapTo(hashSetOf()) { current ->
            if (current.second != initial) return@mapTo current

            current.first to newInitial
        }

        val newState = if (state != initial) state else newInitial
        successors[newState] = newSuccessors
    }

    return TaintRegisterStateAutomata(formulaManager, newInitial, newFinal, successors, nodeIndex)
}

private fun RuleConversionCtx.convertAutomataToTaintRules(
    metaVarInfo: ResolvedMetaVarInfo,
    automata: SemgrepRuleAutomata,
    automataId: String,
): List<SerializedItem> {
    val taintAutomatas = createAutomataWithEdgeElimination(
        automata.formulaManager, metaVarInfo, automata.initialNode
    )

    return taintAutomatas.flatMap { taintAutomata ->
        val ctx = generateAutomataWithTaintEdges(
            taintAutomata, metaVarInfo, automataId, acceptStateVars = emptySet()
        )

        ctx.generateTaintSinkRules(ruleId, meta, semgrepRuleTrace) { function, cond ->
            if (function.matchAnything() && cond is SerializedCondition.True) {
                semgrepRuleTrace.error(
                    "Rule $ruleId match anything",
                    Reason.WARNING,
                )
                return@generateTaintSinkRules false
            }

            true
        }
    }
}

private fun createAutomataWithEdgeElimination(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    initialNode: AutomataNode
): List<TaintRegisterStateAutomata> {
    val registerAutomata = createAutomata(formulaManager, metaVarInfo, initialNode)
    return registerAutomata.map { automata ->
        val anyValueGeneratorEdgeEliminator = edgeTypePreservingEdgeEliminator(::eliminateAnyValueGenerator)
        val automataWithoutGeneratedEdges = eliminateEdges(
            automata,
            anyValueGeneratorEdgeEliminator,
            ValueGeneratorCtx.EMPTY
        )

        val stringConcatEdgeEliminator = edgeTypePreservingEdgeEliminator(::eliminateStringConcat)
        eliminateEdges(
            automataWithoutGeneratedEdges,
            stringConcatEdgeEliminator,
            StringConcatCtx.EMPTY
        )
    }
}

private fun RuleConversionCtx.generateAutomataWithTaintEdges(
    automata: TaintRegisterStateAutomata,
    metaVarInfo: ResolvedMetaVarInfo,
    automataId: String,
    acceptStateVars: Set<MetavarAtom>
): TaintRuleGenerationCtx {
    val simulated = simulateAutomata(automata)
    val cleaned = removeUnreachabeStates(simulated)
    val liveAutomata = eliminateDeadVariables(cleaned, acceptStateVars)
    val cleanAutomata = cleanupAutomata(liveAutomata, metaVarInfo)
    val generatedEdges =  generateTaintEdges(cleanAutomata, metaVarInfo, automataId)
    return cleanupAutomata(generatedEdges)
}

data class TaintRegisterStateAutomata(
    val formulaManager: MethodFormulaManager,
    val initial: State,
    val final: Set<State>,
    val successors: Map<State, Set<Pair<Edge, State>>>,
    val nodeIndex: Map<AutomataNode, Int>
) {
    data class StateRegister(
        val assignedVars: Map<MetavarAtom, Int>,
    )

    data class State(
        val node: AutomataNode,
        val register: StateRegister
    )

    data class MethodPredicate(
        val predicate: Predicate,
        val negated: Boolean,
    )

    data class EdgeCondition(
        val readMetaVar: Map<MetavarAtom, List<MethodPredicate>>,
        val other: List<MethodPredicate>
    )

    data class EdgeEffect(
        val assignMetaVar: Map<MetavarAtom, List<MethodPredicate>>
    )

    sealed interface Edge {
        data class MethodCall(val condition: EdgeCondition, val effect: EdgeEffect) : Edge
        data class MethodEnter(val condition: EdgeCondition, val effect: EdgeEffect) : Edge
        data object AnalysisEnd : Edge
    }

    fun stateId(state: State): Int = nodeIndex[state.node] ?: error("Missing node")
}

data class TaintRuleEdge(
    val stateFrom: State,
    val stateTo: State,
    val edge: Edge,
    val checkGlobalState: Boolean,
)

sealed interface MetaVarConstraintOrPlaceHolder {
    data class Constraint(val constraint: MetaVarConstraints) : MetaVarConstraintOrPlaceHolder
    data class PlaceHolder(val constraint: MetaVarConstraints?) : MetaVarConstraintOrPlaceHolder
}

data class TaintRuleGenerationMetaVarInfo(
    val constraints: Map<String, MetaVarConstraintOrPlaceHolder>
)

open class TaintRuleGenerationCtx(
    val uniqueRuleId: String,
    val automata: TaintRegisterStateAutomata,
    val metaVarInfo: TaintRuleGenerationMetaVarInfo,
    val globalStateAssignStates: Set<State>,
    val edges: List<TaintRuleEdge>,
    val finalEdges: List<TaintRuleEdge>,
) {
    open fun valueMarkName(varName: MetavarAtom): String =
        "${uniqueRuleId}|${varName}"

    open fun stateMarkName(varName: MetavarAtom, varValue: Int): String =
        "${uniqueRuleId}|${varName}|$varValue"

    fun globalStateMarkName(state: State): String {
        val stateId = automata.stateId(state)
        return "${uniqueRuleId}__<STATE>__$stateId"
    }

    val stateVarPosition by lazy {
        PositionBaseWithModifiers.BaseOnly(
            PositionBase.ClassStatic("${uniqueRuleId}__<STATE>__")
        )
    }
}

private class SinkRuleGenerationCtx(
    val initialStateVars: Set<MetavarAtom>,
    val initialVarValue: Int,
    val taintMarkName: String,
    uniqueRuleId: String,
    automata: TaintRegisterStateAutomata,
    metaVarInfo: TaintRuleGenerationMetaVarInfo,
    globalStateAssignStates: Set<State>,
    edges: List<TaintRuleEdge>,
    finalEdges: List<TaintRuleEdge>
) : TaintRuleGenerationCtx(
    uniqueRuleId, automata, metaVarInfo,
    globalStateAssignStates, edges, finalEdges
) {
    constructor(
        initialStateVars: Set<MetavarAtom>, initialVarValue: Int, taintMarkName: String,
        ctx: TaintRuleGenerationCtx
    ) : this(
        initialStateVars, initialVarValue, taintMarkName,
        ctx.uniqueRuleId, ctx.automata, ctx.metaVarInfo,
        ctx.globalStateAssignStates, ctx.edges, ctx.finalEdges
    )

    override fun valueMarkName(varName: MetavarAtom): String {
        if (varName in initialStateVars) {
            return taintMarkName
        }
        return super.valueMarkName(varName)
    }

    override fun stateMarkName(varName: MetavarAtom, varValue: Int): String {
        if (varName in initialStateVars && varValue == initialVarValue) {
            return taintMarkName
        }
        return super.stateMarkName(varName, varValue)
    }
}

private fun TaintRegisterStateAutomata.allStates(): Set<State> {
    val states = hashSetOf<State>()
    states += initial
    states += final
    states += successors.keys
    return states
}

private val automataCreationTimeout = 2.seconds

private fun createAutomata(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    initialNode: AutomataNode
): List<TaintRegisterStateAutomata> {
    val cancelation = OperationCancelation(automataCreationTimeout)

    val result = TaintRegisterStateAutomataBuilder()

    fun nodeId(node: AutomataNode): Int = result.nodeIndex.getOrPut(node) { result.nodeIndex.size }

    val emptyRegister = StateRegister(emptyMap())
    val startState = State(initialNode, emptyRegister)
    val initialStates = mutableListOf(startState)

    val processedStates = hashSetOf<State>()
    val unprocessed = mutableListOf<Pair<State, Pair<State, Edge>?>>(startState to null)

    while (unprocessed.isNotEmpty()) {
        val (state, prevEdge) = unprocessed.removeLast()
        if (!processedStates.add(state)) continue

        // force eval
        nodeId(state.node)

        if (state.node.accept) {
            result.final.add(state)
            // note: no need transitions from final state
            continue
        }

        for ((edgeCondition, dstNode) in state.node.outEdges) {
            for (simplifiedEdge in simplifyEdgeCondition(formulaManager, metaVarInfo, cancelation, edgeCondition)) {
                val nextState = State(dstNode, emptyRegister)

                if (simplifiedEdge.isEpsilonTransition()) {
                    if (prevEdge != null) {
                        val (prevState, edge) = prevEdge
                        result.successors.getOrPut(prevState, ::hashSetOf).add(edge to nextState)
                    } else {
                        initialStates.add(nextState)
                    }

                    unprocessed.add(nextState to prevEdge)
                } else {
                    result.successors.getOrPut(state, ::hashSetOf).add(simplifiedEdge to nextState)
                    unprocessed.add(nextState to (state to simplifiedEdge))
                }
            }
        }
    }

    check(result.final.isNotEmpty()) { "Automata has no accept state" }

    if (initialStates.size > 1) {
        initialStates.removeAll { !acceptStateIsReachable(result, it) }
    }

    return initialStates.map {
        result.build(formulaManager, it)
    }
}

private fun acceptStateIsReachable(automata: TaintRegisterStateAutomataBuilder, initial: State): Boolean {
    val visited = hashSetOf<State>()
    val unprocessed = mutableListOf(initial)
    while (unprocessed.isNotEmpty()) {
        val state = unprocessed.removeLast()
        if (state.node.accept) return true

        if (!visited.add(state)) continue

        unprocessed.addAll(automata.successors[state]?.map { it.second }.orEmpty())
    }

    return false
}

private fun Edge.isEpsilonTransition(): Boolean = when (this) {
    Edge.AnalysisEnd -> false
    is Edge.MethodCall -> condition.isTrue() && effect.hasNoEffect()
    is Edge.MethodEnter -> condition.isTrue() && effect.hasNoEffect()
}

private fun EdgeCondition.isTrue(): Boolean = readMetaVar.isEmpty() && other.isEmpty()

private fun EdgeEffect.hasNoEffect(): Boolean = assignMetaVar.isEmpty()

data class SimulationState(
    val original: State,
    val state: State,
    val originalPath: PersistentMap<State, State>
)

private class LoopAssignVarsException : RuntimeException("Loop assign vars")

private fun simulateAutomata(automata: TaintRegisterStateAutomata): TaintRegisterStateAutomata {
    val initialSimulationState = SimulationState(
        automata.initial, automata.initial,
        persistentHashMapOf(automata.initial to automata.initial)
    )
    val unprocessed = mutableListOf(initialSimulationState)

    val finalStates = hashSetOf<State>()
    val successors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()

    while (unprocessed.isNotEmpty()) {
        val simulationState = unprocessed.removeLast()
        val state = simulationState.state

        if (simulationState.original in automata.final) {
            finalStates.add(state)
            continue
        }

        for ((simplifiedEdge, dstState) in automata.successors[simulationState.original].orEmpty()) {
            val loopStartState = simulationState.originalPath[dstState]
            if (loopStartState != null) {
                if (loopStartState.register == state.register) {
                    // loop has no assignments
                    continue
                }

                throw LoopAssignVarsException()
            }

            val dstStateId = automata.stateId(dstState)
            val updatedEdge = rewriteEdgeWrtComplexMetavars(simplifiedEdge, state.register)
            val dstStateRegister = simulateCondition(updatedEdge, dstStateId, state.register)

            val nextState = dstState.copy(register = dstStateRegister)
            successors.getOrPut(state, ::hashSetOf).add(updatedEdge to nextState)

            val nextPath = simulationState.originalPath.put(dstState, nextState)
            val nextSimulationState = SimulationState(dstState, nextState, nextPath)
            unprocessed.add(nextSimulationState)
        }
    }

    return TaintRegisterStateAutomata(automata.formulaManager, automata.initial, finalStates, successors, automata.nodeIndex)
}

private fun rewriteEdgeWrtComplexMetavars(edge: Edge, register: StateRegister): Edge {
    return when (edge) {
        Edge.AnalysisEnd -> edge
        is Edge.MethodCall -> rewriteEdgeWrtComplexMetavars(edge.effect, edge.condition, register) { effect, condition ->
            Edge.MethodCall(condition, effect)
        }
        is Edge.MethodEnter -> rewriteEdgeWrtComplexMetavars(edge.effect, edge.condition, register) { effect, condition ->
            Edge.MethodEnter(condition, effect)
        }
    }
}

private inline fun rewriteEdgeWrtComplexMetavars(
    effect: EdgeEffect,
    condition: EdgeCondition,
    register: StateRegister,
    rebuildEdge: (EdgeEffect, EdgeCondition) -> Edge
): Edge {
    val newReadMetavar = mutableMapOf<MetavarAtom, List<MethodPredicate>>()

    condition.readMetaVar.forEach { (metavar, preds) ->
        if (metavar.basics.size == 1 || register.assignedVars.containsKey(metavar)) {
            // Nothing to do
            newReadMetavar[metavar] = preds
            return@forEach
        }

        val basics = metavar.basics

        val inputMetavars = hashSetOf<MetavarAtom>()
        register.assignedVars.keys.forEach inner@{ inputMetavar ->
            val thisBasics = inputMetavar.basics
            if (inputMetavar.basics.intersect(metavar.basics).isEmpty()) {
                return@inner
            }

            if (!thisBasics.all { basics.contains(it) }) {
                error("Metavar $inputMetavar from register overlaps with metavar $metavar from condition")
            }

            if (inputMetavars.any { it.basics.intersect(thisBasics).isNotEmpty() }) {
                error("Register contains overlapping metavars")
            }

            inputMetavars.add(inputMetavar)
        }

        if (inputMetavars.isEmpty()) {
            // TODO: is this needed?
            newReadMetavar[metavar] = preds
            return@forEach
        }

        if (inputMetavars.isNotEmpty() && inputMetavars.sumOf { it.basics.size } != basics.size) {
            error("Can't create metavar $metavar from metavars in register")
        }

        inputMetavars.forEach { inputMetavar ->
            newReadMetavar[inputMetavar] = preds.map { pred ->
                pred.replaceMetavar {
                    check(it == metavar) { "Unexpected metavar" }
                    inputMetavar
                }
            }
        }
    }

    val newCondition = EdgeCondition(newReadMetavar, condition.other)
    return rebuildEdge(effect, newCondition)
}

private fun MethodPredicate.replaceMetavar(replace: (MetavarAtom) -> MetavarAtom): MethodPredicate {
    val constraint = predicate.constraint ?: return this
    val newConstraint = constraint.replaceMetavar(replace)

    return MethodPredicate(
        predicate = Predicate(
            signature = predicate.signature,
            constraint = newConstraint
        ),
        negated = negated
    )
}

private fun MethodConstraint.replaceMetavar(replace: (MetavarAtom) -> MetavarAtom): MethodConstraint {
    if (this !is ParamConstraint) {
        return this
    }

    val newCondition = when (condition) {
        is IsMetavar -> IsMetavar(replace(condition.metavar))
        is StringValueMetaVar -> StringValueMetaVar(replace(condition.metaVar))
        else -> return this
    }

    return ParamConstraint(position, newCondition)
}

private fun automataPredecessors(automata: TaintRegisterStateAutomata): Map<State, Set<Pair<Edge, State>>> {
    val predecessors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()
    for ((state, edges) in automata.successors) {
        for ((edge, edgeDst) in edges) {
            predecessors.getOrPut(edgeDst, ::hashSetOf).add(edge to state)
        }
    }
    return predecessors
}

private fun removeUnreachabeStates(
    automata: TaintRegisterStateAutomata
): TaintRegisterStateAutomata {
    val predecessors = automataPredecessors(automata)

    val reachableStates = hashSetOf<State>()
    val unprocessed = automata.final.toMutableList()
    while (unprocessed.isNotEmpty()) {
        val stateId = unprocessed.removeLast()
        if (!reachableStates.add(stateId)) continue

        val predStates = predecessors[stateId] ?: continue
        for ((_, predState) in predStates) {
            unprocessed.add(predState)
        }
    }

    check(automata.initial in reachableStates) {
        "Initial state is unreachable"
    }

    var cleanerStateReachable = false
    val cleanerState = State(AutomataNode(), StateRegister(emptyMap()))
    val reachableSuccessors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()

    unprocessed.add(automata.initial)
    while (unprocessed.isNotEmpty()) {
        val state = unprocessed.removeLast()
        if (reachableSuccessors.containsKey(state)) continue

        if (state !in reachableStates) continue

        val newSuccessors = hashSetOf<Pair<Edge, State>>()
        for ((edge, successor) in automata.successors[state].orEmpty()) {
            if (successor in reachableStates) {
                newSuccessors.add(edge to successor)
                unprocessed.add(successor)
                continue
            }

            cleanerStateReachable = true
            newSuccessors.add(edge to cleanerState)
        }
        reachableSuccessors[state] = newSuccessors
    }

    if (!cleanerStateReachable) {
        return TaintRegisterStateAutomata(automata.formulaManager, automata.initial, automata.final, reachableSuccessors, automata.nodeIndex)
    }

    val nodeIndex = automata.nodeIndex.toMutableMap()
    nodeIndex[cleanerState.node] = nodeIndex.size

    val finalNodes = automata.final + cleanerState
    return TaintRegisterStateAutomata(automata.formulaManager, automata.initial, finalNodes, reachableSuccessors, nodeIndex)
}

private fun eliminateDeadVariables(
    automata: TaintRegisterStateAutomata,
    acceptStateLiveVars: Set<MetavarAtom>
): TaintRegisterStateAutomata {
    // TODO: to we need to specially handle complex variables here?
    val predecessors = automataPredecessors(automata)

    val variableIdx = hashMapOf<MetavarAtom, Int>()
    val stateLiveVars = IdentityHashMap<State, PersistentBitSet>()

    val unprocessed = mutableListOf<Pair<State, PersistentBitSet>>()
    for (state in automata.final) {
        if (!state.node.accept) {
            unprocessed.add(state to emptyPersistentBitSet())
            continue
        }

        val liveVarIndices = acceptStateLiveVars.toBitSet {
            variableIdx.getOrPut(it) { variableIdx.size }
        }
        val liveVarSet = emptyPersistentBitSet().persistentAddAll(liveVarIndices)
        unprocessed.add(state to liveVarSet)
    }

    while (unprocessed.isNotEmpty()) {
        val (state, newLiveVars) = unprocessed.removeLast()

        val currentLiveVars = stateLiveVars[state]
        if (currentLiveVars == newLiveVars) continue

        val liveVars = currentLiveVars?.persistentAddAll(newLiveVars) ?: newLiveVars
        stateLiveVars[state] = liveVars

        for ((edge, predState) in predecessors[state].orEmpty()) {
            val readVariables = when (edge) {
                Edge.AnalysisEnd -> emptySet()
                is Edge.MethodCall -> edge.condition.readMetaVar.keys
                is Edge.MethodEnter -> edge.condition.readMetaVar.keys
            }

            val readVariableSet = readVariables.toBitSet {
                variableIdx.getOrPut(it) { variableIdx.size }
            }
            val dstLiveVars = liveVars.persistentAddAll(readVariableSet)
            unprocessed.add(predState to dstLiveVars)
        }
    }

    val stateMapping = hashMapOf<State, State>()
    for (state in automata.allStates()) {
        val liveVars = stateLiveVars[state] ?: continue
        val liveRegisterValues = state.register.assignedVars.filterKeys {
            val idx = variableIdx[it] ?: return@filterKeys false
            idx in liveVars
        }
        if (liveRegisterValues == state.register.assignedVars) continue

        val register = StateRegister(liveRegisterValues)
        stateMapping[state] = State(state.node, register)
    }

    if (stateMapping.isEmpty()) return automata

    val successors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()
    for ((state, stateSuccessors) in automata.successors) {
        val mappedSuccessors = stateSuccessors.mapTo(hashSetOf()) { (edge, s) ->
            edge to (stateMapping[s] ?: s)
        }
        val mappedState = stateMapping[state] ?: state
        successors[mappedState] = mappedSuccessors
    }

    return TaintRegisterStateAutomata(
        automata.formulaManager,
        initial = stateMapping[automata.initial] ?: automata.initial,
        final = automata.final.mapTo(hashSetOf()) { stateMapping[it] ?: it },
        successors = successors,
        nodeIndex = automata.nodeIndex
    )
}

private fun cleanupAutomata(automata: TaintRegisterStateAutomata, metaVarInfo: ResolvedMetaVarInfo): TaintRegisterStateAutomata {
    val result = tryRemoveEndEdge(automata)
    return tryRemoveDummyMethodEntry(result, metaVarInfo)
}

private fun tryRemoveEndEdge(automata: TaintRegisterStateAutomata): TaintRegisterStateAutomata {
    val predecessors = automataPredecessors(automata)
    val finalReplacement = mutableListOf<Pair<State, State>>()

    for (finalState in automata.final) {
        val preFinalEdges = predecessors[finalState] ?: continue
        if (preFinalEdges.size != 1) continue

        val (edge, predecessor) = preFinalEdges.single()
        if (edge !is Edge.AnalysisEnd) continue

        if (automata.successors[predecessor].orEmpty().size != 1) continue

        finalReplacement.add(finalState to predecessor)
    }

    if (finalReplacement.isEmpty()) return automata

    val successors = automata.successors.toMutableMap()
    val final = automata.final.toHashSet()

    for ((oldState, newState) in finalReplacement) {
        successors.remove(oldState)
        successors[newState] = emptySet()

        final.remove(oldState)

        if (oldState.node.accept) {
            newState.node.accept = true
        }
        final.add(newState)
    }

    return TaintRegisterStateAutomata(
        automata.formulaManager,
        automata.initial,
        final, successors,
        automata.nodeIndex
    )
}

private fun tryRemoveDummyMethodEntry(
    automata: TaintRegisterStateAutomata,
    metaVarInfo: ResolvedMetaVarInfo,
): TaintRegisterStateAutomata {
    val initialSuccessors = automata.successors[automata.initial].orEmpty()
    val dummyMethodEnters = mutableListOf<Pair<Edge.MethodEnter, State>>()
    for ((edge, state) in initialSuccessors) {
        if (edge !is Edge.MethodEnter) continue
        if (edge.effect.assignMetaVar.isNotEmpty()) continue
        if (!edge.condition.isDummyCondition(metaVarInfo)) continue

        dummyMethodEnters.add(edge to state)
    }

    if (dummyMethodEnters.isEmpty()) return automata

    val mutableSuccessors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()
    automata.successors.forEach { (s, edges) -> mutableSuccessors[s] = edges.toMutableSet() }

    val initialSucc = mutableSuccessors[automata.initial]!!

    for ((edge, state) in dummyMethodEnters) {
        val nextEdges = mutableSuccessors[state]
        initialSucc.remove(edge to state)
        nextEdges?.forEach { (e, s) ->
            initialSucc.add(e to s)
        }

        // todo: may be incorrect
        mutableSuccessors.remove(state)
    }

    return TaintRegisterStateAutomata(
        automata.formulaManager, automata.initial, automata.final,
        mutableSuccessors, automata.nodeIndex
    )
}

private fun EdgeCondition.isDummyCondition(metaVarInfo: ResolvedMetaVarInfo): Boolean {
    for (cond in other) {
        if (cond.predicate.constraint != null) return false
        val sig = cond.predicate.signature

        when (val mn = sig.methodName.name) {
            is SignatureName.Concrete -> return false
            SignatureName.AnyName -> {}
            is SignatureName.MetaVar -> {
                if (metaVarInfo.metaVarConstraints[mn.metaVar] != null) {
                    return false
                }
            }
        }

        when (val cn = sig.enclosingClassName.name) {
            TypeNamePattern.AnyType -> {}
            is TypeNamePattern.MetaVar -> {
                if (metaVarInfo.metaVarConstraints[cn.metaVar] != null) {
                    return false
                }
            }
            is TypeNamePattern.ClassName,
            is TypeNamePattern.FullyQualified,
            is TypeNamePattern.PrimitiveName -> return false
        }
    }

    return true
}

private fun cleanupAutomata(automata: TaintRuleGenerationCtx): TaintRuleGenerationCtx {
    return tryRemoveEndEdge(automata)
}

private fun tryRemoveEndEdge(automata: TaintRuleGenerationCtx): TaintRuleGenerationCtx {
    if (automata.finalEdges.size > 1) return automata

    val finalEdge = automata.finalEdges.singleOrNull() ?: return automata

    if (finalEdge.edge !is Edge.AnalysisEnd) return automata

    val preFinalEdges = automata.edges.filterTo(hashSetOf()) { it.stateTo == finalEdge.stateFrom }
    if (preFinalEdges.isEmpty()) return automata

    preFinalEdges.forEach {
        it.stateTo.node.accept = finalEdge.stateTo.node.accept
    }

    return TaintRuleGenerationCtx(
        automata.uniqueRuleId, automata.automata, automata.metaVarInfo,
        automata.globalStateAssignStates.minus(finalEdge.stateFrom),
        automata.edges.minus(preFinalEdges),
        preFinalEdges.toList()
    )
}

private data class ValueGeneratorCtx(
    val valueConstraint: Map<MetavarAtom, List<ParamCondition.Atom>>
) {
    companion object {
        val EMPTY: ValueGeneratorCtx = ValueGeneratorCtx(emptyMap())
    }
}

private fun <CtxT> eliminateEdges(automata: TaintRegisterStateAutomata, edgeEliminator: EdgeEliminator<CtxT>, initialCtx: CtxT): TaintRegisterStateAutomata {
    val successors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()
    val finalStates = automata.final.toHashSet()
    val removedStates = hashSetOf<State>()

    val unprocessed = mutableListOf(automata.initial to initialCtx)
    val visited = hashSetOf<Pair<State, CtxT>>()
    while (unprocessed.isNotEmpty()) {
        val state = unprocessed.removeLast()
        if (!visited.add(state)) continue

        val stateSuccessors = successors.getOrPut(state.first, ::hashSetOf)
        eliminateEdgesForOneState(
            state.first, state.second, automata.successors, finalStates, removedStates,
            stateSuccessors, unprocessed, edgeEliminator
        )
    }

    finalStates.removeAll(removedStates)
    removedStates.forEach { successors.remove(it) }
    finalStates.forEach { successors.remove(it) }

    return TaintRegisterStateAutomata(
        automata.formulaManager, automata.initial, finalStates, successors, automata.nodeIndex
    )
}

private fun <CtxT> eliminateEdgesForOneState(
    state: State,
    ctx: CtxT,
    successors: Map<State, Set<Pair<Edge, State>>>,
    finalStates: MutableSet<State>,
    removedStates: MutableSet<State>,
    resultStateSuccessors: MutableSet<Pair<Edge, State>>,
    unprocessed: MutableList<Pair<State, CtxT>>,
    edgeEliminator: EdgeEliminator<CtxT>
) {
    for ((edge, nextState) in successors[state].orEmpty()) {
        val elimResult = edgeEliminator.eliminateEdge(edge, ctx)
        when (elimResult) {
            EdgeEliminationResult.Unchanged -> {
                resultStateSuccessors.add(edge to nextState)
                unprocessed.add(nextState to ctx)
                continue
            }

            is EdgeEliminationResult.Replace -> {
                resultStateSuccessors.add(elimResult.newEdge to nextState)
                unprocessed.add(nextState to elimResult.ctx)
                continue
            }

            is EdgeEliminationResult.Eliminate -> {
                if (nextState in finalStates) {
                    val nextSuccessors = successors[nextState].orEmpty()
                    check(nextSuccessors.isEmpty())

                    removedStates.add(nextState)
                    finalStates.add(state)

                    if (nextState.node.accept) {
                        state.node.accept = true
                    }
                }

                if (nextState == state) continue

                eliminateEdgesForOneState(
                    nextState, elimResult.ctx, successors, finalStates, removedStates,
                    resultStateSuccessors, unprocessed, edgeEliminator
                )
            }
        }
    }
}

private fun interface EdgeEliminator<CtxT> {
    fun eliminateEdge(edge: Edge, ctx: CtxT): EdgeEliminationResult<CtxT>
}

private sealed interface EdgeEliminationResult<out CtxT> {
    data object Unchanged : EdgeEliminationResult<Nothing>
    data class Replace<CtxT>(val newEdge: Edge, val ctx: CtxT) : EdgeEliminationResult<CtxT>
    data class Eliminate<CtxT>(val ctx: CtxT) : EdgeEliminationResult<CtxT>
}

private fun <CtxT> edgeTypePreservingEdgeEliminator(
    eliminateEdge: (EdgeEffect, EdgeCondition, CtxT, (EdgeEffect, EdgeCondition) -> Edge) -> EdgeEliminationResult<CtxT>
): EdgeEliminator<CtxT> = EdgeEliminator { edge, ctx ->
    when (edge) {
        Edge.AnalysisEnd -> EdgeEliminationResult.Unchanged
        is Edge.MethodCall -> eliminateEdge(edge.effect, edge.condition, ctx) { effect, cond ->
            Edge.MethodCall(cond, effect)
        }

        is Edge.MethodEnter -> eliminateEdge(edge.effect, edge.condition, ctx) { effect, cond ->
            Edge.MethodEnter(cond, effect)
        }
    }
}

private fun eliminateAnyValueGenerator(
    effect: EdgeEffect,
    condition: EdgeCondition,
    ctx: ValueGeneratorCtx,
    rebuildEdge: (EdgeEffect, EdgeCondition) -> Edge,
): EdgeEliminationResult<ValueGeneratorCtx> {
    if (effect.anyValueGeneratorUsed()) {
        val metaVar = effect.assignMetaVar.keys.singleOrNull()
            ?: error("Value gen with multiple mata vars")

        val metaVarPred = effect.assignMetaVar.getValue(metaVar).first()
        check((metaVarPred.predicate.constraint as ParamConstraint).position is Position.Result) {
            "Unexpected constraint: $metaVarPred"
        }

        check(condition.readMetaVar.keys.all { it == metaVar }) {
            "Unexpected condition: $condition"
        }

        val metaVarConstraints = mutableListOf<ParamCondition.Atom>()
        for (constraint in condition.other) {
            when (val c = constraint.predicate.constraint) {
                is NumberOfArgsConstraint -> continue

                is ParamConstraint -> {
                    if (c.position !is Position.Result) {
                        error("Unexpected constraint: $c")
                    }

                    if (c.condition is IsMetavar) {
                        error("Unexpected condition: $c")
                    }

                    metaVarConstraints.add(c.condition)
                }

                null -> TODO("any value generator without constraints")
                is ClassModifierConstraint,
                is MethodModifierConstraint -> error("Unexpected any value generator constraint")
            }
        }

        val nextCtx = ValueGeneratorCtx(ctx.valueConstraint + (metaVar to metaVarConstraints))
        return EdgeEliminationResult.Eliminate(nextCtx)
    }

    val valueGenEffect = hashMapOf<MetavarAtom, MutableList<MethodPredicate>>()
    for ((metaVar, preds) in effect.assignMetaVar) {
        for (pred in preds) {
            if (pred.anyValueGeneratorUsed()) {
                valueGenEffect.getOrPut(metaVar, ::mutableListOf).add(pred)
            }
        }
    }

    var resultCondition = condition
    val resultConstraint = ctx.valueConstraint.toMutableMap()
    val constraintIter = resultConstraint.iterator()
    while (constraintIter.hasNext()) {
        val (metaVar, constraint) = constraintIter.next()

        val metaVarEffect = effect.assignMetaVar[metaVar] ?: continue

        val readMetaVar = resultCondition.readMetaVar - metaVar
        val other = resultCondition.other.toMutableList()

        for (mp in metaVarEffect) {
            val paramConstraint = mp.predicate.constraint as? ParamConstraint ?: continue
            check(paramConstraint.condition is IsMetavar && paramConstraint.condition.metavar == metaVar)
            for (atom in constraint) {
                val newParamConstraint = paramConstraint.copy(condition = atom)
                val newPredicate = mp.predicate.copy(constraint = newParamConstraint)
                other += MethodPredicate(newPredicate, negated = false)
            }
        }

        resultCondition = EdgeCondition(readMetaVar, other)
        constraintIter.remove()
    }

    if (resultCondition === condition) return EdgeEliminationResult.Unchanged

    val newEdge = rebuildEdge(effect, resultCondition)
    return EdgeEliminationResult.Replace(newEdge, ValueGeneratorCtx(resultConstraint))
}

private fun EdgeEffect.anyValueGeneratorUsed(): Boolean =
    assignMetaVar.values.any { preds -> preds.any { it.anyValueGeneratorUsed() } }

private fun MethodPredicate.anyValueGeneratorUsed(): Boolean =
    predicate.signature.isGeneratedAnyValueGenerator()

private fun MethodSignature.isGeneratedAnyValueGenerator(): Boolean {
    val name = methodName.name
    if (name !is SignatureName.Concrete) return false
    return name.name == generatedAnyValueGeneratorMethodName
}

private data class StringConcatCtx(
    val metavarMapping: Map<MetavarAtom, Set<MetavarAtom>>
) {
    fun transform(condition: EdgeCondition): EdgeCondition {
        return EdgeCondition(
            transform(condition.readMetaVar),
            condition.other.flatMap(::transform)
        )
    }

    fun transform(effect: EdgeEffect): EdgeEffect {
        return EdgeEffect(transform(effect.assignMetaVar))
    }

    private fun transform(preds: Map<MetavarAtom, List<MethodPredicate>>): Map<MetavarAtom, List<MethodPredicate>> {
        val result = hashMapOf<MetavarAtom, MutableList<MethodPredicate>>()
        preds.forEach { (prevMetavar, prevPreds) ->
            val newMetavars = metavarMapping.getOrElse(prevMetavar) { setOf(prevMetavar) }

            newMetavars.forEach { newMetavar ->
                // Need to concretize context for `prevMetavar`
                val newCtx = StringConcatCtx(metavarMapping + (prevMetavar to setOf(newMetavar)))
                val newPreds = prevPreds.flatMap(newCtx::transform)

                result.getOrPut(newMetavar) {
                    mutableListOf()
                }.addAll(newPreds)
            }
        }
        return result
    }

    private fun transform(predicate: MethodPredicate): List<MethodPredicate> {
        return transform(predicate.predicate).map { newPredicate ->
            MethodPredicate(newPredicate, predicate.negated)
        }
    }

    private fun transform(predicate: Predicate): List<Predicate> {
        if (predicate.signature.isGeneratedStringConcat()) {
            // Replacing with String.concat()
            val newConstraints = predicate.constraint?.let { constraint ->
                transform(constraint) {
                    if (it is Position.Argument) {
                        val index = it.index
                        check(index is Position.ArgumentIndex.Concrete) { "Expected concrete argument index" }
                        check(index.idx in 0 until 2) { "Invalid index for string concat" }
                        if (index.idx == 0) {
                            Position.Object
                        } else {
                            Position.Argument(
                                Position.ArgumentIndex.Concrete(0)
                            )
                        }
                    } else {
                        it
                    }
                }
            }

            return (newConstraints ?: listOf(null)).map { newConstraint ->
                Predicate(stringConcatMethodSignature, newConstraint)
            }
        }

        val newConstraints = predicate.constraint?.let { constraint ->
            transform(constraint) { it }
        }
        return (newConstraints ?: listOf(null)).map { newConstraint ->
            Predicate(predicate.signature, newConstraint)
        }
    }

    private fun transform(
        constraint: MethodConstraint,
        positionTransform: (Position) -> Position
    ): List<MethodConstraint> {
        if (constraint !is ParamConstraint) {
            return listOf(constraint)
        }

        val newPosition = positionTransform(constraint.position)
        val newConditions = transform(constraint.condition)

        return newConditions.map { newCondition ->
            ParamConstraint(newPosition, newCondition)
        }
    }

    private fun transform(condition: ParamCondition.Atom): List<ParamCondition.Atom> {
        return when (condition) {
            is IsMetavar -> {
                val newMetavars = metavarMapping[condition.metavar] ?: return listOf(condition)
                val modified = newMetavars.map(::IsMetavar)

                if (condition.metavar !in newMetavars || newMetavars.size > 1) {
                    return modified + ParamCondition.TypeIs(TypeNamePattern.FullyQualified("java.lang.String"))
                } else {
                    return modified
                }
            }

            is StringValueMetaVar -> {
                val newMetavars = metavarMapping[condition.metaVar] ?: return listOf(condition)
                return newMetavars.map(::StringValueMetaVar)
            }

            else -> listOf(condition)
        }
    }

    companion object {
        val EMPTY: StringConcatCtx = StringConcatCtx(emptyMap())

        val stringConcatMethodSignature by lazy {
            MethodSignature(
                MethodName(SignatureName.Concrete("concat")),
                MethodEnclosingClassName(TypeNamePattern.FullyQualified("java.lang.String"))
            )
        }
    }
}

private fun eliminateStringConcat(
    effect: EdgeEffect,
    condition: EdgeCondition,
    ctx: StringConcatCtx,
    rebuildEdge: (EdgeEffect, EdgeCondition) -> Edge,
): EdgeEliminationResult<StringConcatCtx> {
    // TODO: rollback renaming of metavar when necessary (?)
    val generatedByConcatHelperMetavars = effect.assignMetaVar.mapNotNull { (metavar, preds) ->
        val isResultOfConcatHelper = preds.any {
            val predCondition = it.asConditionOnStringConcat<Position.Result>()
                ?: return@any false

            check(predCondition == IsMetavar(metavar)) { "Unexpected condition" }
            !it.negated
        }

        metavar.takeIf { isResultOfConcatHelper }
    }.toSet()

    if (generatedByConcatHelperMetavars.isEmpty()) {
        return ctx.transformEdge(effect, condition, rebuildEdge)
    }

    val metavarArguments = condition.readMetaVar.flatMap { (metavar, preds) ->
        val isArgumentOfConcatHelper = preds.any {
            val predCondition = it.asConditionOnStringConcat<Position.Argument>()
                ?: return@any false

            check(predCondition == IsMetavar(metavar)) { "Unexpected condition" }
            !it.negated
        }

        if (isArgumentOfConcatHelper) {
            ctx.metavarMapping.getOrElse(metavar) { setOf(metavar) }
        } else {
            emptyList()
        }
    }.toSet()

    val otherArguments = condition.other.mapNotNull {
        it.asConditionOnStringConcat<Position.Argument>()
    }

    if (otherArguments.isEmpty() || otherArguments.singleOrNull() == ParamCondition.AnyStringLiteral) {
        val newCtx = if (metavarArguments.size == 1 && metavarArguments == generatedByConcatHelperMetavars) {
            ctx
        } else {
            StringConcatCtx(
                metavarMapping = ctx.metavarMapping + generatedByConcatHelperMetavars.associateWith { metavarArguments }
            )
        }
        return EdgeEliminationResult.Eliminate(newCtx)
    }
    return ctx.transformEdge(effect, condition, rebuildEdge)
}

private fun StringConcatCtx.transformEdge(
    effect: EdgeEffect,
    condition: EdgeCondition,
    rebuildEdge: (EdgeEffect, EdgeCondition) -> Edge
): EdgeEliminationResult<StringConcatCtx> {
    val newEffect = transform(effect)
    val newCondition = transform(condition)

    return if (effect == newEffect && condition == newCondition) {
        EdgeEliminationResult.Unchanged
    } else {
        val newEdge = rebuildEdge(newEffect, newCondition)
        EdgeEliminationResult.Replace(newEdge, this)
    }
}

private inline fun <reified T : Position> MethodPredicate.asConditionOnStringConcat(): ParamCondition.Atom? {
    if (!predicate.signature.isGeneratedStringConcat()) {
        return null
    }

    val constraint = predicate.constraint as? ParamConstraint ?: return null

    if (constraint.position !is T) {
        return null
    }

    return constraint.condition
}

private fun MethodSignature.isGeneratedStringConcat(): Boolean {
    val name = methodName.name
    if (name !is SignatureName.Concrete) return false
    return name.name == generatedStringConcatMethodName
}

private fun RuleConversionCtx.generateTaintEdges(
    automata: TaintRegisterStateAutomata,
    metaVarInfo: ResolvedMetaVarInfo,
    uniqueRuleId: String
): TaintRuleGenerationCtx {
    val globalStateAssignStates = hashSetOf<State>()
    val taintRuleEdges = mutableListOf<TaintRuleEdge>()
    val finalEdges = mutableListOf<TaintRuleEdge>()

    val predecessors = automataPredecessors(automata)

    val outDegree: MutableMap<State, Int> = automata.successors.mapValues { it.value.size }.toMutableMap()

    val unprocessed = ArrayDeque<State>()
    unprocessed.addAll(automata.final)

    while (unprocessed.isNotEmpty()) {
        val dstState = unprocessed.removeFirst()

        val isFinal = dstState in automata.final

        for ((edge, state) in predecessors[dstState].orEmpty()) {
            outDegree.compute(state) { _, prevDeg ->
                check(prevDeg != null && prevDeg > 0) { "Unexpected degree $prevDeg for state" }

                (prevDeg - 1).also {
                    if (it == 0) {
                        check(state !in automata.final) { "Unexpected final state as predecessor" }
                        unprocessed.add(state)
                    }
                }
            }

            val stateId = automata.stateId(state)
            val stateVars = state.register.assignedVars.filter { it.value == stateId }

            val globalVarRequired = when {
                state == automata.initial -> false
                edge is Edge.AnalysisEnd -> true
                stateVars.isEmpty() -> true
                else -> {
                    val writeVars = when (edge) {
                        Edge.AnalysisEnd -> emptySet()
                        is Edge.MethodCall -> edge.effect.assignMetaVar.keys
                        is Edge.MethodEnter -> edge.effect.assignMetaVar.keys
                    }
                    stateVars.all { it.key !in writeVars }
                }
            }

            if (isFinal) {
                if (dstState.node.accept || state.register.assignedVars.isNotEmpty()) {
                    if (globalVarRequired) {
                        globalStateAssignStates.add(state)
                    }

                    val taintEdge = edge.ensurePositiveCondition(this)
                        ?: continue

                    finalEdges += TaintRuleEdge(state, dstState, taintEdge, globalVarRequired)
                }

                continue
            }

            val edgeRequired = state.register != dstState.register
                    || (dstState in globalStateAssignStates && dstState != state && edge.canAssignStateVar())

            if (!edgeRequired) continue

            if (globalVarRequired) {
                globalStateAssignStates.add(state)
            }

            val taintEdge = edge.ensurePositiveCondition(this)
                ?: continue

            taintRuleEdges += TaintRuleEdge(state, dstState, taintEdge, globalVarRequired)
        }
    }

    check(outDegree.all { it.value == 0 }) { "Some states were not visited" }

    val initialStateWithGlobalAssign = hashSetOf<State>()
    for (state in globalStateAssignStates) {
        if (taintRuleEdges.any { it.stateTo == state }) continue
        if (finalEdges.any { it.stateTo == state }) continue

        initialStateWithGlobalAssign.add(state)
    }

    if (initialStateWithGlobalAssign.isNotEmpty()) {
        globalStateAssignStates.removeAll(initialStateWithGlobalAssign)

        for ((i, edge) in taintRuleEdges.withIndex()) {
            if (edge.stateFrom in initialStateWithGlobalAssign) {
                taintRuleEdges[i] = edge.copy(checkGlobalState = false)
            }
        }

        for ((i, edge) in finalEdges.withIndex()) {
            if (edge.stateFrom in initialStateWithGlobalAssign) {
                finalEdges[i] = edge.copy(checkGlobalState = false)
            }
        }
    }

    val metVarConstraints = hashMapOf<String, MetaVarConstraintOrPlaceHolder>()

    val placeHolders = computePlaceHolders(taintRuleEdges, finalEdges)
    placeHolders.placeHolderRequiredMetaVars.forEach {
        metVarConstraints[it] = MetaVarConstraintOrPlaceHolder.PlaceHolder(metaVarInfo.metaVarConstraints[it])
    }

    metaVarInfo.metaVarConstraints.forEach { (mv, c) ->
        if (mv !in metVarConstraints) {
            metVarConstraints[mv] = MetaVarConstraintOrPlaceHolder.Constraint(c)
        }
    }


    return TaintRuleGenerationCtx(
        uniqueRuleId, automata, TaintRuleGenerationMetaVarInfo(metVarConstraints),
        globalStateAssignStates, taintRuleEdges, finalEdges
    )
}

private class MetaVarCtx {
    val metaVarIdx = hashMapOf<String, Int>()
    val metaVars = mutableListOf<String>()

    fun String.idx() = metaVarIdx.getOrPut(this) {
        metaVars.add(this)
        metaVarIdx.size
    }
}

private data class MetaVarPlaceHolders(
    val placeHolderRequiredMetaVars: Set<String>,
)

private fun computePlaceHolders(
    taintRuleEdges: List<TaintRuleEdge>,
    finalEdges: List<TaintRuleEdge>
): MetaVarPlaceHolders {
    val predecessors = hashMapOf<State, MutableList<TaintRuleEdge>>()
    taintRuleEdges.forEach { predecessors.getOrPut(it.stateTo, ::mutableListOf).add(it) }
    finalEdges.forEach { predecessors.getOrPut(it.stateTo, ::mutableListOf).add(it) }

    val metaVarCtx = MetaVarCtx()

    val resultPlaceHolders = BitSet()
    val unprocessed = mutableListOf<Pair<State, PersistentBitSet>>()
    val visited = hashSetOf<Pair<State, PersistentBitSet>>()
    finalEdges.mapTo(unprocessed) { it.stateTo to emptyPersistentBitSet() }

    while (unprocessed.isNotEmpty()) {
        val entry = unprocessed.removeLast()
        if (!visited.add(entry)) continue

        val (state, statePlaceholders) = entry

        for (edge in predecessors[state].orEmpty()) {
            val edgeMetaVars = metaVarCtx.signatureMetaVars(edge.edge)

            val nextMetaVars = statePlaceholders.persistentAddAll(edgeMetaVars)

            // metavar has multiple usages
            edgeMetaVars.and(statePlaceholders)
            resultPlaceHolders.or(edgeMetaVars)

            unprocessed.add(edge.stateFrom to nextMetaVars)
        }
    }

    if (resultPlaceHolders.isEmpty) {
        return MetaVarPlaceHolders(emptySet())
    }

    val placeHolders = hashSetOf<String>()
    resultPlaceHolders.forEach { placeHolders.add(metaVarCtx.metaVars[it]) }
    return MetaVarPlaceHolders(placeHolders)
}

private fun MetaVarCtx.signatureMetaVars(edge: Edge): BitSet = when (edge) {
    Edge.AnalysisEnd -> BitSet()

    is Edge.MethodCall -> {
        val metaVars = BitSet()
        edgeConditionSignatureMetaVars(edge.condition, metaVars)
        edgeEffectSignatureMetaVars(edge.effect, metaVars)
        metaVars
    }

    is Edge.MethodEnter -> {
        val metaVars = BitSet()
        edgeConditionSignatureMetaVars(edge.condition, metaVars)
        edgeEffectSignatureMetaVars(edge.effect, metaVars)
        metaVars
    }
}

private fun MetaVarCtx.edgeConditionSignatureMetaVars(condition: EdgeCondition, metaVars: BitSet) {
    condition.readMetaVar.values.forEach { predicates ->
        predicates.forEach { predicateSignatureMetaVars(it.predicate, metaVars) }
    }

    condition.other.forEach { predicateSignatureMetaVars(it.predicate, metaVars) }
}

private fun MetaVarCtx.edgeEffectSignatureMetaVars(effect: EdgeEffect, metaVars: BitSet) {
    effect.assignMetaVar.values.forEach { predicates ->
        predicates.forEach { predicateSignatureMetaVars(it.predicate, metaVars) }
    }
}

private fun MetaVarCtx.predicateSignatureMetaVars(predicate: Predicate, metaVars: BitSet) {
    methodSignatureMetaVars(predicate.signature, metaVars)
    predicate.constraint?.let { methodConstraintMetaVars(it, metaVars) }
}

private fun MetaVarCtx.methodSignatureMetaVars(signature: MethodSignature, metaVars: BitSet) {
    typeNameMetaVars(signature.enclosingClassName.name, metaVars)

    val name = signature.methodName.name
    if (name is SignatureName.MetaVar) {
        metaVars.set(name.metaVar.idx())
    }
}

private fun MetaVarCtx.methodConstraintMetaVars(signature: MethodConstraint, metaVars: BitSet) {
    when (signature) {
        is ClassModifierConstraint -> signatureModifierMetaVars(signature.modifier, metaVars)
        is MethodModifierConstraint -> signatureModifierMetaVars(signature.modifier, metaVars)
        is NumberOfArgsConstraint -> {}
        is ParamConstraint -> paramConditionMetaVars(signature.condition, metaVars)
    }
}

private fun MetaVarCtx.signatureModifierMetaVars(sm: SignatureModifier, metaVars: BitSet) {
    typeNameMetaVars(sm.type, metaVars)

    val value = sm.value
    if (value is SignatureModifierValue.MetaVar) {
        metaVars.set(value.metaVar.idx())
    }
}

private fun MetaVarCtx.paramConditionMetaVars(pc: ParamCondition.Atom, metaVars: BitSet) {
    when (pc) {
        is IsMetavar -> {} // handled semantically with taint engine
        is ParamCondition.ParamModifier -> signatureModifierMetaVars(pc.modifier, metaVars)

        is StringValueMetaVar -> {
            /**
             *  todo: for now we ignore metavar substitution
             *  "$A"; "$A" will trigger for different A values
             *  */
        }

        is ParamCondition.TypeIs -> {
            typeNameMetaVars(pc.typeName, metaVars)
        }

        is ParamCondition.SpecificStaticFieldValue -> {
            typeNameMetaVars(pc.fieldClass, metaVars)
        }

        ParamCondition.AnyStringLiteral,
        is SpecificBoolValue,
        is SpecificStringValue -> {
            // do nothing, no metavars
        }
    }
}

private fun MetaVarCtx.typeNameMetaVars(typeName: TypeNamePattern, metaVars: BitSet) {
    when (typeName) {
        is TypeNamePattern.MetaVar -> {
            metaVars.set(typeName.metaVar.idx())
        }

        TypeNamePattern.AnyType,
        is TypeNamePattern.ClassName,
        is TypeNamePattern.PrimitiveName,
        is TypeNamePattern.FullyQualified -> {
            // no metavars
        }
    }
}

private fun Edge.ensurePositiveCondition(ctx: RuleConversionCtx): Edge? = when (this) {
    Edge.AnalysisEnd -> this
    is Edge.MethodCall -> condition.ensurePositiveCondition(ctx)?.let { copy(condition = it) }
    is Edge.MethodEnter -> condition.ensurePositiveCondition(ctx)?.let { copy(condition = it) }
}

private fun EdgeCondition.ensurePositiveCondition(ctx: RuleConversionCtx): EdgeCondition? {
    if (containsPositivePredicate()) return this

    val signatures = hashSetOf<MethodSignature>()
    other.mapTo(signatures) { it.predicate.signature }
    readMetaVar.values.forEach { predicates -> predicates.mapTo(signatures) { it.predicate.signature } }

    if (signatures.size == 1) {
        // !f(a) /\ !f(b) -> f(*) /\ !f(a) /\ !f(b)
        val commonSignature = signatures.single()
        val positivePredicate = Predicate(commonSignature, constraint = null)
        val otherPredicates = other + MethodPredicate(positivePredicate, negated = false)
        return copy(other = otherPredicates)
    }

    ctx.semgrepRuleTrace.error(
        "Edge without positive predicate",
        Reason.ERROR
    )

    return null
}

private fun EdgeCondition.findPositivePredicate(): Predicate? =
    other.find { !it.negated }?.predicate
        ?: readMetaVar.values.firstNotNullOfOrNull { p -> p.find { !it.negated }?.predicate }

private fun EdgeCondition.containsPositivePredicate(): Boolean =
    other.any { !it.negated } || readMetaVar.values.any { p -> p.any { !it.negated } }

private fun Edge.canAssignStateVar(): Boolean = when (this) {
    Edge.AnalysisEnd -> false
    is Edge.MethodCall -> true
    is Edge.MethodEnter -> true
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

private fun TaintRuleGenerationCtx.generateTaintSinkRules(
    id: String, meta: SinkMetaData,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
    checkRule: (SerializedFunctionNameMatcher, SerializedCondition) -> Boolean,
) = generateTaintRules({ currentRules, ruleEdge, _, function, cond ->
    if (!checkRule(function, cond)) {
        return@generateTaintRules emptyList()
    }

    if (function.isGeneratedReturnValue()) {
        return@generateTaintRules generateEndSink(currentRules, cond, id, meta)
    }

    val rule = when (ruleEdge.edge) {
        is Edge.MethodEnter -> SerializedRule.MethodEntrySink(
            function, signature = null, overrides = false, cond, id, meta = meta
        )

        is Edge.MethodCall -> SerializedRule.Sink(
            function, signature = null, overrides = true, cond, id, meta = meta
        )

        Edge.AnalysisEnd -> return@generateTaintRules generateEndSink(currentRules, cond, id, meta)
    }
    listOf(rule)
}, semgrepRuleTrace)

private fun TaintRuleGenerationCtx.generateTaintSanitizerRules(
    taintMarkName: String,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
) = generateTaintRules({ _, ruleEdge, _, function, cond ->
    if (ruleEdge.edge !is Edge.MethodCall) {
        semgrepRuleTrace.error("Non method call cleaner", Reason.NOT_IMPLEMENTED)
    }

    val cleanerPos = PositionBase.AnyArgument(classifier = "tainted")
    val action = SerializedTaintCleanAction(taintMarkName, PositionBaseWithModifiers.BaseOnly(cleanerPos))
    val rule = SerializedRule.Cleaner(function, signature = null, overrides = true, cond, listOf(action))

    listOf(rule)
}, semgrepRuleTrace)

private fun generateEndSink(
    currentRules: List<SerializedItem>,
    cond: SerializedCondition,
    id: String,
    meta: SinkMetaData
): List<SinkRule> {
    val endCondition = cond.rewriteAsEndCondition()
    val entryPointRules = currentRules.filterIsInstance<SerializedRule.EntryPoint>()

    if (entryPointRules.isEmpty()) {
        return listOf(AnalysisEndSink(endCondition, id, meta = meta))
    }

    return entryPointRules.map { rule ->
        val sinkCond = SerializedCondition.and(listOf(rule.condition ?: SerializedCondition.True, endCondition))
        SerializedRule.MethodExitSink(rule.function, rule.signature, rule.overrides, sinkCond, id, meta = meta)
    }
}

private fun SerializedCondition.rewriteAsEndCondition(): SerializedCondition = when (this) {
    is SerializedCondition.And -> SerializedCondition.and(allOf.map { it.rewriteAsEndCondition() })
    is SerializedCondition.Or -> SerializedCondition.Or(anyOf.map { it.rewriteAsEndCondition() })
    is SerializedCondition.Not -> SerializedCondition.not(not.rewriteAsEndCondition())
    SerializedCondition.True -> this
    is SerializedCondition.ClassAnnotated -> this
    is SerializedCondition.MethodAnnotated -> this
    is SerializedCondition.MethodNameMatches -> this
    is SerializedCondition.ClassNameMatches -> this
    is SerializedCondition.AnnotationType -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.ConstantCmp -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.ConstantEq -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.ConstantGt -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.ConstantLt -> copy(pos = pos.rewriteAsEndPosition())
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

private fun TaintRuleGenerationCtx.generateTaintSourceRules(
    stateVars: Set<MetavarAtom>, taintMarkName: String,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
) = generateTaintRules({ _, ruleEdge, condition, function, cond ->
    val actions = stateVars.flatMapTo(mutableListOf()) { varName ->
        val varPosition = condition.accessedVarPosition[varName] ?: return@flatMapTo emptyList()
        varPosition.positions.map {
            SerializedTaintAssignAction(taintMarkName, pos = PositionBaseWithModifiers.BaseOnly(it))
        }
    }

    if (actions.isEmpty()) return@generateTaintRules emptyList()

    if (function.isGeneratedReturnValue()) {
        TODO("Eliminate generated return value")
    }

    val rule = when (ruleEdge.edge) {
        is Edge.MethodCall -> SerializedRule.Source(
            function, signature = null, overrides = true, cond, actions
        )

        is Edge.MethodEnter -> SerializedRule.EntryPoint(
            function, signature = null, overrides = false, cond, actions
        )

        Edge.AnalysisEnd -> TODO()
    }

    listOf(rule)
}, semgrepRuleTrace)

private fun SinkRuleGenerationCtx.generateTaintPassRules(
    fromVar: MetavarAtom, toVar: MetavarAtom,
    taintMarkName: String,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): List<SerializedItem> {
    // todo: generate taint pass when possible
    return generateTaintSourceRules(setOf(toVar), taintMarkName, semgrepRuleTrace)
}

private fun TaintRuleGenerationCtx.generateTaintRules(
    generateAcceptStateRules: (
        currentGeneratedRules: List<SerializedItem>,
        TaintRuleEdge,
        EvaluatedEdgeCondition,
        SerializedFunctionNameMatcher,
        SerializedCondition
    ) -> List<SerializedItem>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): List<SerializedItem> {
    val rules = mutableListOf<SerializedItem>()

    val evaluatedConditions = hashMapOf<State, MutableMap<Edge, EvaluatedEdgeCondition>>()

    fun evaluate(edge: Edge, state: State): EvaluatedEdgeCondition =
        evaluatedConditions
            .getOrPut(state, ::hashMapOf)
            .getOrPut(edge) { evaluateEdgeCondition(edge, state, semgrepRuleTrace) }

    for (ruleEdge in edges) {
        val edge = ruleEdge.edge
        val state = ruleEdge.stateFrom

        val condition = evaluate(edge, state).addStateCheck(this, ruleEdge.checkGlobalState, state)
        rules += condition.additionalFieldRules

        val nodeId = automata.stateId(ruleEdge.stateTo)

        val requiredVariables = ruleEdge.stateTo.register.assignedVars.keys
        val actions = requiredVariables.flatMapTo(mutableListOf()) { varName ->
            val varPosition = condition.accessedVarPosition[varName] ?: return@flatMapTo emptyList()
            val stateMark = stateMarkName(varPosition.varName, nodeId)
            val valueMark = valueMarkName(varPosition.varName)

            varPosition.positions.flatMap {
                val pos = PositionBaseWithModifiers.BaseOnly(it)
                listOf(
                    SerializedTaintAssignAction(stateMark, pos = pos),
                    SerializedTaintAssignAction(valueMark, pos = pos),
                )
            }
        }

        if (ruleEdge.stateTo in globalStateAssignStates) {
            actions += SerializedTaintAssignAction(globalStateMarkName(ruleEdge.stateTo), pos = stateVarPosition)
        }

        if (actions.isNotEmpty()) {
            rules += generateRules(condition.ruleCondition) { function, cond ->
                if (function.isGeneratedReturnValue()) {
                    TODO("Eliminate generated return value")
                }

                when (edge) {
                    is Edge.MethodCall -> SerializedRule.Source(
                        function, signature = null, overrides = true, cond, actions
                    )

                    is Edge.MethodEnter -> SerializedRule.EntryPoint(
                        function, signature = null, overrides = false, cond, actions
                    )

                    Edge.AnalysisEnd -> TODO()
                }
            }
        }
    }

    for (ruleEdge in finalEdges) {
        val edge = ruleEdge.edge
        val state = ruleEdge.stateFrom

        val condition = evaluate(edge, state).addStateCheck(this, ruleEdge.checkGlobalState, state)
        rules += condition.additionalFieldRules

        if (ruleEdge.stateTo.node.accept) {
            rules += generateRules(condition.ruleCondition) { function, cond ->
                generateAcceptStateRules(rules, ruleEdge, condition, function, cond)
            }

            continue
        }

        val actions = condition.accessedVarPosition.values.flatMapTo(mutableListOf()) { varPosition ->
            val value = state.register.assignedVars[varPosition.varName] ?: return@flatMapTo emptyList()
            val stateMark = stateMarkName(varPosition.varName, value)
            val valueMark = valueMarkName(varPosition.varName)

            varPosition.positions.flatMap {
                val pos = PositionBaseWithModifiers.BaseOnly(it)
                listOf(
                    SerializedTaintCleanAction(stateMark, pos = pos),
                    SerializedTaintCleanAction(valueMark, pos = pos),
                )
            }
        }

        if (state in globalStateAssignStates) {
            actions += SerializedTaintCleanAction(globalStateMarkName(state), stateVarPosition)
        }

        if (actions.isNotEmpty()) {
            if (edge !is Edge.MethodCall) {
                TODO()
            }

            rules += generateRules(condition.ruleCondition) { function, cond ->
                if (function.isGeneratedReturnValue()) {
                    TODO("Eliminate generated return value")
                }

                SerializedRule.Cleaner(function, signature = null, overrides = true, cond, actions)
            }
        }
    }

    return rules
}

private fun EvaluatedEdgeCondition.addStateCheck(
    ctx: TaintRuleGenerationCtx,
    checkGlobalState: Boolean,
    state: State
): EvaluatedEdgeCondition {
    val stateChecks = mutableListOf<SerializedCondition.ContainsMark>()
    if (checkGlobalState) {
        stateChecks += SerializedCondition.ContainsMark(ctx.globalStateMarkName(state), ctx.stateVarPosition)
    } else {
        for ((metaVar, value) in state.register.assignedVars) {
            val markName = ctx.stateMarkName(metaVar, value)

            for (pos in accessedVarPosition[metaVar]?.positions.orEmpty()) {
                val position = PositionBaseWithModifiers.BaseOnly(pos)
                stateChecks += SerializedCondition.ContainsMark(markName, position)
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

private fun TaintRuleGenerationCtx.evaluateEdgeCondition(
    edge: Edge,
    state: State,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): EvaluatedEdgeCondition = when (edge) {
    is Edge.MethodCall -> evaluateMethodConditionAndEffect(edge.condition, edge.effect, state, semgrepRuleTrace)
    is Edge.MethodEnter -> evaluateMethodConditionAndEffect(edge.condition, edge.effect, state, semgrepRuleTrace)
    Edge.AnalysisEnd -> EvaluatedEdgeCondition(RuleConditionBuilder().build(), emptyList(), emptyMap())
}

private class RuleConditionBuilder {
    var enclosingClassPackage: SerializedNameMatcher? = null
    var enclosingClassName: SerializedNameMatcher? = null
    var methodName: SerializedNameMatcher? = null

    val conditions = hashSetOf<SerializedCondition>()

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
    state: State,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): EvaluatedEdgeCondition {
    val ruleBuilder = RuleConditionBuilder()
    val additionalFieldRules = mutableListOf<SerializedFieldRule>()

    val evaluatedSignature = evaluateConditionAndEffectSignatures(effect, condition, ruleBuilder, semgrepRuleTrace)

    condition.readMetaVar.values.flatten().forEach {
        val signature = it.predicate.signature.notEvaluatedSignature(evaluatedSignature)
        evaluateEdgePredicateConstraint(
            signature, it.predicate.constraint, it.negated, state, ruleBuilder, additionalFieldRules, semgrepRuleTrace
        )
    }

    condition.other.forEach {
        val signature = it.predicate.signature.notEvaluatedSignature(evaluatedSignature)
        evaluateEdgePredicateConstraint(
            signature, it.predicate.constraint, it.negated, state, ruleBuilder, additionalFieldRules, semgrepRuleTrace
        )
    }

    val varPositions = hashMapOf<MetavarAtom, RegisterVarPosition>()
    effect.assignMetaVar.values.flatten().forEach {
        findMetaVarPosition(it.predicate.constraint, varPositions)
    }

    return EvaluatedEdgeCondition(ruleBuilder.build(), additionalFieldRules, varPositions)
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
    ruleBuilder: RuleConditionBuilder,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): MethodSignature {
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

    return evaluateFormulaSignature(signatures, ruleBuilder, semgrepRuleTrace)
}

private fun TaintRuleGenerationCtx.evaluateFormulaSignature(
    signatures: List<MethodSignature>,
    builder: RuleConditionBuilder,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): MethodSignature {
    val signature = signatures.first()

    if (signatures.any { it != signature }) {
        TODO("Signature mismatch")
    }

    if (signature.isGeneratedAnyValueGenerator()) {
        TODO("Eliminate generated method")
    }

    val methodName = signature.methodName.name
    builder.methodName = evaluateFormulaSignatureMethodName(methodName, builder.conditions, semgrepRuleTrace)

    val classSignatureMatcherFormula = typeMatcher(signature.enclosingClassName.name)
    if (classSignatureMatcherFormula == null) return signature

    if (classSignatureMatcherFormula !is MetaVarConstraintFormula.Constraint) {
        TODO("Complex class signature matcher")
    }

    val classSignatureMatcher = classSignatureMatcherFormula.constraint
    when (classSignatureMatcher) {
        is SerializedNameMatcher.ClassPattern -> {
            builder.enclosingClassPackage = classSignatureMatcher.`package`
            builder.enclosingClassName = classSignatureMatcher.`class`
        }

        is SerializedNameMatcher.Simple -> {
            val parts = classSignatureMatcher.value.split(".")
            val packageName = parts.dropLast(1).joinToString(separator = ".")
            builder.enclosingClassPackage = SerializedNameMatcher.Simple(packageName)
            builder.enclosingClassName = SerializedNameMatcher.Simple(parts.last())
        }

        is SerializedNameMatcher.Pattern -> {
            TODO("Signature class name pattern")
        }
    }
    return signature
}

private fun TaintRuleGenerationCtx.evaluateFormulaSignatureMethodName(
    methodName: SignatureName,
    conditions: MutableSet<SerializedCondition>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): SerializedNameMatcher.Simple? {
    return when (methodName) {
        SignatureName.AnyName -> null
        is SignatureName.Concrete -> SerializedNameMatcher.Simple(methodName.name)
        is SignatureName.MetaVar -> {
            val constraint = when (val constraints = metaVarInfo.constraints[methodName.metaVar]) {
                null -> null
                is MetaVarConstraintOrPlaceHolder.Constraint -> constraints.constraint
                is MetaVarConstraintOrPlaceHolder.PlaceHolder -> {
                    semgrepRuleTrace.error(
                        "Placeholder: method name",
                        Reason.NOT_IMPLEMENTED
                    )
                    constraints.constraint
                }
            }

            val concrete = mutableListOf<String>()
            conditions += constraint?.constraint.toSerializedCondition { c, negated ->
                when (c) {
                    is MetaVarConstraint.Concrete -> {
                        if (!negated) {
                            concrete.add(c.value)
                            SerializedCondition.True
                        } else {
                            TODO("Negated concrete constraint")
                        }
                    }

                    is MetaVarConstraint.RegExp -> SerializedCondition.MethodNameMatches(c.regex)
                }
            }

            check(concrete.size <= 1) { "Multiple concrete names" }
            concrete.firstOrNull()?.let { SerializedNameMatcher.Simple(it) }
        }
    }
}

private fun TaintRuleGenerationCtx.evaluateEdgePredicateConstraint(
    signature: MethodSignature?,
    constraint: MethodConstraint?,
    negated: Boolean,
    state: State,
    builder: RuleConditionBuilder,
    additionalFieldRules: MutableList<SerializedFieldRule>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
) {
    if (!negated) {
        evaluateMethodConstraints(
            signature,
            constraint,
            state,
            builder.conditions,
            additionalFieldRules,
            semgrepRuleTrace
        )
    } else {
        val negatedConditions = hashSetOf<SerializedCondition>()
        evaluateMethodConstraints(
            signature,
            constraint,
            state,
            negatedConditions,
            additionalFieldRules,
            semgrepRuleTrace
        )
        builder.conditions += SerializedCondition.not(SerializedCondition.and(negatedConditions.toList()))
    }
}

private fun TaintRuleGenerationCtx.evaluateMethodConstraints(
    signature: MethodSignature?,
    constraint: MethodConstraint?,
    state: State,
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
            val annotation = signatureModifierConstraint(constraint.modifier)
            conditions += SerializedCondition.ClassAnnotated(annotation)
        }

        is MethodModifierConstraint -> {
            val annotation = signatureModifierConstraint(constraint.modifier)
            conditions += SerializedCondition.MethodAnnotated(annotation)
        }

        is NumberOfArgsConstraint -> conditions += SerializedCondition.NumberOfArgs(constraint.num)
        is ParamConstraint -> evaluateParamConstraints(
            constraint,
            state,
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
    val classType = typeMatcher(signature.enclosingClassName.name)
    conditions += classType.toSerializedCondition { typeMatcher, _ ->
        SerializedCondition.ClassNameMatches(typeMatcher)
    }

    val methodName = evaluateFormulaSignatureMethodName(signature.methodName.name, conditions, semgrepRuleTrace)
    if (methodName != null) {
        val methodNameRegex = "^${methodName.value}\$"
        conditions += SerializedCondition.MethodNameMatches(methodNameRegex)
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
    typeName: TypeNamePattern
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

        TypeNamePattern.AnyType -> return null

        is TypeNamePattern.MetaVar -> {
            val constraints = metaVarInfo.constraints[typeName.metaVar] ?: return null

            val constraint = when (constraints) {
                is MetaVarConstraintOrPlaceHolder.Constraint -> constraints.constraint.constraint
                is MetaVarConstraintOrPlaceHolder.PlaceHolder -> TODO("Placeholder: type name")
            }

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
                            if (clsPattern.patternCanMatchDot()){
                                SerializedNameMatcher.Pattern(value.regex)
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
    modifier: SignatureModifier
): SerializedCondition.AnnotationConstraint {
    val typeMatcherFormula = typeMatcher(modifier.type)

    val type = when (typeMatcherFormula) {
        null -> anyName()
        is MetaVarConstraintFormula.Constraint -> typeMatcherFormula.constraint
        else -> TODO("Complex annotation type")
    }

    val params = when (val v = modifier.value) {
        SignatureModifierValue.AnyValue -> null
        SignatureModifierValue.NoValue -> emptyList()
        is SignatureModifierValue.StringValue -> listOf(
            AnnotationParamStringMatcher(v.paramName, v.value)
        )

        is SignatureModifierValue.StringPattern -> listOf(
            AnnotationParamPatternMatcher(v.paramName, v.pattern)
        )

        is SignatureModifierValue.MetaVar -> {
            val paramMatchers = mutableListOf<SerializedCondition.AnnotationParamMatcher>()

            val constraints = metaVarInfo.constraints[v.metaVar]
            val constraint = when (constraints) {
                null -> null
                is MetaVarConstraintOrPlaceHolder.Constraint -> constraints.constraint.constraint
                is MetaVarConstraintOrPlaceHolder.PlaceHolder -> TODO("Placeholder: annotation")
            }

            constraint.toSerializedCondition { c, negated ->
                if (negated) {
                    TODO("Negated annotation param condition")
                }

                paramMatchers += when (c) {
                    is MetaVarConstraint.Concrete -> AnnotationParamStringMatcher(v.paramName, c.value)
                    is MetaVarConstraint.RegExp -> AnnotationParamPatternMatcher(v.paramName, c.regex)
                }

                SerializedCondition.True
            }
            paramMatchers
        }
    }

    return SerializedCondition.AnnotationConstraint(type, params)
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
    state: State,
    conditions: MutableSet<SerializedCondition>,
    additionalFieldRules: MutableList<SerializedFieldRule>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
) {
    val position = param.position.toSerializedPosition()
    conditions += evaluateParamCondition(position, param.condition, state, additionalFieldRules, semgrepRuleTrace)
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
    state: State,
    additionalFieldRules: MutableList<SerializedFieldRule>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): SerializedCondition {
    when (condition) {
        is IsMetavar -> {
            val constraints = metaVarInfo.constraints[condition.metavar.toString()]
            if (constraints != null) {
                // todo: semantic metavar constraint
                semgrepRuleTrace.error(
                    "Rule $uniqueRuleId: metavar ${condition.metavar} constraint ignored",
                    Reason.WARNING,
                )
            }

            val varValue = state.register.assignedVars[condition.metavar]
                // first occurrence
                ?: return SerializedCondition.True

            val pos = PositionBaseWithModifiers.BaseOnly(position)

            val valueMark = valueMarkName(condition.metavar)
            val stateMark = stateMarkName(condition.metavar, varValue)

            return serializedConditionOr(listOf(
                SerializedCondition.ContainsMark(valueMark, pos),
                SerializedCondition.ContainsMark(stateMark, pos),
            ))
        }

        is ParamCondition.TypeIs -> {
            return typeMatcher(condition.typeName).toSerializedCondition { typeNameMatcher, _ ->
                SerializedCondition.IsType(typeNameMatcher, position)
            }
        }

        is ParamCondition.SpecificStaticFieldValue -> {
            val enclosingClassMatcherFormula = typeMatcher(condition.fieldClass)

            val enclosingClassMatcher = when (enclosingClassMatcherFormula) {
                null -> anyName()
                is MetaVarConstraintFormula.Constraint -> enclosingClassMatcherFormula.constraint
                else -> TODO("Complex static field type")
            }

            val mark = valueMarkName(
                MetavarAtom.create("__STATIC_FIELD_VALUE__${condition.fieldName}")
            )

            val action = SerializedTaintAssignAction(
                mark, pos = PositionBaseWithModifiers.BaseOnly(PositionBase.Result)
            )
            additionalFieldRules += SerializedFieldRule.SerializedStaticFieldSource(
                enclosingClassMatcher, condition.fieldName, condition = null, listOf(action)
            )

            return SerializedCondition.ContainsMark(
                mark, PositionBaseWithModifiers.BaseOnly(position)
            )
        }

        ParamCondition.AnyStringLiteral -> {
            return SerializedCondition.IsConstant(position)
        }

        is SpecificBoolValue -> {
            val value = ConstantValue(ConstantType.Bool, condition.value.toString())
            return SerializedCondition.ConstantCmp(position, value, ConstantCmpType.Eq)
        }

        is SpecificStringValue -> {
            val value = ConstantValue(ConstantType.Str, condition.value)
            return SerializedCondition.ConstantCmp(position, value, ConstantCmpType.Eq)
        }

        is StringValueMetaVar -> {
            val constraints = metaVarInfo.constraints[condition.metaVar.toString()]
            val constraint = when (constraints) {
                null -> null
                is MetaVarConstraintOrPlaceHolder.Constraint -> constraints.constraint.constraint
                is MetaVarConstraintOrPlaceHolder.PlaceHolder -> TODO("Placeholder: string value")
            }
            return constraint.toSerializedCondition { c, _ ->
                when (c) {
                    is MetaVarConstraint.Concrete -> {
                        val value = ConstantValue(ConstantType.Str, c.value)
                        SerializedCondition.ConstantCmp(position, value, ConstantCmpType.Eq)
                    }

                    is MetaVarConstraint.RegExp -> {
                        SerializedCondition.ConstantMatches(c.regex, position)
                    }
                }
            }
        }

        is ParamCondition.ParamModifier -> {
            val annotation = signatureModifierConstraint(condition.modifier)
            return SerializedCondition.ParamAnnotated(position, annotation)
        }
    }
}

private fun simulateCondition(
    edge: Edge,
    stateId: Int,
    initialRegister: StateRegister
) = when (edge) {
    is Edge.MethodCall -> simulateEdgeEffect(edge.effect, stateId, initialRegister)
    is Edge.MethodEnter -> simulateEdgeEffect(edge.effect, stateId, initialRegister)
    Edge.AnalysisEnd -> StateRegister(emptyMap())
}

private fun simplifyEdgeCondition(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    cancelation: OperationCancelation,
    edge: AutomataEdgeType
) = when (edge) {
    is AutomataEdgeType.MethodCall -> simplifyMethodFormula(
        formulaManager, edge.formula, metaVarInfo, cancelation, applyNotEquivalentTransformations = true
    ).map {
        val (effect, cond) = edgeEffectAndCondition(it, formulaManager)
        Edge.MethodCall(cond, effect)
    }

    is AutomataEdgeType.MethodEnter -> simplifyMethodFormula(
        formulaManager, edge.formula, metaVarInfo, cancelation, applyNotEquivalentTransformations = true
    ).map {
        val (effect, cond) = edgeEffectAndCondition(it, formulaManager)
        Edge.MethodEnter(cond, effect)
    }

    AutomataEdgeType.End -> listOf(Edge.AnalysisEnd)

    AutomataEdgeType.PatternEnd, AutomataEdgeType.PatternStart -> error("unexpected edge type: $edge")
}

private fun Cube.predicates(manager: MethodFormulaManager): List<MethodPredicate> {
    check(!negated) { "Negated cube" }

    val result = mutableListOf<MethodPredicate>()
    cube.positiveLiterals.forEach {
        result += MethodPredicate(manager.predicate(it), negated = false)
    }
    cube.negativeLiterals.forEach {
        result += MethodPredicate(manager.predicate(it), negated = true)
    }
    return result
}

private fun edgeEffectAndCondition(cube: Cube, formulaManager: MethodFormulaManager): Pair<EdgeEffect, EdgeCondition> {
    val predicates = cube.predicates(formulaManager)

    val metaVarWrite = hashMapOf<MetavarAtom, MutableList<MethodPredicate>>()
    val metaVarRead = hashMapOf<MetavarAtom, MutableList<MethodPredicate>>()
    val other = mutableListOf<MethodPredicate>()

    for (mp in predicates) {
        val constraint = mp.predicate.constraint
        val metaVar = ((constraint as? ParamConstraint)?.condition as? IsMetavar)?.metavar

        if (!mp.negated && metaVar != null) {
            metaVarWrite.getOrPut(metaVar, ::mutableListOf).add(mp)
        }

        if (metaVar != null) {
            metaVarRead.getOrPut(metaVar, ::mutableListOf).add(mp)
        } else {
            other.add(mp)
        }
    }

    return EdgeEffect(metaVarWrite) to EdgeCondition(metaVarRead, other)
}

private fun simulateEdgeEffect(
    effect: EdgeEffect,
    stateId: Int,
    initialRegister: StateRegister,
): StateRegister {
    if (effect.assignMetaVar.isEmpty()) return initialRegister

    val newStateVars = initialRegister.assignedVars.toMutableMap()
    effect.assignMetaVar.keys.forEach {
        newStateVars[it] = stateId
    }

    effect.assignMetaVar.keys.forEach { metavar ->
        val basics = metavar.basics
        val toDelete = newStateVars.keys.filter {
            it.basics.intersect(basics).isNotEmpty() && it.basics.size < basics.size
        }
        toDelete.forEach(newStateVars::remove)
    }

    return StateRegister(newStateVars)
}

private fun anyName() = SerializedNameMatcher.Pattern(".*")

private fun SerializedFunctionNameMatcher.matchAnything(): Boolean =
    `class` == anyName() && `package` == anyName() && name == anyName()

private fun SerializedFunctionNameMatcher.isGeneratedReturnValue(): Boolean {
    val name = this.name as? SerializedNameMatcher.Simple ?: return false
    return name.value == generatedReturnValueMethod
}

private fun serializedConditionOr(args: List<SerializedCondition>): SerializedCondition {
    val result = mutableListOf<SerializedCondition>()
    for (arg in args) {
        if (arg is SerializedCondition.Or) {
            result.addAll(arg.anyOf)
            continue
        }

        if (arg is SerializedCondition.True) return SerializedCondition.True

        if (arg.isFalse()) continue

        result.add(arg)
    }

    return when (result.size) {
        0 -> mkFalse()
        1 -> result.single()
        else -> SerializedCondition.Or(result)
    }
}

private fun <T> MetaVarConstraintFormula<T>?.toSerializedCondition(
    transform: (T, Boolean) -> SerializedCondition,
): SerializedCondition {
    if (this == null) return SerializedCondition.True
    return toSerializedConditionUtil(negated = false, transform)
}

private fun <T> MetaVarConstraintFormula<T>.toSerializedConditionUtil(
    negated: Boolean,
    transform: (T, Boolean) -> SerializedCondition,
): SerializedCondition = when (this) {
    is MetaVarConstraintFormula.Constraint -> {
        transform(constraint, negated)
    }

    is MetaVarConstraintFormula.Not -> {
        SerializedCondition.not(this.negated.toSerializedConditionUtil(!negated, transform))
    }

    is MetaVarConstraintFormula.And -> {
        SerializedCondition.and(args.map { it.toSerializedConditionUtil(negated, transform) })
    }
}
