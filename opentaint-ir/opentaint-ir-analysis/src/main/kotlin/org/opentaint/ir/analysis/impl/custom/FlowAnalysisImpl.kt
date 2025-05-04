package org.opentaint.ir.analysis.impl.custom

import org.opentaint.ir.api.core.cfg.ControlFlowGraph
import org.opentaint.ir.api.core.cfg.Graph
import org.opentaint.ir.api.jvm.cfg.JIRGotoInst
import java.util.*

enum class Flow {
    IN {
        override fun <NODE, F> getFlow(e: FlowEntry<NODE, F>): F? {
            return e.inFlow
        }
    },
    OUT {
        override fun <NODE, F> getFlow(e: FlowEntry<NODE, F>): F? {
            return e.outFlow
        }
    };

    abstract fun <NODE, F> getFlow(e: FlowEntry<NODE, F>): F?
}

/**
 * Creates a new `Entry` graph based on a `Graph`. This includes pseudo topological order, local
 * access for predecessors and successors, a graph entry-point, connected component marker.
 */
private fun <NODE, T> ControlFlowGraph<NODE>.newScope(
    direction: FlowAnalysisDirection,
    entryFlow: T,
    isForward: Boolean
): List<FlowEntry<NODE, T>> {
    val size = toList().size
    val s = ArrayDeque<FlowEntry<NODE, T>>(size)
    val scope = ArrayList<FlowEntry<NODE, T>>(size)
    val visited = HashMap<NODE, FlowEntry<NODE, T>>((size + 1) * 4 / 3)

    // out of scope node
    val instructions: List<NODE>?
    val actualEntries = direction.entries(this)
    if (actualEntries.isNotEmpty()) {
        // normal cases: there is at least
        // one return statement for a backward analysis
        // or one entry statement for a forward analysis
        instructions = actualEntries
    } else {
        // cases without any entry statement
        if (isForward) {
            // case of a forward flow analysis on
            // a method without any entry point
            throw RuntimeException("No entry point for method in forward analysis")
        } else {
            // case of backward analysis on
            // a method which potentially has
            // an infinite loop and no return statement
            instructions = ArrayList()
            val head = entries.first()

            // collect all 'goto' statements to catch the 'goto' from the infinite loop
            val visitedInst = HashSet<NODE>()
            val list = arrayListOf(head)
            var temp: NODE
            while (list.isNotEmpty()) {
                temp = list.removeAt(0)
                visitedInst.add(temp)

                // only add 'goto' statements
                if (temp is JIRGotoInst) { // TODO caelmbleidd replace with abstract GOTO expr
                    instructions.add(temp)
                }
                for (next in successors(temp)) {
                    if (visitedInst.contains(next)) {
                        continue
                    }
                    list.add(next)
                }
            }

            if (instructions.isEmpty()) {
                throw RuntimeException("Backward analysis on an empty entry set.")
            }
        }
    }
    val root = RootEntry<NODE, T>()
    root.visitEntry(instructions, visited)
    root.inFlow = entryFlow
    root.outFlow = entryFlow

    val sv: Array<FlowEntry<NODE, T>?> = arrayOfNulls(size)
    val si = IntArray(size)
    var index = 0
    var i = 0
    var entry: FlowEntry<NODE, T> = root
    while (true) {
        if (i < entry.outs.size) {
            val next = entry.outs[i++]

            // an unvisited child node
            if (next.number == Int.MIN_VALUE) {
                next.number = s.size
                s.add(next)
                next.visitEntry(direction.outOf(this, next.data), visited)

                // save old
                si[index] = i
                sv[index] = entry
                index++
                i = 0
                entry = next
            }
        } else {
            if (index == 0) {
                assert(scope.size <= size)
                scope.reverse()
                return scope
            }
            scope.add(entry)
            s.pop(entry)

            // restore old
            index--
            entry = sv[index]!!
            i = si[index]
        }
    }
}

private fun <NODE, T> FlowEntry<NODE, T>.visitEntry(
    instructions: List<NODE>,
    visited: MutableMap<NODE, FlowEntry<NODE, T>>
): Array<FlowEntry<NODE, T>> {
    val n = instructions.size
    return Array(n) {
        instructions[it].toEntry(this, visited)
    }.also {
        outs = it
    }
}

private fun <NODE, T> NODE.toEntry(
    pred: FlowEntry<NODE, T>?,
    visited: MutableMap<NODE, FlowEntry<NODE, T>>
): FlowEntry<NODE, T> {
    // either we reach a new node or a merge node, the latter one is rare
    // so put and restore should be better that a lookup

    val newEntry = LeafEntry(this, pred)
    val oldEntry = visited.putIfAbsent(this, newEntry) ?: return newEntry

    // no restore required

    // adding self ref (real strongly connected with itself)
    if (oldEntry === pred) {
        oldEntry.isStronglyConnected = true
    }

    // merge nodes are rare, so this is ok
    val length = oldEntry.ins.size
    oldEntry.ins = Arrays.copyOf(oldEntry.ins, length + 1)
    oldEntry.ins[length] = pred
    return oldEntry
}

private fun <NODE, F> Deque<FlowEntry<NODE, F>>.pop(entry: FlowEntry<NODE, F>) {
    var min = entry.number
    for (e in entry.outs) {
        assert(e.number > Int.MIN_VALUE)
        min = min.coerceAtMost(e.number)
    }

    // not our SCC
    if (min != entry.number) {
        entry.number = min
        return
    }

    // we only want real SCCs (size > 1)
    var last = removeLast()
    last.number = Int.MAX_VALUE
    if (last === entry) {
        return
    }
    last.isStronglyConnected = true
    while (true) {
        last = removeLast()
        assert(last.number >= entry.number)
        last.isStronglyConnected = true
        last.number = Int.MAX_VALUE
        if (last === entry) {
            assert(last.ins.size >= 2)
            return
        }
    }
}

enum class FlowAnalysisDirection {
    BACKWARD {
        override fun <NODE> entries(g: ControlFlowGraph<NODE>): List<NODE> {
            return g.exits
        }

        override fun <NODE> outOf(g: ControlFlowGraph<NODE>, s: NODE): List<NODE> {
            return g.predecessors(s).toList()
        }
    },
    FORWARD {
        override fun <NODE> entries(g: ControlFlowGraph<NODE>): List<NODE> {
            return g.entries
        }

        override fun <NODE> outOf(g: ControlFlowGraph<NODE>, s: NODE): List<NODE> {
            return g.successors(s).toList()
        }
    };

    abstract fun <NODE> entries(g: ControlFlowGraph<NODE>): List<NODE>
    abstract fun <NODE> outOf(g: ControlFlowGraph<NODE>, s: NODE): List<NODE>
}

abstract class FlowEntry<NODE, T>(pred: FlowEntry<NODE, T>?) {

    abstract val data: NODE

    var number = Int.MIN_VALUE
    var isStronglyConnected = false
    var ins: Array<FlowEntry<NODE, T>> = pred?.let { arrayOf(pred) } ?: emptyArray()
    var outs: Array<FlowEntry<NODE, T>> = emptyArray()
    var inFlow: T? = null
    var outFlow: T? = null

    override fun toString(): String {
        return data.toString()
    }

}

class RootEntry<NODE, T> : FlowEntry<NODE, T>(null) {
    override val data: NODE get() = throw IllegalStateException()
}

class LeafEntry<NODE, T>(override val data: NODE, pred: FlowEntry<NODE, T>?) : FlowEntry<NODE, T>(pred)

abstract class FlowAnalysisImpl<NODE, T>(graph: ControlFlowGraph<NODE>) : AbstractFlowAnalysis<NODE, T>(graph) {

    protected abstract fun flowThrough(instIn: T?, ins: NODE, instOut: T)

    fun outs(s: NODE): T {
        return outs[s] ?: newFlow()
    }

    override fun ins(s: NODE): T {
        return ins[s] ?: newFlow()
    }

    private fun Iterable<FlowEntry<NODE, T>>.initFlow() {
        // If a node has only a single in-flow, the in-flow is always equal
        // to the out-flow if its predecessor, so we use the same object.
        // this saves memory and requires less object creation and copy calls.

        // Furthermore a node can be marked as `canSkip`, this allows us to use
        // the same "flow-set" for out-flow and in-flow. T merge node with within
        // a real scc cannot be omitted, as it could cause endless loops within
        // the fixpoint-iteration!
        for (node in this) {
            var omit = true
            val inFlow: T
            val outFlow: T

            if (node.ins.size > 1) {
                inFlow = newFlow()

                // no merge points in loops
                omit = !node.isStronglyConnected
            } else {
                assert(node.ins.size == 1) { "Missing head" }
                val flow = getFlow(node.ins.first(), node)
                assert(flow != null) { "Topological order is broken" }
                inFlow = flow!!
            }
            if (omit && node.data.canSkip) {
                // We could recalculate the graph itself but that is more expensive than
                // just falling through such nodes.
                outFlow = inFlow
            } else {
                outFlow = newFlow()
            }
            node.inFlow = inFlow
            node.outFlow = outFlow

            ins[node.data] = inFlow
            outs[node.data] = outFlow
        }
    }

    /**
     * If a flow node can be skipped return `true`, otherwise `false`. There is no guarantee a node will
     * be omitted. `canSkip` node does not influence the result of an analysis.
     *
     * If you are unsure, don't overwrite this method
     */
    protected open val NODE.canSkip: Boolean
        get() {
            return false
        }

    protected open fun getFlow(from: NODE, mergeNode: NODE) = Flow.OUT

    private fun getFlow(o: FlowEntry<NODE, T>, e: FlowEntry<NODE, T>): T? {
        return if (o.inFlow === o.outFlow) {
            o.outFlow
        } else {
            getFlow(o.data, e.data).getFlow(o)
        }
    }

    private fun FlowEntry<NODE, T>.meetFlows() {
        assert(ins.isNotEmpty())
        if (ins.size > 1) {
            var copy = true
            for (o in ins) {
                val flow = getFlow(o, this)
                val inFlow = inFlow
                if (flow != null && inFlow != null) {
                    if (copy) {
                        copy = false
                        copy(flow, inFlow)
                    } else {
                        mergeInto(data, inFlow, flow)
                    }
                }
            }
        }
    }

    open fun runAnalysis(
        direction: FlowAnalysisDirection,
        inFlow: Map<NODE, T?>,
        outFlow: Map<NODE, T?>
    ): Int {
        val scope = graph.newScope(direction, newEntryFlow(), isForward).also {
            it.initFlow()
        }
        val queue = PriorityQueue<FlowEntry<NODE, T>> { o1, o2 -> o1.number.compareTo(o2.number) }
            .also { it.addAll(scope) }

        // Perform fixed point flow analysis
        var numComputations = 0
        while (true) {
            val entry = queue.poll() ?: return numComputations
            entry.meetFlows()

            val hasChanged = flowThrough(entry)

            // Update queue appropriately
            if (hasChanged) {
                queue.addAll(entry.outs.toList())
            }
            numComputations++
        }
    }

    private fun flowThrough(entry: FlowEntry<NODE, T>): Boolean {
        if (entry.inFlow === entry.outFlow) {
            assert(!entry.isStronglyConnected || entry.ins.size == 1)
            return true
        }
        if (entry.isStronglyConnected) {
            // A flow node that is influenced by at least one back-reference.
            // It's essential to check if "flowThrough" changes the result.
            // This requires the calculation of "equals", which itself
            // can be really expensive - depending on the used flow-model.
            // Depending on the "merge"+"flowThrough" costs, it can be cheaper
            // to fall through. Only nodes with real back-references always
            // need to be checked for changes
            val out = newFlow()
            flowThrough(entry.inFlow, entry.data, out)
            if (out == entry.outFlow) {
                return false
            }
            // copy back the result, as it has changed
            entry.outFlow?.let {
                copy(out, it)
            }
            return true
        }

        // no back-references, just calculate "flowThrough"
        val outFlow = entry.outFlow
        if (outFlow != null) {
            flowThrough(entry.inFlow, entry.data, outFlow)
        }
        return true
    }

}
