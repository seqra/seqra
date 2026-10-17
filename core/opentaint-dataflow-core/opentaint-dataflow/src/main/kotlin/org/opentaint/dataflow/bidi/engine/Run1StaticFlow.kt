package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.ir.api.common.cfg.CommonInst

/** Actor-owned exact identity descriptors. No descriptor is submitted as an
 * ordinary AP fact, nor does it invent an entry supply at a ZERO-reached call. */
internal class Run1StaticFlow(private val core: RunMethodAnalyzer) {
    val entries = StaticIdentityEntries(core.ops)
    private val statistics = core.context.shared.abstractionStatistics
    private var entry: FlowTree? = null
    private val at = HashMap<CommonInst, FlowTree>()
    private val pending = LinkedHashMap<CommonInst, FlowTree>()
    val isEmpty get() = pending.isEmpty()
    fun entryFacts(): FlowTree? = entry

    fun add(value: FlowTree) {
        entries.validate(value)
        val (stored, delta) = entries.merge(entry, value)
        entry = stored
        if (delta == null) return
        statistics?.staticEntry(staticEntryMemberships(delta))
        for (node in core.forms.startNodes(core.key, zero = false)) {
            core.ap.cancellation.checkpoint()
            core.forms.withStartRules(core.key, node) { rules ->
                val affected = entries.affected(rules, delta)
                if (affected != null) for (initial in entries.originals(affected)) {
                    statistics?.staticScalar()
                    core.startStaticScalar(initial, node)
                }
                entries.remainder(delta, affected)?.let { offer(node, it) }
            }
        }
    }

    private fun offer(node: CommonInst, value: FlowTree) {
        val (stored, fresh) = entries.merge(at[node], value)
        if (fresh == null) return
        if (node !in at) statistics?.staticNodes(1)
        at[node] = stored
        pending[node] = entries.merge(pending[node], fresh).first
        core.requestWork()
    }

    fun work() {
        val item = pending.entries.first()
        val node = item.key
        val value = item.value
        pending.remove(node)
        statistics?.staticWork()
        core.runnerPort.onStaticProcess?.invoke(core.key, node, value)
        if (core.context.callAt(node) != null) core.processStaticCall(node, value)
        else core.forms.withStatement(core.key, node) { summary ->
            val affected = entries.affected(summary, value)
            if (affected != null) for (initial in entries.originals(affected)) {
                statistics?.staticScalar()
                core.processStaticScalar(EdgeDelta(initial, node, entries.single(initial)))
            }
            entries.remainder(value, affected)?.let { after(node, it) }
        }
    }

    /** Only a complete original/payload identity can rejoin the packed lane. */
    fun capture(premise: PremiseKey, node: CommonInst, facts: Facts): Boolean {
        val initial = premise as? InitialAp ?: return false
        if (facts !is FlowTree || compactRun1StaticIdentity(core.ops, initial, facts) != null) return false
        after(node, facts)
        return true
    }

    fun after(node: CommonInst, value: FlowTree) {
        if (core.context.isEnd(node) || core.context.isExceptionalExit(node)) {
            core.forms.withEndRules(core.key, node) { exit ->
                val affected = entries.affected(exit.rules, value)
                if (affected != null) for (initial in entries.originals(affected)) {
                    statistics?.staticScalar()
                    core.finishStaticScalar(initial, node, entries.single(initial))
                }
                // Remaining pure field identities are covered by the stored root default.
            }
        }
        core.context.forEachSuccessor(node) { offer(it, value) }
    }

    fun clear() { statistics?.staticNodes(-at.size.toLong()); entry = null; at.clear(); pending.clear() }
}
