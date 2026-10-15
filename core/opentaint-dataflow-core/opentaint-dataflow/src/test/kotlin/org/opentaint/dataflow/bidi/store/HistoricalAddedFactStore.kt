package org.opentaint.dataflow.bidi.store

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ApOps
import org.opentaint.dataflow.bidi.ap.EMPTY_PATH
import org.opentaint.dataflow.bidi.ap.Facts
import org.opentaint.dataflow.bidi.ap.GroupKey
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.ap.PathNode
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.ap.PremiseKey
import org.opentaint.dataflow.bidi.ap.forEachLeafPosition
import org.opentaint.dataflow.bidi.ap.groupKey
import org.opentaint.dataflow.bidi.ap.mergeAddDelta
import org.opentaint.dataflow.bidi.ap.FlowTree
import org.opentaint.dataflow.bidi.ap.FlowAlgebra
import org.opentaint.dataflow.bidi.ap.TaintTree
import org.opentaint.dataflow.bidi.ap.Reach
import org.opentaint.ir.api.common.cfg.CommonInst

/** Scalar input history used by historical test oracles, never retained by a production callee. */
/** One added leaf and its own layer, which can differ from the caller edge's layer. */
data class Link(val addedFact: Pattern, val linkLayer: Layer, val caller: CallerRef)

/** Historical oracle only. Production emission does not own this input relation. */
/** Indexed raw links share the exact accumulator used by cumulative subscriptions. */
class AddedFactStore(private val m: ApManager) {
    private val values = AddedFactAccumulator(m)
    private val index = AddedFactIndex(m.cancellation)
    private val ops = ApOps(m)
    private var activeView: View? = null

    data class Snapshot(val handle: Int, val ref: CallerRef, val group: GroupKey, val facts: Facts)
    data class Update(val handle: Int, val ref: CallerRef, val group: GroupKey, val facts: Facts, val delta: Facts)

    fun addUpdate(ref: CallerRef, linkLayer: Layer, added: Facts): Update? {
        val before = activeView?.let { view -> { handle: Int, old: AddedFactAccumulator.Snapshot ->
            view.beforeChange(handle, old)
        } }
        val update = values.add(ref, linkLayer, added, before) ?: return null
        m.forEachLeafPosition(update.delta) { base, path -> index.add(base, path, update.handle) }
        return Update(update.handle, update.snapshot.ref, update.snapshot.group, update.snapshot.facts, update.delta)
    }

    /** The new links of event E2, including every leaf for a new caller of an existing added fact. */
    fun add(ref: CallerRef, linkLayer: Layer, added: Facts): Facts? {
        return addUpdate(ref, linkLayer, added)?.delta
    }

    /** Pending deltas belong to the admitted header, even when an exact-only TAINT delta clears E. */
    fun mergePending(group: GroupKey, a: Facts, b: Facts): Facts {
        m.cancellation.checkpoint()
        check(a.groupKey.kind == group.kind && b.groupKey.kind == group.kind)
        check(a.base == group.base && b.base == group.base && a.layer == group.layer && b.layer == group.layer)
        return when (a) {
            is Reach -> { check(b is Reach); a }
            is FlowTree -> {
                check(b is FlowTree && a.markExclusion == group.markExclusion && b.markExclusion == group.markExclusion &&
                    a.exclusion == group.exclusion && b.exclusion == group.exclusion)
                a.withRoot(FlowAlgebra.mergeAddDelta(a.root, b.root, m.cancellation).first)
            }
            is TaintTree -> {
                check(b is TaintTree && (a.exclusion == group.exclusion || !a.root.hasAny) &&
                    (b.exclusion == group.exclusion || !b.root.hasAny))
                m.taintTree(group.base, group.layer,
                    m.taintAlg.mergeAddDelta(a.root, b.root, m.cancellation).first, group.exclusion)
            }
        }
    }

    /** At most one old value per changed captured group; no historical phase chain is retained. */
    fun beginSnapshot(): View {
        m.cancellation.checkpoint()
        check(activeView == null) { "Only one raw-link phase can be active" }
        return View(values.size).also { activeView = it }
    }

    inner class View internal constructor(private val groupHorizon: Int) : AutoCloseable {
        private val frozen = HashMap<Int, Snapshot>()
        private var closed = false
        val retainedSnapshotCount: Int get() = frozen.size
        internal fun beforeChange(handle: Int, old: AddedFactAccumulator.Snapshot) {
            if (handle < groupHorizon) frozen.putIfAbsent(handle, Snapshot(handle, old.ref, old.group, old.facts))
        }
        fun groupsNear(base: AccessPathBase, path: PathNode?): GroupCursor {
            check(!closed)
            m.cancellation.checkpoint()
            return GroupCursor(this, index.around(base, path), groupHorizon)
        }
        internal fun snapshot(handle: Int): Snapshot {
            check(!closed)
            return frozen[handle] ?: values.snapshot(handle).let { Snapshot(handle, it.ref, it.group, it.facts) }
        }
        override fun close() {
            if (closed) return
            closed = true
            frozen.clear()
            check(activeView === this)
            activeView = null
        }
    }

    inner class GroupCursor internal constructor(
        private val view: View,
        private val postings: AddedFactIndex.Cursor,
        private val groupHorizon: Int,
    ) {
        private val seen = IntOpenHashSet()
        val isComplete: Boolean get() = postings.isComplete
        fun step(budget: Int, visit: (Snapshot) -> Unit): Int = postings.step(budget) { handle ->
            m.cancellation.checkpoint()
            if (handle < groupHorizon && seen.add(handle)) visit(view.snapshot(handle))
        }
    }

    /** Candidate keys are deduplicated before reading only the overlapping leaves along and below the query path. */
    fun overlapping(base: AccessPathBase, path: PathNode?): Sequence<Link> {
        m.cancellation.checkpoint()
        val p = path?.toIntArray() ?: EMPTY_PATH
        val handles = ArrayList<Int>()
        val cursor = index.around(base, path)
        val seen = IntOpenHashSet()
        while (!cursor.isComplete) cursor.step(1024) { if (seen.add(it)) handles.add(it) }
        return handles.asSequence().flatMap { handle ->
            m.cancellation.checkpoint()
            val snapshot = values.snapshot(handle)
            ops.leavesNear(snapshot.facts, p).map {
                m.cancellation.checkpoint()
                Link(it, snapshot.group.layer, snapshot.ref)
            }
        }
    }

    /** Exact raw links for support computation at the barrier. */
    fun links(): Sequence<Link> {
        m.cancellation.checkpoint()
        return (0 until values.size).asSequence().flatMap { handle ->
            m.cancellation.checkpoint()
            val snapshot = values.snapshot(handle)
            ops.leaves(snapshot.facts).map {
                m.cancellation.checkpoint()
                Link(it, snapshot.group.layer, snapshot.ref)
            }
        }
    }
}
