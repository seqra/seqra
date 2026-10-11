package org.opentaint.dataflow.bidi.interp

import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst

/** One owner uses a slot during each run; the barrier orders later runs. */
class FormsCache<F : Any>(private val size: Int, private val reverse: (F) -> F) {
    init { require(size >= 0) { "A form cache size must be nonnegative" } }
    private val slots = arrayOfNulls<Any>(Math.multiplyExact(2, size))

    @Suppress("UNCHECKED_CAST")
    fun get(slot: Int, direction: Direction, forward: () -> F): F {
        require(slot in 0 until size) { "A form slot must be within the cache" }
        val f = (slots[2 * slot] ?: forward().also { slots[2 * slot] = it }) as F
        if (direction == Direction.FORWARD) return f
        return (slots[2 * slot + 1] ?: reverse(f).also { slots[2 * slot + 1] = it }) as F
    }
}

/** Cached language forms retain method and context ownership. */
interface MethodForms {
    fun statement(direction: Direction, s: CommonInst): StatementSummary
    fun call(direction: Direction, s: CommonInst, call: CommonCallExpr): CallPlan
    fun entryRules(direction: Direction): RuleStatement
    fun exitRules(direction: Direction, exit: CommonInst): ExitRules
}

/** The run reads forward or reversed cached forms through this direction view. */
class DirectedForms(val interp: Interpreter, val direction: Direction, private val forms: (MethodKey) -> MethodForms) {
    private val forward get() = direction == Direction.FORWARD
    fun startNodes(m: MethodKey, zero: Boolean): List<CommonInst> =
        if (forward) listOf(interp.entryNode(m)) else interp.exitNodes(m).filter { zero || !it.exceptional }.map { it.node }
    fun startRules(m: MethodKey, node: CommonInst): RuleStatement =
        if (forward) forms(m).entryRules(Direction.FORWARD) else forms(m).exitRules(Direction.BACKWARD, node).rules
    fun statement(m: MethodKey, s: CommonInst) = forms(m).statement(direction, s)
    fun call(m: MethodKey, s: CommonInst, c: CommonCallExpr) = forms(m).call(direction, s, c)
    fun endNodes(m: MethodKey): List<CommonInst> =
        if (forward) interp.exitNodes(m).filter { !it.exceptional }.map { it.node } else listOf(interp.entryNode(m))
    fun endRules(m: MethodKey, node: CommonInst): ExitRules =
        if (forward) forms(m).exitRules(Direction.FORWARD, node)
        else ExitRules(forms(m).entryRules(Direction.BACKWARD), globalStateDrop = false, entryMarks = emptySet())
}
