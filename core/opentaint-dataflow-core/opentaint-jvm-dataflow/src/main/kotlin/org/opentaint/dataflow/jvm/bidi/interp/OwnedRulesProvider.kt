package org.opentaint.dataflow.jvm.bidi.interp

import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.bidi.ap.MarkGroupKey
import org.opentaint.dataflow.bidi.ap.MarkTable
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.configuration.jvm.serialized.UserDefinedRuleInfo
import org.opentaint.dataflow.jvm.ap.ifds.taint.ContainsMarkOnAnyField
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.dataflow.util.Cancellation

/** New-core view around the complete existing provider chain. Full objects and
 * full literals remain unchanged, except for owned sink copies with no end actions. */
class OwnedRulesProvider(private val delegate: TaintRulesProvider,
                         private val catalog: MarkTable) : TaintRulesProvider by delegate {
    private val names = java.util.Collections.synchronizedSet(linkedSetOf<String>())
    init {
        for ((name, owner) in delegate.markOwners) catalog.register(name, MarkGroupKey.Rule(owner))
    }

    private fun mark(name: String) { names.add(name); catalog.observeModelReference(name) }

    fun observedFullNames(): List<String> = synchronized(names) { names.toList() }

    /** Select only after full signed folding, binding and original role admission. */
    internal fun groupConditions(cancellation: Cancellation): GroupRuleConditions = GroupRuleConditions(catalog, cancellation)

    /** Census both selected and allRelevant role views before group execution. */
    fun censusMethod(method: CommonMethod, statement: CommonInst) {
        for (all in listOf(false, true)) {
            entryPointRulesForMethod(method, statement, null, all)
            sourceRulesForMethod(method, statement, null, all)
            exitSourceRulesForMethod(method, statement, null, all)
            sinkRulesForMethod(method, statement, null, all)
            sinkRulesForMethodEntry(method, statement, null, all)
            sinkRulesForMethodExit(method, statement, null, null, all)
            cleanerRulesForMethod(method, statement, null, all)
            passTroughRulesForMethod(method, statement, null, all)
        }
    }

    private fun condition(condition: Condition) {
        val pending = ArrayDeque<Condition>()
        pending.addLast(condition)
        while (pending.isNotEmpty()) when (val next = pending.removeLast()) {
            is CommonCondition.True -> Unit
            is CommonCondition.Not -> pending.addLast(next.arg)
            is CommonCondition.And -> next.args.forEach(pending::addLast)
            is CommonCondition.Or -> next.args.forEach(pending::addLast)
            is CommonCondition.Atom -> when (val atom = next.atom) {
                is ContainsMark -> mark(atom.mark.name)
                is ContainsMarkOnAnyField -> mark(atom.mark.name)
                else -> Unit
            }
        }
    }

    private fun action(action: Action) = when (action) {
        is AssignMark -> mark(action.mark.name)
        is CopyMark -> mark(action.mark.name)
        is RemoveMark -> mark(action.mark.name)
        is CopyAllMarks, is RemoveAllMarks -> Unit
    }

    private fun observe(rule: TaintConfigurationItem) {
        when (rule) {
            is TaintConfigurationSource -> { condition(rule.condition); rule.actionsAfter.forEach(::action) }
            is TaintConfigurationSink -> condition(rule.condition)
            is TaintPassThrough -> { condition(rule.condition); rule.actionsAfter.forEach(::action) }
            is TaintCleaner -> { condition(rule.condition); rule.actionsAfter.forEach(::action) }
        }
        // A sink's legacy relevance metadata can mention ignored end-only marks.
        // Its effective active marks come from its retained condition, above.
        if (rule !is TaintConfigurationSink)
            (rule.info as? UserDefinedRuleInfo)?.relevantTaintMarks?.forEach(::mark)
    }

    private fun <R : TaintConfigurationItem> owned(values: Iterable<R>): List<R> =
        values.map { it.also(::observe) }

    override fun entryPointRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        owned(delegate.entryPointRulesForMethod(method, statement, fact, allRelevant))
    override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        owned(delegate.sourceRulesForMethod(method, statement, fact, allRelevant))
    override fun exitSourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        owned(delegate.exitSourceRulesForMethod(method, statement, fact, allRelevant))
    override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        owned(delegate.sourceRulesForStaticField(field, statement, fact, allRelevant))
    override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        owned(delegate.cleanerRulesForMethod(method, statement, fact, allRelevant))
    override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst?, fact: FactAp?, allRelevant: Boolean) =
        owned(delegate.passTroughRulesForMethod(method, statement, fact, allRelevant))
    override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        owned(delegate.sinkRulesForMethod(method, statement, fact, allRelevant).map { if (it.trackFactsReachAnalysisEnd.isEmpty()) it else it.copy(trackFactsReachAnalysisEnd = emptyList()) })
    override fun sinkRulesForMethodEntry(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        owned(delegate.sinkRulesForMethodEntry(method, statement, fact, allRelevant).map { if (it.trackFactsReachAnalysisEnd.isEmpty()) it else it.copy(trackFactsReachAnalysisEnd = emptyList()) })
    override fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst, fact: FactAp?,
                                       initialFacts: Set<InitialFactAp>?, allRelevant: Boolean) =
        owned(delegate.sinkRulesForMethodExit(method, statement, fact, initialFacts, allRelevant)
            .map { if (it.trackFactsReachAnalysisEnd.isEmpty()) it else it.copy(trackFactsReachAnalysisEnd = emptyList()) })
}
