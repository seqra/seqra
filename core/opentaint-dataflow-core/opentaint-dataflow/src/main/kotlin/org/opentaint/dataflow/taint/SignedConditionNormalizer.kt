package org.opentaint.dataflow.taint

import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.mkFalse
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.util.Cancellation
import java.util.Collections
import java.util.IdentityHashMap

/** Signed Boolean folding before the source/sink approximation. No clauses or products are built. */
object SignedConditionNormalizer {
    fun <A> normalize(condition: CommonCondition<A>, cancellation: Cancellation? = null,
                      negativeLiteralsAreTrue: Boolean = false): CommonCondition<A> =
        Compiler<A>(cancellation).normalize(condition, negativeLiteralsAreTrue)

    private const val FALSE = 0
    private const val TRUE = 1
    private const val LEAF = 2
    private const val AND = 3
    private const val OR = 4

    private class Node(val kind: Int, val variable: Int = -1, val negative: Boolean = false,
                       val children: List<Int> = emptyList(), val hasNegative: Boolean = false,
                       val hasPositive: Boolean = false, val hasAnd: Boolean = false, val hasOr: Boolean = false)
    private data class JunctionKey(val kind: Int, val children: List<Int>)
    private class Frame<A>(val condition: CommonCondition<A>, val negative: Boolean, val finish: Boolean)

    private class Compiler<A>(private val cancellation: Cancellation?) {
        private val nodes = arrayListOf(Node(FALSE), Node(TRUE))
        private val atoms = ArrayList<A>()
        private val variables = HashMap<A, Int>()
        private val leafIds = ArrayList<IntArray>()
        private val junctions = HashMap<JunctionKey, Int>()
        private val falseProofs = HashMap<Int, Boolean>()
        private val duals = hashMapOf(FALSE to TRUE, TRUE to FALSE)
        private fun checkpoint() { cancellation?.checkpoint() }

        fun normalize(input: CommonCondition<A>, projectNegative: Boolean): CommonCondition<A> {
            checkpoint()
            var root = compile(input)
            if (projectNegative && root > TRUE) {
                val rewritten = IntArray(nodes.size) { -1 }
                rewritten[FALSE] = FALSE
                rewritten[TRUE] = TRUE
                // Every child preceded its immutable parent when it was interned.
                for (id in 2 until rewritten.size) {
                    checkpoint()
                    val n = nodes[id]
                    rewritten[id] = if (n.kind == LEAF) {
                        if (n.negative) TRUE else id
                    } else junction(n.kind, n.children.map { rewritten[it] }, proveExtremes = false)
                }
                root = rewritten[root]
            }
            return materialize(root)
        }

        private fun compile(input: CommonCondition<A>): Int {
            val done = IdentityHashMap<CommonCondition<A>, IntArray>()
            val todo = ArrayDeque<Frame<A>>()
            todo.add(Frame(input, false, false))
            fun result(x: CommonCondition<A>, negated: Boolean): Int = checkNotNull(done[x])[if (negated) 1 else 0]
            while (todo.isNotEmpty()) {
                checkpoint()
                val f = todo.removeLast()
                val sign = if (f.negative) 1 else 0
                if (done[f.condition]?.get(sign)?.let { it >= 0 } == true) continue
                val x = f.condition
                if (!f.finish) {
                    todo.add(Frame(x, f.negative, true))
                    when (x) {
                        is CommonCondition.Not -> todo.add(Frame(x.arg, !f.negative, false))
                        is CommonCondition.And -> x.args.asReversed().forEach { todo.add(Frame(it, f.negative, false)) }
                        is CommonCondition.Or -> x.args.asReversed().forEach { todo.add(Frame(it, f.negative, false)) }
                        else -> Unit
                    }
                    continue
                }
                val id = when (x) {
                    is CommonCondition.True -> if (f.negative) FALSE else TRUE
                    is CommonCondition.Atom -> leaf(x.atom, f.negative)
                    is CommonCondition.Not -> result(x.arg, !f.negative)
                    is CommonCondition.And -> junction(if (f.negative) OR else AND, x.args.map { result(it, f.negative) })
                    is CommonCondition.Or -> junction(if (f.negative) AND else OR, x.args.map { result(it, f.negative) })
                }
                done.getOrPut(x) { intArrayOf(-1, -1) }[sign] = id
            }
            return result(input, false)
        }

        private fun leaf(atom: A, negative: Boolean): Int {
            val variable = variables.getOrPut(atom) {
                atoms.add(atom)
                leafIds.add(intArrayOf(-1, -1))
                atoms.lastIndex
            }
            val sign = if (negative) 1 else 0
            val old = leafIds[variable][sign]
            if (old >= 0) return old
            return nodes.size.also { id ->
                nodes.add(Node(LEAF, variable, negative, hasNegative = negative, hasPositive = !negative))
                leafIds[variable][sign] = id
            }
        }

        private fun junction(kind: Int, children: List<Int>, proveExtremes: Boolean = true): Int {
            val absorbing = if (kind == AND) FALSE else TRUE
            val neutral = if (kind == AND) TRUE else FALSE
            val unique = LinkedHashSet<Int>()
            val signs = HashMap<Int, Boolean>()
            val todo = ArrayDeque<Int>()
            children.asReversed().forEach { todo.add(it) }
            while (todo.isNotEmpty()) {
                checkpoint()
                val id = todo.removeLast()
                if (id == absorbing) return absorbing
                if (id == neutral) continue
                val n = nodes[id]
                if (n.kind == kind) {
                    n.children.asReversed().forEach { todo.add(it) }
                    continue
                }
                if (n.kind == LEAF) {
                    val old = signs.putIfAbsent(n.variable, n.negative)
                    if (old != null && old != n.negative) return absorbing
                }
                unique.add(id)
            }
            if (unique.isEmpty()) return neutral
            if (unique.size == 1) return unique.first()
            val owned = Collections.unmodifiableList(ArrayList(unique))
            val key = JunctionKey(kind, owned)
            val id = junctions.getOrPut(key) {
                nodes.size.also {
                    nodes.add(Node(kind, children = owned,
                        hasNegative = owned.any { nodes[it].hasNegative },
                        hasPositive = owned.any { nodes[it].hasPositive },
                        hasAnd = kind == AND || owned.any { nodes[it].hasAnd },
                        hasOr = kind == OR || owned.any { nodes[it].hasOr }))
                }
            }
            val n = nodes[id]
            // Prove only intrinsic falsity. Leave a satisfiable read footprint untouched.
            if (proveExtremes && n.hasNegative && n.hasPositive) {
                if (kind == AND && n.hasOr && falseProofs.getOrPut(id) { proveFalse(id) }) return FALSE
                if (kind == OR && n.hasAnd) {
                    val dual = negate(id)
                    if (dual == FALSE || falseProofs.getOrPut(dual) { proveFalse(dual) }) return TRUE
                }
            }
            return id
        }

        /** De Morgan changes node tags/signs; it never distributes children or builds clauses. */
        private fun negate(root: Int): Int {
            val todo = ArrayDeque<Pair<Int, Boolean>>().also { it.add(root to false) }
            while (todo.isNotEmpty()) {
                checkpoint()
                val (id, finish) = todo.removeLast()
                if (id in duals) continue
                val n = nodes[id]
                if (!finish && n.kind != LEAF) {
                    todo.add(id to true)
                    n.children.asReversed().forEach { if (it !in duals) todo.add(it to false) }
                    continue
                }
                val dual = if (n.kind == LEAF) leaf(atoms[n.variable], !n.negative)
                    else junction(if (n.kind == AND) OR else AND,
                        n.children.map { checkNotNull(duals[it]) }, proveExtremes = false)
                duals[id] = dual
                if (dual > TRUE) duals.putIfAbsent(dual, id)
            }
            return checkNotNull(duals[root])
        }

        /** Monotone three-valued evaluation plus forced AND/single-remaining-OR propagation. */
        private fun proveFalse(root: Int): Boolean {
            val reachable = ArrayList<Int>()
            val seen = HashSet<Int>()
            val parents = HashMap<Int, MutableList<Int>>()
            val todo = ArrayDeque<Int>().also { it.add(root) }
            while (todo.isNotEmpty()) {
                checkpoint()
                val id = todo.removeLast()
                if (!seen.add(id)) continue
                reachable.add(id)
                for (child in nodes[id].children) {
                    parents.getOrPut(child) { ArrayList() }.add(id)
                    todo.add(child)
                }
            }
            // -1 unknown, 0 false, 1 true. No interpretation is assigned to an unknown atom.
            val values = HashMap<Int, Int>()
            val assignments = HashMap<Int, Boolean>()
            val forced = HashSet<Int>()
            val changed = ArrayDeque<Int>()
            val force = ArrayDeque<Int>().also { it.add(root) }
            for (id in reachable) if (id <= TRUE) values[id] = id
            fun value(id: Int): Int = values[id] ?: -1
            fun update(id: Int): Boolean {
                val n = nodes[id]
                val next = when (n.kind) {
                    FALSE -> FALSE
                    TRUE -> TRUE
                    LEAF -> assignments[n.variable]?.let { if (it != n.negative) TRUE else FALSE } ?: -1
                    AND -> if (n.children.any { value(it) == FALSE }) FALSE
                        else if (n.children.all { value(it) == TRUE }) TRUE else -1
                    OR -> if (n.children.any { value(it) == TRUE }) TRUE
                        else if (n.children.all { value(it) == FALSE }) FALSE else -1
                    else -> error("node kind")
                }
                if (next < 0 || value(id) == next) return false
                values[id] = next
                changed.add(id)
                if (id in forced) force.add(id)
                return true
            }
            while (force.isNotEmpty() || changed.isNotEmpty()) {
                checkpoint()
                if (force.isNotEmpty()) {
                    val id = force.removeFirst()
                    forced.add(id)
                    if (value(id) == FALSE) return true
                    if (value(id) == TRUE) continue
                    val n = nodes[id]
                    when (n.kind) {
                        LEAF -> {
                            val required = !n.negative
                            val old = assignments.putIfAbsent(n.variable, required)
                            if (old != null && old != required) return true
                            for (leafId in leafIds[n.variable]) if (leafId >= 0 && leafId in seen) update(leafId)
                        }
                        AND -> n.children.forEach { if (it !in forced) force.add(it) }
                        OR -> {
                            val unknown = n.children.filter { value(it) != FALSE }
                            if (unknown.isEmpty()) return true
                            if (unknown.size == 1 && unknown[0] !in forced) force.add(unknown[0])
                        }
                    }
                } else {
                    val id = changed.removeFirst()
                    for (parent in parents[id].orEmpty()) {
                        update(parent)
                        if (parent in forced) force.add(parent)
                    }
                }
            }
            return false
        }

        private fun materialize(root: Int): CommonCondition<A> {
            val results = HashMap<Int, CommonCondition<A>>()
            results[FALSE] = mkFalse()
            results[TRUE] = mkTrue()
            val todo = ArrayDeque<Pair<Int, Boolean>>().also { it.add(root to false) }
            while (todo.isNotEmpty()) {
                checkpoint()
                val (id, finish) = todo.removeLast()
                if (id in results) continue
                val n = nodes[id]
                if (!finish && n.kind != LEAF) {
                    todo.add(id to true)
                    n.children.asReversed().forEach { if (it !in results) todo.add(it to false) }
                    continue
                }
                results[id] = if (n.kind == LEAF) {
                    val atom = CommonCondition.Atom(atoms[n.variable])
                    if (n.negative) CommonCondition.Not(atom) else atom
                } else {
                    val owned = Collections.unmodifiableList(n.children.map { checkNotNull(results[it]) })
                    if (n.kind == AND) CommonCondition.And(owned) else CommonCondition.Or(owned)
                }
            }
            return checkNotNull(results[root])
        }
    }
}
