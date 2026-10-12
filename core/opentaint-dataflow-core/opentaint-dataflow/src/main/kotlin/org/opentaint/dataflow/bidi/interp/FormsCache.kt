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

    /** Callback adapter; this staging revision retains the original strong slots. */
    fun <R> withForm(slot: Int, direction: Direction, forward: () -> F, use: (F) -> R): R =
        use(get(slot, direction, forward))

}

/** Cached language forms retain method and context ownership. */
interface MethodForms {
    fun statement(direction: Direction, s: CommonInst): StatementSummary
    fun call(direction: Direction, s: CommonInst, call: CommonCallExpr): CallPlan
    fun entryRules(direction: Direction): RuleStatement
    fun exitRules(direction: Direction, exit: CommonInst): ExitRules

    fun <R> withStatement(direction: Direction, s: CommonInst, use: (StatementSummary) -> R): R =
        use(statement(direction, s))
    fun <R> withCall(direction: Direction, s: CommonInst, call: CommonCallExpr, use: (CallPlan) -> R): R =
        use(call(direction, s, call))
    fun <R> withEntryRules(direction: Direction, use: (RuleStatement) -> R): R = use(entryRules(direction))
    fun <R> withExitRules(direction: Direction, exit: CommonInst, use: (ExitRules) -> R): R =
        use(exitRules(direction, exit))
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

    fun <R> withStartRules(m: MethodKey, node: CommonInst, use: (RuleStatement) -> R): R =
        if (forward) forms(m).withEntryRules(Direction.FORWARD, use)
        else forms(m).withExitRules(Direction.BACKWARD, node) { use(it.rules) }
    fun <R> withStatement(m: MethodKey, s: CommonInst, use: (StatementSummary) -> R): R =
        forms(m).withStatement(direction, s, use)
    fun <R> withCall(m: MethodKey, s: CommonInst, c: CommonCallExpr, use: (CallPlan) -> R): R =
        forms(m).withCall(direction, s, c, use)
    fun <R> withEndRules(m: MethodKey, node: CommonInst, use: (ExitRules) -> R): R =
        if (forward) forms(m).withExitRules(Direction.FORWARD, node, use)
        else forms(m).withEntryRules(Direction.BACKWARD) {
            use(ExitRules(it, globalStateDrop = false, entryMarks = emptySet()))
        }
}
