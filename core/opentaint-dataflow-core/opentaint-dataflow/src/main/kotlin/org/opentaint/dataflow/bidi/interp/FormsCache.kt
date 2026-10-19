package org.opentaint.dataflow.bidi.interp

import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.util.SoftReferenceManager
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.util.concurrent.atomic.AtomicReferenceArray

/** A later forward run must not reuse the run-1 GROUP payload. */
enum class RuleView { RUN1_GROUP, FULL }

/** Lazy slots retain one reclaimable forward/reverse payload, with one build at a time per slot. */
class FormsCache<F : Any>(
    private val size: Int,
    private val refs: SoftReferenceManager? = null,
    private val statistics: FormCacheStatistics? = null,
    private val reverse: (F) -> F,
) {
    constructor(size: Int, reverse: (F) -> F) : this(size, null, null, reverse)

    init { require(size >= 0) { "A form cache size must be nonnegative" } }
    private val slots = AtomicReferenceArray<FormSlot<F>?>(size)
    private val groupSlots = AtomicReferenceArray<FormSlot<F>?>(size)

    private class FormSlot<F : Any> {
        @Volatile var payload: Reference<ForwardReversePayload<F>>? = null
        var building = false
    }

    private class ForwardReversePayload<F : Any>(val forward: F, private val statistics: FormCacheStatistics?) {
        @Volatile private var backward: F? = null
        private var reversing = false

        fun selected(direction: Direction, reverse: (F) -> F): F {
            if (direction == Direction.FORWARD) return forward
            backward?.let { return it }
            return synchronized(this) {
                backward?.let { return it }
                check(!reversing) { "Recursive reversal of a form slot" }
                reversing = true
                val start = if (statistics != null) statistics.startReversal() else 0L
                var success = false
                try {
                    reverse(forward).also {
                        backward = it
                        success = true
                        statistics?.formBuilt(it, reversed = true)
                    }
                } finally {
                    statistics?.finishReversal(start, success)
                    reversing = false
                }
            }
        }
    }

    private fun formSlot(index: Int, view: RuleView): FormSlot<F> {
        val slots = if (view == RuleView.FULL) this.slots else groupSlots
        slots.get(index)?.let { return it }
        val created = FormSlot<F>()
        return if (slots.compareAndSet(index, null, created)) created else checkNotNull(slots.get(index))
    }

    private fun payload(slot: FormSlot<F>, forward: () -> F): ForwardReversePayload<F> {
        slot.payload?.get()?.let { statistics?.hit(); return it }
        return synchronized(slot) {
            slot.payload?.get()?.let { statistics?.hit(); return it }
            check(!slot.building) { "Recursive construction of a form slot" }
            slot.building = true
            val start = if (statistics != null) statistics.startBuild() else 0L
            var success = false
            var cached = false
            try {
                ForwardReversePayload(forward(), statistics).also {
                    slot.payload = if (refs == null) SoftReference(it) else refs.createRef(it)
                    cached = slot.payload != null
                    success = true
                    statistics?.formBuilt(it.forward, reversed = false)
                }
            } finally {
                statistics?.finishBuild(start, success, cached)
                slot.building = false
            }
        }
    }

    fun <R> withForm(slot: Int, direction: Direction, forward: () -> F, use: (F) -> R): R {
        return withForm(slot, direction, RuleView.FULL, forward, use)
    }

    fun <R> withForm(slot: Int, direction: Direction, view: RuleView, forward: () -> F, use: (F) -> R): R {
        require(slot in 0 until size) { "A form slot must be within the cache" }
        statistics?.access()
        val selectedPayload = payload(formSlot(slot, view), forward)
        statistics?.pin()
        try {
            return use(selectedPayload.selected(direction, reverse))
        } finally {
            Reference.reachabilityFence(selectedPayload)
            statistics?.unpin()
        }
    }

    fun get(slot: Int, direction: Direction, forward: () -> F): F =
        withForm(slot, direction, forward) { it }

    fun get(slot: Int, direction: Direction, view: RuleView, forward: () -> F): F =
        withForm(slot, direction, view, forward) { it }
}

/** Cached language forms retain method and context ownership. */
interface MethodForms {
    fun statement(direction: Direction, s: CommonInst): StatementSummary
    fun call(direction: Direction, s: CommonInst, call: CommonCallExpr): CallPlan
    fun entryRules(direction: Direction): RuleStatement
    fun exitRules(direction: Direction, exit: CommonInst): ExitRules

    fun statement(direction: Direction, s: CommonInst, view: RuleView): StatementSummary = statement(direction, s)
    fun call(direction: Direction, s: CommonInst, call: CommonCallExpr, view: RuleView): CallPlan = call(direction, s, call)
    fun entryRules(direction: Direction, view: RuleView): RuleStatement = entryRules(direction)
    fun exitRules(direction: Direction, exit: CommonInst, view: RuleView): ExitRules = exitRules(direction, exit)

    fun <R> withStatement(direction: Direction, s: CommonInst, use: (StatementSummary) -> R): R =
        use(statement(direction, s))
    fun <R> withCall(direction: Direction, s: CommonInst, call: CommonCallExpr, use: (CallPlan) -> R): R =
        use(call(direction, s, call))
    fun <R> withEntryRules(direction: Direction, use: (RuleStatement) -> R): R = use(entryRules(direction))
    fun <R> withExitRules(direction: Direction, exit: CommonInst, use: (ExitRules) -> R): R =
        use(exitRules(direction, exit))

    // Providers with distinct views override these scopes as well as their getters.
    // Compatibility defaults retain existing pin/rebuild hooks for either view.
    fun <R> withStatement(direction: Direction, s: CommonInst, view: RuleView, use: (StatementSummary) -> R): R =
        withStatement(direction, s, use)
    fun <R> withCall(direction: Direction, s: CommonInst, call: CommonCallExpr, view: RuleView, use: (CallPlan) -> R): R =
        withCall(direction, s, call, use)
    fun <R> withEntryRules(direction: Direction, view: RuleView, use: (RuleStatement) -> R): R =
        withEntryRules(direction, use)
    fun <R> withExitRules(direction: Direction, exit: CommonInst, view: RuleView, use: (ExitRules) -> R): R =
        withExitRules(direction, exit, use)
}

/** The run reads forward or reversed cached forms through this direction view. */
class DirectedForms(val interp: Interpreter, val direction: Direction, private val forms: (MethodKey) -> MethodForms,
                    val ruleView: RuleView) {
    constructor(interp: Interpreter, direction: Direction, forms: (MethodKey) -> MethodForms) :
        this(interp, direction, forms, RuleView.FULL)
    private val forward get() = direction == Direction.FORWARD
    fun startNodes(m: MethodKey, zero: Boolean): List<CommonInst> =
        if (forward) listOf(interp.entryNode(m)) else interp.exitNodes(m).filter { zero || !it.exceptional }.map { it.node }
    fun startRules(m: MethodKey, node: CommonInst): RuleStatement =
        if (forward) entryRules(m, Direction.FORWARD) else exitRules(m, Direction.BACKWARD, node).rules
    fun statement(m: MethodKey, s: CommonInst) = if (ruleView == RuleView.FULL)
        forms(m).statement(direction, s) else forms(m).statement(direction, s, ruleView)
    fun call(m: MethodKey, s: CommonInst, c: CommonCallExpr) = if (ruleView == RuleView.FULL)
        forms(m).call(direction, s, c) else forms(m).call(direction, s, c, ruleView)
    fun endNodes(m: MethodKey): List<CommonInst> =
        if (forward) interp.exitNodes(m).filter { !it.exceptional }.map { it.node } else listOf(interp.entryNode(m))
    fun endRules(m: MethodKey, node: CommonInst): ExitRules =
        if (forward) exitRules(m, Direction.FORWARD, node)
        else ExitRules(entryRules(m, Direction.BACKWARD), globalStateDrop = false, entryMarks = emptySet())

    private fun entryRules(m: MethodKey, d: Direction) = if (ruleView == RuleView.FULL)
        forms(m).entryRules(d) else forms(m).entryRules(d, ruleView)
    private fun exitRules(m: MethodKey, d: Direction, s: CommonInst) = if (ruleView == RuleView.FULL)
        forms(m).exitRules(d, s) else forms(m).exitRules(d, s, ruleView)
    private fun <R> withEntry(m: MethodKey, d: Direction, use: (RuleStatement) -> R): R =
        if (ruleView == RuleView.FULL) forms(m).withEntryRules(d, use) else forms(m).withEntryRules(d, ruleView, use)
    private fun <R> withExit(m: MethodKey, d: Direction, s: CommonInst, use: (ExitRules) -> R): R =
        if (ruleView == RuleView.FULL) forms(m).withExitRules(d, s, use) else forms(m).withExitRules(d, s, ruleView, use)

    fun <R> withStartRules(m: MethodKey, node: CommonInst, use: (RuleStatement) -> R): R =
        if (forward) withEntry(m, Direction.FORWARD, use)
        else withExit(m, Direction.BACKWARD, node) { use(it.rules) }
    fun <R> withStatement(m: MethodKey, s: CommonInst, use: (StatementSummary) -> R): R =
        if (ruleView == RuleView.FULL) forms(m).withStatement(direction, s, use)
        else forms(m).withStatement(direction, s, ruleView, use)
    fun <R> withCall(m: MethodKey, s: CommonInst, c: CommonCallExpr, use: (CallPlan) -> R): R =
        if (ruleView == RuleView.FULL) forms(m).withCall(direction, s, c, use)
        else forms(m).withCall(direction, s, c, ruleView, use)
    fun <R> withEndRules(m: MethodKey, node: CommonInst, use: (ExitRules) -> R): R =
        if (forward) withExit(m, Direction.FORWARD, node, use)
        else withEntry(m, Direction.BACKWARD) {
            use(ExitRules(it, globalStateDrop = false, entryMarks = emptySet()))
        }
}
