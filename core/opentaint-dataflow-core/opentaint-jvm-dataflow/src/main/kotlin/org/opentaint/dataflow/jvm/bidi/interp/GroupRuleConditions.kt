package org.opentaint.dataflow.jvm.bidi.interp

import org.opentaint.dataflow.bidi.ap.MarkMode
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.MarkTable
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.interp.RulePredicate
import org.opentaint.dataflow.util.Cancellation

/** The new-core provider selects bound GROUP literals. Legacy rules stay FULL.
 * Scratch nodes are discarded after compilation; the form owns one positive DAG. */
internal class GroupRuleConditions(private val catalog: MarkTable, private val cancellation: Cancellation) {
    private data class Node(val kind: RulePredicate.Kind, val children: List<Int> = emptyList(), val leaf: Pattern? = null)
    private val nodes = arrayListOf(Node(RulePredicate.Kind.FALSE), Node(RulePredicate.Kind.TRUE))
    private val canonical = hashMapOf(nodes[0] to 0, nodes[1] to 1)

    fun project(pattern: Pattern): Pattern {
        val mark = pattern.fact.mark as MarkSlot.Concrete
        return pattern.copy(fact = pattern.fact.copy(mark = MarkSlot.Concrete(catalog.runtimeAtom(mark.mark, MarkMode.GROUP))))
    }

    private fun intern(node: Node): Int {
        cancellation.checkpoint()
        return canonical.getOrPut(node) { nodes.size.also { nodes += node } }
    }

    private fun junction(kind: RulePredicate.Kind, children: List<Int>): Int {
        val and = kind == RulePredicate.Kind.AND
        val neutral = if (and) 1 else 0
        val absorbing = if (and) 0 else 1
        val live = linkedSetOf<Int>()
        for (child in children) {
            cancellation.checkpoint()
            if (child == absorbing) return absorbing
            if (child == neutral) continue
            if (nodes[child].kind == kind) live.addAll(nodes[child].children) else live += child
        }
        if (live.isEmpty()) return neutral
        if (live.size == 1) return live.first()
        val dual = if (and) RulePredicate.Kind.OR else RulePredicate.Kind.AND
        // Positive absorption uses node equality and child-set inclusion only.
        // It does not distribute connectives or build condition cubes.
        val retained = live.filter { candidate ->
            cancellation.checkpoint()
            val node = nodes[candidate]
            if (node.kind != dual) true else !live.any { other ->
                other != candidate && if (nodes[other].kind == dual)
                    node.children.containsAll(nodes[other].children) &&
                        (node.children.size > nodes[other].children.size || other < candidate)
                else other in node.children
            }
        }
        return when (retained.size) {
            0 -> neutral
            1 -> retained.single()
            else -> intern(Node(kind, retained))
        }
    }

    private fun selectedNodes(full: RulePredicate): IntArray {
        check(catalog.isOwnershipCensusComplete) { "Finish ownership before compiling GROUP conditions" }
        val selected = IntArray(full.nodeCount)
        for (node in 0 until full.nodeCount) {
            cancellation.checkpoint()
            selected[node] = when (val kind = full.kind(node)) {
                RulePredicate.Kind.FALSE -> 0
                RulePredicate.Kind.TRUE -> 1
                RulePredicate.Kind.LEAF -> intern(Node(kind, leaf = project(full.leaves[full.leafOrdinal(node)])))
                RulePredicate.Kind.AND, RulePredicate.Kind.OR -> {
                    val children = buildList { full.childrenOf(node) { add(selected[it]) } }
                    junction(kind, children)
                }
            }
        }
        return selected
    }

    private fun build(root: Int): RulePredicate {
        val builder = RulePredicate.Builder(cancellation, catalog)
        val built = IntArray(nodes.size)
        for ((index, node) in nodes.withIndex()) built[index] = when (node.kind) {
            RulePredicate.Kind.FALSE -> builder.falseNode
            RulePredicate.Kind.TRUE -> builder.trueNode
            RulePredicate.Kind.LEAF -> builder.leaf(checkNotNull(node.leaf))
            RulePredicate.Kind.AND -> builder.and(node.children.map { built[it] })
            RulePredicate.Kind.OR -> builder.or(node.children.map { built[it] })
        }
        return builder.build(built[root])
    }

    fun select(full: RulePredicate): RulePredicate {
        val selected = selectedNodes(full)
        return build(selected[full.root])
    }

    private data class Admission(val root: Int, val empty: Int, val unary: Map<Int, Int>, val multi: Int)

    /** Keep the existing fixed-target path when aliases reduce its Boolean guard
     * to one leaf. This is compile-time AP compatibility metadata, not full proof
     * storage. The maps name original singleton leaves, never proof products. */
    fun multiAdmission(full: RulePredicate): RulePredicate? {
        val selected = selectedNodes(full)
        val info = arrayOfNulls<Admission>(full.nodeCount)
        fun and(vararg children: Int) = junction(RulePredicate.Kind.AND, children.toList())
        fun or(children: List<Int>) = junction(RulePredicate.Kind.OR, children)
        fun combine(a: Admission, b: Admission): Admission {
            val unary = linkedMapOf<Int, Int>()
            for (leaf in a.unary.keys + b.unary.keys) {
                val left = a.unary[leaf] ?: 0
                val right = b.unary[leaf] ?: 0
                val guard = or(listOf(and(left, or(listOf(b.empty, right))), and(a.empty, right)))
                if (guard != 0) unary[leaf] = guard
            }
            val right = b.unary.entries.toList()
            val prefix = IntArray(right.size + 1)
            val suffix = IntArray(right.size + 1)
            for (i in right.indices) prefix[i + 1] = or(listOf(prefix[i], right[i].value))
            for (i in right.indices.reversed()) suffix[i] = or(listOf(right[i].value, suffix[i + 1]))
            val positions = right.mapIndexed { i, entry -> entry.key to i }.toMap()
            val distinct = a.unary.map { (leaf, guard) ->
                cancellation.checkpoint()
                val at = positions[leaf]
                val other = if (at == null) prefix.last() else or(listOf(prefix[at], suffix[at + 1]))
                and(guard, other)
            }
            val multi = or(listOf(and(a.multi, b.root), and(a.root, b.multi)) + distinct)
            return Admission(and(a.root, b.root), and(a.empty, b.empty), unary, multi)
        }
        for (node in 0 until full.nodeCount) {
            cancellation.checkpoint()
            info[node] = when (full.kind(node)) {
                RulePredicate.Kind.FALSE -> Admission(0, 0, emptyMap(), 0)
                RulePredicate.Kind.TRUE -> Admission(1, 1, emptyMap(), 0)
                RulePredicate.Kind.LEAF -> Admission(selected[node], 0,
                    mapOf(full.leafOrdinal(node) to selected[node]), 0)
                RulePredicate.Kind.OR -> {
                    val children = buildList { full.childrenOf(node) { add(checkNotNull(info[it])) } }
                    val singleton = linkedMapOf<Int, MutableList<Int>>()
                    for (child in children) for ((leaf, guard) in child.unary)
                        singleton.getOrPut(leaf) { arrayListOf() } += guard
                    Admission(selected[node], or(children.map { it.empty }),
                        singleton.mapValues { or(it.value) }, or(children.map { it.multi }))
                }
                RulePredicate.Kind.AND -> {
                    var accumulated = Admission(1, 1, emptyMap(), 0)
                    full.childrenOf(node) { accumulated = combine(accumulated, checkNotNull(info[it])) }
                    accumulated
                }
            }
        }
        val root = checkNotNull(info[full.root]).multi
        return if (root == 0) null else build(root)
    }
}
