package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.RequestMarkCursor
import org.opentaint.dataflow.bidi.store.RequestMarkSelection

/** One immutable raw group and one request row. Cursor state belongs only to the active join phase. */
class RequestGroupCursor internal constructor(
    private val manager: ApManager,
    private val initial: InitialAp,
    private val marks: RequestMarkSelection,
    private val facts: Facts,
    private val caller: CallerRef,
    query: Pattern? = null,
) {
    private class Prefix(val previous: Prefix?, val accessor: Int) { val depth: Int = (previous?.depth ?: 0) + 1 }
    private class Frame(val node: TaintNode, val prefix: Prefix?) {
        var entered = false
        var child = 0
    }
    private val path = query?.fact?.path?.toIntArray() ?: initial.pathData
    private val includeDescendants = query == null || query.fact.tail == Tail.STAR || query.fact.tail.isAny
    private val queryExclusion = if (query?.fact?.tail == Tail.STAR || query?.fact?.tail == Tail.ANY_TAINT)
        query.exclusion else ExclusionSet.Empty
    private var depth = 0
    private var prefix: Prefix? = null
    private var walking = true
    private var complete = initial.base != facts.base || facts is Reach
    private var flowNode = (facts as? FlowTree)?.root
    private var flowMarks: RequestMarkCursor? = null
    private var flowChild = 0
    private var taintNode = (facts as? TaintTree)?.root
    private val stack = ArrayDeque<Frame>()
    private var leaf: TaintLeaves? = null
    private var leafPrefix: Prefix? = null
    private var leafExact = false
    private var exactOffset = 0
    private var anyOffset = 0
    private var leafPath: PathNode? = null
    private var leafPathReady = false
    private var buildPrefix: Prefix? = null
    private var pendingMark: TaintMark? = null
    private var pendingExact = false
    private var staticAnswerAtSupplier = false

    var memberScans: Long = 0
        private set
    var geometryTests: Long = 0
        private set
    val isComplete: Boolean get() = complete

    init {
        manager.cancellation.checkpoint()
        check(query == null || (query.fact.base == initial.base && path.startsWith(initial.pathData)))
        check(initial.tail == Tail.STAR && initial.exclusion == ExclusionSet.Empty && initial.mark == MarkSlot.STAR) {
            "A request needs a policy initial or position answer"
        }
    }

    fun step(budget: Int, onAnswer: (InitialAp) -> Unit, onClimb: (TaintMark) -> Unit): Int {
        require(budget > 0)
        var used = 0
        while (used < budget && !complete) {
            manager.cancellation.checkpoint()
            used += when (facts) {
                is FlowTree -> flowStep(budget - used, facts, onClimb)
                is TaintTree -> taintStep(facts, onAnswer)
                is Reach -> { complete = true; 0 }
            }
        }
        return used
    }

    /** Proper ancestors need E admission. A nonempty tip subtree is an existential witness. */
    private fun flowStep(budget: Int, flow: FlowTree, onClimb: (TaintMark) -> Unit): Int {
        val memberCursor = flowMarks
        if (memberCursor != null) {
            val used = memberCursor.step(budget) { mark ->
                manager.cancellation.checkpoint()
                if (mark !in flow.markExclusion) {
                    check(caller.premise is InitialAp) { "An abstract link needs a singleton caller premise" }
                    onClimb(mark)
                }
            }
            memberScans += used
            if (memberCursor.isComplete) complete = true
            return used
        }
        val node = flowNode
        geometryTests++
        if (node == null) complete = true
        else if ((depth == path.size && !node.leaf.isEmpty) || (!node.leaf.isEmpty && depth < path.size &&
                (flow.layer == Layer.DEMAND || flow.exclusion.admits(path[depth])))) {
            flowMarks = marks.cursor()
        } else if (depth == path.size) {
            val children = node.childData
            if (!includeDescendants || children == null || flowChild == children.size) complete = true
            else {
                // Each candidate child consumes this step's one unit. Its subtree is nonempty.
                val offset = flowChild++
                if (queryExclusion.admits(node.accessorData!![offset])) flowMarks = marks.cursor()
            }
        } else {
            flowNode = node.child(path[depth++])
        }
        return 1
    }

    private fun taintStep(taint: TaintTree, onAnswer: (InitialAp) -> Unit): Int {
        // Static answers at or below the request retain the actual leaf position. Build that
        // canonical path once per leaf, with one prefix cell per budget unit, then reuse it.
        val pending = pendingMark
        if (pending != null) {
            val cell = buildPrefix
            if (cell != null) {
                leafPath = manager.path(cell.accessor, leafPath)
                buildPrefix = cell.previous
                return 1
            }
            leafPathReady = true
            pendingMark = null
            emit(taint, pending, pendingExact, onAnswer)
            return 1
        }
        val currentLeaf = leaf
        if (currentLeaf != null) {
            val exact = leafExact && exactOffset < currentLeaf.exact.data.size
            val id = when {
                exact -> currentLeaf.exact.data[exactOffset++]
                anyOffset < currentLeaf.any.data.size -> currentLeaf.any.data[anyOffset++]
                else -> { leaf = null; return 0 }
            }
            memberScans++
            if (marks.containsId(id)) {
                val mark = TaintMark(id)
                if (initial.base == STATIC && staticAnswerAtSupplier && !leafPathReady) {
                    pendingMark = mark; pendingExact = exact; buildPrefix = leafPrefix
                } else emit(taint, mark, exact, onAnswer)
            }
            return 1
        }
        if (walking) {
            val node = taintNode
            if (node == null) { walking = false; complete = true; return 0 }
            geometryTests++
            if (depth < path.size) {
                if (!node.leaf.any.isEmpty && taint.exclusion.admits(path[depth])) {
                    setLeaf(node.leaf, prefix, includeExact = false)
                }
                val accessor = path[depth++]
                prefix = Prefix(prefix, accessor)
                taintNode = node.child(accessor)
            } else {
                walking = false
                stack.addLast(Frame(node, prefix))
            }
            return 1
        }
        if (stack.isEmpty()) { complete = true; return 0 }
        val frame = stack.last()
        if (!frame.entered) {
            frame.entered = true
            geometryTests++
            if (!frame.node.leaf.isEmpty) setLeaf(frame.node.leaf, frame.prefix, includeExact = true)
            return 1
        }
        val children = frame.node.childData
        if (includeDescendants && children != null && frame.child < children.size) {
            val offset = frame.child++
            val accessor = frame.node.accessorData!![offset]
            // E tests the first continuation accessor only; deeper branches remain admitted.
            if (frame.prefix !== prefix || queryExclusion.admits(accessor))
                stack.addLast(Frame(children[offset], Prefix(frame.prefix, accessor)))
            return 1
        }
        stack.removeLast()
        return 1
    }

    private fun setLeaf(value: TaintLeaves, at: Prefix?, includeExact: Boolean) {
        leaf = value; leafPrefix = at; leafExact = includeExact; staticAnswerAtSupplier = (at?.depth ?: 0) >= initial.pathData.size
        exactOffset = 0; anyOffset = 0; leafPath = null; leafPathReady = false
    }

    private fun emit(taint: TaintTree, mark: TaintMark, exact: Boolean, onAnswer: (InitialAp) -> Unit) {
        val concrete = MarkSlot.Concrete(mark)
        val result = when {
            initial.base == STATIC && staticAnswerAtSupplier -> manager.initial(STATIC, leafPath,
                if (exact) Tail.EXACT else Tail.ANY, ExclusionSet.Empty, concrete)
            exact && leafPrefixDepth() == path.size -> manager.initial(initial.base, initial.path,
                Tail.EXACT, ExclusionSet.Empty, concrete)
            else -> manager.initial(initial.base, initial.path, Tail.STAR, ExclusionSet.Empty, concrete)
        }
        onAnswer(result)
    }

    private fun leafPrefixDepth(): Int {
        // Equality with the request tip is already represented by its prefix object. No path scan.
        return if (leafPrefix === prefix) path.size else -1
    }
}
