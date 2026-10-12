package org.opentaint.dataflow.bidi.interp

import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.util.SoftReferenceManager
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.util.concurrent.atomic.AtomicReferenceArray

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
                    reverse(forward).also { backward = it; success = true }
                } finally {
                    statistics?.finishReversal(start, success)
                    reversing = false
                }
            }
        }
    }

    private fun formSlot(index: Int): FormSlot<F> {
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
                }
            } finally {
                statistics?.finishBuild(start, success, cached)
                slot.building = false
            }
        }
    }

    fun <R> withForm(slot: Int, direction: Direction, forward: () -> F, use: (F) -> R): R {
        require(slot in 0 until size) { "A form slot must be within the cache" }
        statistics?.access()
        val selectedPayload = payload(formSlot(slot), forward)
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
