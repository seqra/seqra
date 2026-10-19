package org.opentaint.dataflow.jvm.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.CombinedMethodContext
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.ap.TaintMark
import org.opentaint.dataflow.bidi.interp.ExitRules
import org.opentaint.dataflow.bidi.interp.FormKey
import org.opentaint.dataflow.bidi.interp.RuleKind
import org.opentaint.dataflow.bidi.interp.RuleOccurrenceId
import org.opentaint.dataflow.bidi.interp.MicroEdgeBuilder
import org.opentaint.dataflow.bidi.interp.RuleStatement
import org.opentaint.dataflow.bidi.interp.RuleView
import org.opentaint.dataflow.bidi.interp.SinkRule
import org.opentaint.dataflow.jvm.ap.ifds.CalleePositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRArgumentTypeMethodContext
import org.opentaint.dataflow.jvm.ap.ifds.JIRInstanceTypeMethodContext
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionRewriter
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.jvm.graph.JMethodExitExceptionalInst
import org.opentaint.jvm.graph.JMethodExitNormalInst

/** Entry and exit rules use method coordinates; the context selects only the entry filter. */
internal object JIRBoundaryForms {
    private fun MethodContext.locationClass(key: MethodKey, base: AccessPathBase): JIRClassOrInterface? = when (this) {
        is EmptyMethodContext -> if (base is AccessPathBase.This) (key.method as? JIRMethod)?.enclosingClass else null
        is JIRInstanceTypeMethodContext -> if (base is AccessPathBase.This) typeConstraint.type else null
        is JIRArgumentTypeMethodContext -> if (base is AccessPathBase.Argument && base.idx == argIdx) typeConstraint.type else null
        is CombinedMethodContext -> first.locationClass(key, base) ?: second.locationClass(key, base)
        else -> error("Unexpected method context: $this")
    }

    /** Zero is kept. A context-filtered initial base has its identity; source targets are gen only. */
    fun entryRules(interp: JIRInterpreter, entry: JIRMethodEntry, key: MethodKey,
                   aliasAnalysis: JIRLocalAliasAnalysis? = entry.aliasAnalysis,
                   potentialUnknownAliases: Boolean = false, view: RuleView = RuleView.FULL): RuleStatement {
        val method = key.method as JIRMethod
        val node = key.statement as JIRInst
        val rewriter = calleeRewriter(interp, aliasAnalysis, method, node, potentialUnknownAliases)
        val forms = interp.ruleForms(view)
        val builder = MicroEdgeBuilder().apply { keepZero() }
        val form = FormKey(interp.entries.formOwner, method, key, node)
        val bases = listOf<AccessPathBase>(AccessPathBase.This) + method.parameters.indices.map { AccessPathBase.Argument(it) }
        for (base in bases) {
            val cls = key.context.locationClass(key, base) ?: continue
            builder.keep(base)
            builder.operandFilter(base, interp.typeFilters.of(cls.toType()))
        }
        for ((ordinal, rule) in interp.rules.entryPointRulesForMethod(method, node, fact = null).withIndex()) {
            if (!forms.valid(rule)) continue
            val condition = forms.rewriteSourceOrSink(rule, rewriter, projectNegatives = false)
            if (condition.isTrue) forms.source(builder, rule,
                RuleOccurrenceId(form, RuleKind.ENTRY_SOURCE, ordinal), condition)
        }
        val sinks = interp.rules.sinkRulesForMethodEntry(method, node, fact = null)
            .withIndex().flatMap { (ordinal, rule) ->
                if (!forms.valid(rule)) return@flatMap emptyList()
                val condition = forms.rewriteSourceOrSink(rule, rewriter, projectNegatives = false)
                if (condition.isTrue) forms.sinks(rule, condition,
                    RuleOccurrenceId(form, RuleKind.ENTRY_SINK, ordinal)) else emptyList()
            }
        forms.sinkInputs(sinks).forEach(builder::staticOperation)
        return RuleStatement(builder.build(), genSummary(sinks), sinks)
    }

    /** End-fact marks belong to the sink; only entry-point sources supply entry marks. */
    fun entryMarks(rules: RuleStatement): Set<TaintMark> = rules.summary.edges
        .filter { it.isSource }.mapNotNullTo(HashSet()) { (it.edge.to.mark as? MarkSlot.Concrete)?.mark }

    /** Both exits evaluate rule sources and sinks. Only the normal exit carries removal metadata. */
    fun exitRules(interp: JIRInterpreter, entry: JIRMethodEntry, key: MethodKey,
                  exit: CommonInst, entryRules: RuleStatement,
                  aliasAnalysis: JIRLocalAliasAnalysis? = entry.aliasAnalysis,
                  potentialUnknownAliases: Boolean = false, view: RuleView = RuleView.FULL): ExitRules {
        val normal = when (exit) {
            is JMethodExitNormalInst -> true
            is JMethodExitExceptionalInst -> false
            else -> error("Not a boundary exit: $exit")
        }
        val forms = interp.ruleForms(view, exceptional = !normal)
        val method = key.method as JIRMethod
        val rewriter = calleeRewriter(interp, aliasAnalysis, method, exit, potentialUnknownAliases)
        val builder = MicroEdgeBuilder().apply { keepZero() }
        val form = FormKey(interp.entries.formOwner, method, null, exit)
        for ((ordinal, rule) in interp.rules.exitSourceRulesForMethod(method, exit, fact = null).withIndex()) {
            if (!forms.valid(rule)) continue
            val predicate = forms.source(builder, rule, RuleOccurrenceId(form, RuleKind.EXIT_SOURCE, ordinal),
                forms.rewriteSourceOrSink(rule, rewriter))
            predicate.leaves.forEach { builder.keep(it.fact.base) }
        }
        val sinks = interp.rules.sinkRulesForMethodExit(method, exit, fact = null, initialFacts = null)
            .withIndex().flatMap { (ordinal, rule) ->
                if (!forms.valid(rule)) emptyList() else forms.sinks(rule, forms.rewriteSourceOrSink(rule, rewriter),
                    RuleOccurrenceId(form, RuleKind.EXIT_SINK, ordinal))
            }
        forms.sinkInputs(sinks).forEach(builder::staticOperation)
        return ExitRules(RuleStatement(builder.build(), genSummary(sinks), sinks),
            globalStateDrop = normal && sinks.isNotEmpty(),
            entryMarks = if (normal) entryMarks(entryRules) else emptySet())
    }

    private fun calleeRewriter(interp: JIRInterpreter, aliasAnalysis: JIRLocalAliasAnalysis?, method: JIRMethod, node: CommonInst,
                               potentialUnknownAliases: Boolean) =
        JIRMarkAwareConditionRewriter(CalleePositionToJIRValueResolver(method), interp.checker, aliasAnalysis, node, potentialUnknownAliases)

    private fun genSummary(sinks: List<SinkRule>) = MicroEdgeBuilder().apply {
        sinks.forEach { sink -> sink.endFacts.forEach { edge(it.edge) } }
    }.buildGen()
}
