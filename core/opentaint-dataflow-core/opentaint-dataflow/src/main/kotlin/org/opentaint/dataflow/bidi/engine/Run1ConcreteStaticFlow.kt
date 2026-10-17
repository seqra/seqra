package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.ir.api.common.cfg.CommonInst

/** Owner-local concrete diagonal work. Effect boundaries use the ordinary evaluator. */
internal class Run1ConcreteStaticFlow(private val core: RunMethodAnalyzer) {
    val entries = ConcreteStaticEntries(core.ops)
    private var entry: TaintTree? = null
    private val at = HashMap<CommonInst, TaintTree>()
    private val pending = LinkedHashMap<CommonInst, TaintTree>()
    val isEmpty get() = pending.isEmpty()
    fun entryFacts(): TaintTree? = entry

    fun add(initial: InitialAp) = add(entries.single(initial))
    fun add(value: TaintTree) {
        entries.validate(value)
        val (stored, delta) = entries.merge(entry, value)
        entry = stored
        if (delta == null) return
        for (node in core.forms.startNodes(core.key, zero = false)) {
            core.ap.cancellation.checkpoint()
            core.forms.withStartRules(core.key, node) { rules ->
                val affected = entries.affected(rules, delta)
                if (affected != null) for (initial in entries.originals(affected)) core.startStaticScalar(initial, node)
                entries.remainder(delta, affected)?.let { offer(node, it) }
            }
        }
    }

    private fun offer(node: CommonInst, value: TaintTree) {
        val (stored, delta) = entries.merge(at[node], value)
        if (delta == null) return
        at[node] = stored
        pending[node] = entries.merge(pending[node], delta).first
        core.requestWork()
    }
    fun work() {
        val item = pending.entries.first()
        val node = item.key
        val value = item.value
        pending.remove(node)
        core.runnerPort.onConcreteStaticProcess?.invoke(core.key, node, value)
        if (core.context.callAt(node) != null) core.processConcreteStaticCall(node, value)
        else core.forms.withStatement(core.key, node) { summary ->
            val affected = entries.affected(summary, value)
            if (affected != null) for (initial in entries.originals(affected))
                core.processStaticScalar(EdgeDelta(initial, node, entries.current(initial)))
            entries.remainder(value, affected)?.let { after(node, it) }
        }
    }
    fun capture(premise: PremiseKey, node: CommonInst, facts: Facts): Boolean {
        if (!entries.canonical(premise, facts)) return false
        after(node, entries.single(premise as InitialAp))
        return true
    }
    fun after(node: CommonInst, value: TaintTree) {
        if (core.context.isEnd(node) || core.context.isExceptionalExit(node)) {
            // STAR -> ANY is a real summary even in a read-free method.
            // Ordinary exit evaluation also preserves events when facts die.
            for (initial in entries.originals(value))
                core.finishConcreteStaticScalar(initial, node, entries.current(initial))
        }
        core.context.forEachSuccessor(node) { offer(it, value) }
    }
    fun clear() { entry = null; at.clear(); pending.clear() }
}
