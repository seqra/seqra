package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.fixtures.testApManager

import org.opentaint.dataflow.ap.ifds.*
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The oracle evaluates the concrete path first, then the mark policy on element-only paths. */
class TypeFilterTest {
    private val cancellation = Cancellation()
    private val m = testApManager(cancellation)
    private val ops = ApOps(m)
    private val x = AccessPathBase.Argument(0)
    private val fieldF = FieldAccessor("Box", "f", "Box")
    private val fieldG = FieldAccessor("Box", "g", "Box")
    private val fieldH = FieldAccessor("Box", "fresh", "Box")
    private val static = ClassStaticAccessor("Box")
    private val f = m.accessors.index(fieldF)
    private val g = m.accessors.index(fieldG)
    private val h = m.accessors.index(fieldH)
    private val cls = m.accessors.index(static)
    private val e = m.accessors.index(ElementAccessor)
    private val t = m.marks.mark("T")
    private val u = m.marks.mark("U")
    private val fresh = m.marks.mark("Fresh")
    private val empty = ExclusionSet.Empty
    private fun ex(vararg ids: Int) = ExclusionSet.of(ids)
    private fun marks(vararg ids: Int) = MarkSet(ids)
    private fun filter(step: (Accessor) -> FactTypeChecker.FilterResult) = object : FactTypeChecker.FactApFilter {
        override fun check(accessor: Accessor) = step(accessor)
    }
    private class Prefix {
        var flag = false
        val exact = sortedSetOf<Int>()
        val any = sortedSetOf<Int>()
        val children = sortedMapOf<Int, Prefix>()
    }
    private fun at(root: Prefix, path: List<Int>): Prefix {
        var result = root
        for (accessor in path) result = result.children.getOrPut(accessor) { Prefix() }
        return result
    }
    private fun <P : TrieLeaf> build(root: Prefix, alg: LeafAlgebra<P>, leaf: (Prefix) -> P): TrieNode<P> =
        alg.node(leaf(root), root.children.keys.toIntArray(), root.children.values.map { build(it, alg, leaf) }.toTypedArray())
    private fun flow(vararg paths: List<Int>, exclusion: ExclusionSet = empty,
                     layer: Layer = Layer.NORMAL): FlowTree {
        val root = Prefix()
        for (path in paths) at(root, path).flag = true
        return m.flowTree(x, layer, exclusion, marks(u.id),
            build(root, m.flowAlg) { if (it.flag) FlowLeaf.LEAF else FlowLeaf.NONE })
    }
    private fun c(path: List<Int>, tail: Tail, mark: TaintMark = t) =
        Conclusion(PathFact(x, path, tail, MarkSlot.Concrete(mark)), empty, false)
    private fun taint(vararg leaves: Conclusion, exclusion: ExclusionSet = empty,
                      layer: Layer = Layer.NORMAL): TaintTree {
        val root = Prefix()
        for (leaf in leaves) {
            val prefix = at(root, leaf.fact.path)
            val id = (leaf.fact.mark as MarkSlot.Concrete).mark.id
            if (leaf.fact.tail == Tail.EXACT) prefix.exact += id else prefix.any += id
        }
        return m.taintTree(x, layer, build(root, m.taintAlg) {
            m.taintLeaves(MarkSet(it.exact.toIntArray()), MarkSet(it.any.toIntArray()))
        }, exclusion)
    }
    private fun <P : TrieLeaf> walk(root: TrieNode<P>, action: (List<Int>, P) -> Unit) {
        val pending = ArrayDeque<Pair<List<Int>, TrieNode<P>>>()
        pending.addLast(emptyList<Int>() to root)
        while (pending.isNotEmpty()) {
            val (path, node) = pending.removeLast()
            if (!node.leaf.isEmpty) action(path, node.leaf)
            val accessors = node.accessors
            val children = node.children
            if (accessors != null && children != null) for (i in accessors.indices)
                pending.addLast((path + accessors[i]) to children[i])
        }
    }
    private fun decode(value: Facts?): Set<Conclusion> = buildSet {
        if (value == null) return@buildSet
        val demand = value.layer == Layer.DEMAND
        fun put(path: List<Int>, tail: Tail, mark: MarkSlot, exclusion: ExclusionSet = empty) {
            add(Conclusion(PathFact(value.base, path, tail, mark), exclusion, demand))
        }
        when (value) {
            is Reach -> put(emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO))
            is FlowTree -> walk(value.root) { path, _ ->
                put(path, if (demand) Tail.ANY else Tail.STAR, MarkSlot.Star(value.markExclusion), value.exclusion)
            }
            is TaintTree -> walk(value.root) { path, leaf ->
                for (id in leaf.exact.ids) put(path, Tail.EXACT, MarkSlot.Concrete(TaintMark(id)))
                for (id in leaf.any.ids) put(path, if (demand) Tail.ANY else Tail.ANY_TAINT,
                    MarkSlot.Concrete(TaintMark(id)), value.exclusion)
            }
        }
    }
    private fun pathPasses(path: List<Int>, initial: FactTypeChecker.FactApFilter): Boolean {
        var state = initial
        for (accessor in path) when (val result = state.check(m.accessors.accessor(accessor))) {
            FactTypeChecker.FilterResult.Accept -> return true
            FactTypeChecker.FilterResult.Reject -> return false
            is FactTypeChecker.FilterResult.FilterNext -> state = result.filter
        }
        return true
    }
    private fun expected(input: Facts, type: TypeFilter): Set<Conclusion> = decode(input).filter { leaf ->
        val mark = leaf.fact.mark
        val policy = type.markPolicy
        pathPasses(leaf.fact.path, type.may) &&
            (mark !is MarkSlot.Concrete || policy == null ||
                leaf.fact.path.any { it != e } ||
                policy.keeps(mark.mark, leaf.fact.path.size))
    }.toSet()

    @Test
    fun `zero and accepted flags preserve their canonical values`() {
        val accept = TypeFilter(FactTypeChecker.AlwaysAcceptFilter)
        assertSame(Reach.NORMAL, ops.filter(Reach.NORMAL, accept))
        assertSame(Reach.DEMAND, ops.filter(Reach.DEMAND, accept))
        val flag = m.flowTree(AccessPathBase.Argument(0), Layer.NORMAL, ExclusionSet.Empty,
            MarkSet.EMPTY, FlowAlgebra.LEAF_NODE)
        assertSame(flag, ops.filter(flag, accept))
    }

    @Test
    fun `path filtering preserves complete tails E marks and layers`() {
        val below = filter { if (it == fieldG) FactTypeChecker.FilterResult.Reject else FactTypeChecker.FilterResult.Accept }
        val may = filter {
            when (it) {
                fieldF, ElementAccessor -> FactTypeChecker.FilterResult.FilterNext(below)
                fieldG -> FactTypeChecker.FilterResult.Reject
                else -> FactTypeChecker.FilterResult.Accept
            }
        }
        val type = TypeFilter(may)
        for (layer in Layer.entries) {
            val values = listOf<Facts>(
                flow(emptyList(), listOf(f), listOf(f, g), listOf(f, h), listOf(g), listOf(cls, g),
                    listOf(e), listOf(e, g), exclusion = ex(g), layer = layer),
                taint(c(emptyList(), Tail.EXACT), c(emptyList(), Tail.ANY_TAINT, u),
                    c(listOf(f), Tail.EXACT, u), c(listOf(f), Tail.ANY_TAINT),
                    c(listOf(f, g), Tail.EXACT), c(listOf(f, h), Tail.ANY_TAINT, u),
                    c(listOf(g), Tail.ANY_TAINT), c(listOf(cls, g), Tail.EXACT),
                    c(listOf(e, g), Tail.ANY_TAINT), exclusion = ex(h), layer = layer))
            for (value in values) {
                val actual = requireNotNull(ops.filter(value, type))
                assertEquals(expected(value, type), decode(actual))
                assertEquals(value.layer, actual.layer)
                assertEquals(value.groupKey, actual.groupKey)
            }
        }
        assertNull(ops.filter(flow(listOf(g)), type))
        assertEquals(setOf(c(emptyList(), Tail.EXACT)),
            decode(ops.filter(taint(c(emptyList(), Tail.EXACT), c(listOf(g), Tail.ANY_TAINT), exclusion = ex(f)), type)))
    }

    @Test
    fun `mark policy acts only at the root and the element chain for both payload tails`() {
        val paths = listOf(emptyList(), listOf(e), listOf(e, e), listOf(f), listOf(f, e),
            listOf(cls), listOf(cls, e), listOf(e, f), listOf(e, cls))
        val leaves = paths.flatMap { path -> listOf(c(path, Tail.EXACT), c(path, Tail.EXACT, u),
            c(path, Tail.ANY_TAINT), c(path, Tail.ANY_TAINT, u)) }
        val policy = MarkPolicy { mark, level ->
            when (level) { 0 -> mark != t; 1 -> mark != u; else -> false }
        }
        val type = TypeFilter(FactTypeChecker.AlwaysAcceptFilter, policy)
        for (layer in Layer.entries) {
            val input = taint(*leaves.toTypedArray(), exclusion = ex(g), layer = layer)
            val actual = requireNotNull(ops.filter(input, type))
            assertEquals(expected(input, type), decode(actual))
            assertEquals(input.groupKey, actual.groupKey)
            val pathsOffChain = paths.filter { path -> path.any { it != e } }
            assertEquals(decode(input).filter { it.fact.path in pathsOffChain }.toSet(),
                decode(actual).filter { it.fact.path in pathsOffChain }.toSet())
        }
        val reject = TypeFilter(FactTypeChecker.AlwaysAcceptFilter, MarkPolicy { _, _ -> false })
        assertNull(ops.filter(taint(c(emptyList(), Tail.EXACT), c(listOf(e), Tail.ANY_TAINT)), reject))
        val lastMust = taint(c(emptyList(), Tail.ANY_TAINT), c(listOf(f), Tail.EXACT, u), exclusion = ex(g))
        val exactOnly = ops.filter(lastMust, reject) as TaintTree
        assertEquals(setOf(c(listOf(f), Tail.EXACT, u)), decode(exactOnly))
        assertEquals(empty, exactOnly.exclusion)
    }

    @Test
    fun `path filtering finishes before mark policy and zero and flow never run the policy`() {
        val events = ArrayList<String>()
        val may = filter {
            events += "path"
            if (it == ElementAccessor) FactTypeChecker.FilterResult.Reject else FactTypeChecker.FilterResult.Accept
        }
        val levels = ArrayList<Int>()
        val policy = MarkPolicy { _, level -> events += "mark"; levels += level; true }
        val input = taint(c(emptyList(), Tail.EXACT), c(listOf(e), Tail.EXACT, u), c(listOf(f), Tail.EXACT))
        val actual = requireNotNull(ops.filter(input, TypeFilter(may, policy)))
        assertEquals(setOf(c(emptyList(), Tail.EXACT), c(listOf(f), Tail.EXACT)), decode(actual))
        assertEquals(listOf(0), levels)
        assertTrue(events.indexOf("mark") > events.indexOfLast { it == "path" })
        val forbidden = TypeFilter(filter { error("zero has no path checks") }, MarkPolicy { _, _ -> error("no concrete marks") })
        assertSame(Reach.NORMAL, ops.filter(Reach.NORMAL, forbidden))
        assertSame(Reach.DEMAND, ops.filter(Reach.DEMAND, forbidden))
        val flags = flow(emptyList(), listOf(f))
        assertSame(flags, ops.filter(flags, TypeFilter(FactTypeChecker.AlwaysAcceptFilter, forbidden.markPolicy)))
    }

    @Test
    fun `conjunction retains both FilterNext states and combines policies at every element level`() {
        val afterA = filter { if (it == fieldG) FactTypeChecker.FilterResult.Reject else FactTypeChecker.FilterResult.Accept }
        val afterB = filter { if (it == fieldH) FactTypeChecker.FilterResult.Reject else FactTypeChecker.FilterResult.Accept }
        val a = TypeFilter(filter {
            if (it == fieldF || it == ElementAccessor) FactTypeChecker.FilterResult.FilterNext(afterA)
            else FactTypeChecker.FilterResult.Accept
        }, MarkPolicy { mark, k -> mark != t || k > 0 })
        val b = TypeFilter(filter {
            if (it == fieldF || it == ElementAccessor) FactTypeChecker.FilterResult.FilterNext(afterB)
            else if (it == fieldG) FactTypeChecker.FilterResult.Reject else FactTypeChecker.FilterResult.Accept
        }, MarkPolicy { mark, k -> mark != u || k != 1 })
        val paths = listOf(emptyList(), listOf(f), listOf(f, f), listOf(f, g), listOf(f, h), listOf(g),
            listOf(h), listOf(e), listOf(e, g), listOf(e, h), listOf(cls, f))
        val leaves = paths.flatMap { listOf(c(it, Tail.EXACT), c(it, Tail.ANY_TAINT, u)) }
        val input = taint(*leaves.toTypedArray(), exclusion = ex(h))
        val both = a.and(b)
        val want = decode(input).filter { it in expected(input, a) && it in expected(input, b) }.toSet()
        assertEquals(want, decode(ops.filter(input, both)))
        assertEquals(want, decode(ops.filter(input, b.and(a))))
        val noPolicy = TypeFilter(FactTypeChecker.AlwaysAcceptFilter)
        assertSame(a.markPolicy, a.and(noPolicy).markPolicy)
        assertSame(a.markPolicy, noPolicy.and(a).markPolicy)
    }

    @Test
    fun `shared subtree filtering is keyed by both node and filter identity`() {
        val suffix = m.flowAlg.node(FlowLeaf.LEAF, intArrayOf(g), arrayOf(FlowAlgebra.LEAF_NODE))
        val root = m.flowAlg.node(FlowLeaf.NONE, intArrayOf(f, h), arrayOf(suffix, suffix))
        val input = m.flowTree(x, Layer.NORMAL, ex(g), marks(u.id), root)
        val reject = filter { FactTypeChecker.FilterResult.Reject }
        val accept = filter { FactTypeChecker.FilterResult.Accept }
        val may = filter {
            FactTypeChecker.FilterResult.FilterNext(if (it == fieldF) reject else accept)
        }
        val actual = ops.filter(input, TypeFilter(may)) as FlowTree
        assertEquals(setOf(listOf(f), listOf(h), listOf(h, g)), decode(actual).map { it.fact.path }.toSet())
        assertNull(actual.root.child(f)?.child(g))
        assertSame(suffix, actual.root.child(h))
        assertSame(FlowAlgebra.LEAF_NODE, actual.root.child(h)?.child(g))
    }

    @Test
    fun `shared removed subtree is evaluated once including a cached null result`() {
        val suffix = m.flowAlg.node(FlowLeaf.NONE, intArrayOf(g), arrayOf(FlowAlgebra.LEAF_NODE))
        val root = m.flowAlg.node(FlowLeaf.NONE, intArrayOf(f, h), arrayOf(suffix, suffix))
        val input = m.flowTree(x, Layer.NORMAL, empty, MarkSet.EMPTY, root)
        var roots = 0
        var shared = 0
        val childState = filter { shared++; FactTypeChecker.FilterResult.Reject }
        val rootState = filter { roots++; FactTypeChecker.FilterResult.FilterNext(childState) }
        assertNull(ops.filter(input, TypeFilter(rootState)))
        assertEquals(2, roots)
        assertEquals(1, shared)
    }

    @Test
    fun `unchanged filters and policies share the original value and offchain subtrees`() {
        val input = taint(c(emptyList(), Tail.EXACT), c(listOf(e), Tail.ANY_TAINT, u),
            c(listOf(f, e), Tail.EXACT), c(listOf(cls), Tail.ANY_TAINT), exclusion = ex(g))
        val keep = TypeFilter(FactTypeChecker.AlwaysAcceptFilter, MarkPolicy { _, _ -> true })
        assertSame(input, ops.filter(input, keep))
        val changed = ops.filter(input, TypeFilter(FactTypeChecker.AlwaysAcceptFilter,
            MarkPolicy { mark, level -> mark != t || level != 0 })) as TaintTree
        assertSame(input.root.child(f), changed.root.child(f))
        assertSame(input.root.child(cls), changed.root.child(cls))
        assertSame(input.root.child(e), changed.root.child(e))
        assertFalse(changed.root.leaf.exact.contains(t))
    }

    @Test
    fun `deep path filtering and element policies use bounded stack and exact element depth`() {
        val depth = 32768
        var flag = FlowAlgebra.LEAF_NODE
        repeat(depth) { flag = m.flowAlg.node(FlowLeaf.NONE, intArrayOf(f), arrayOf(flag)) }
        val input = m.flowTree(x, Layer.NORMAL, empty, MarkSet.EMPTY, flag)
        var checks = 0
        val next = object : FactTypeChecker.FactApFilter {
            override fun check(accessor: Accessor): FactTypeChecker.FilterResult {
                checks++
                return FactTypeChecker.FilterResult.FilterNext(this)
            }
        }
        assertSame(input, ops.filter(input, TypeFilter(next)))
        assertEquals(depth, checks)
        val leaf = m.taintLeaves(m.markSetOf(t), MarkSet.EMPTY)
        var chain = m.taintAlg.leafNode(leaf)
        repeat(depth) { chain = m.taintAlg.node(leaf, intArrayOf(e), arrayOf(chain)) }
        val elementInput = m.taintTree(x, Layer.NORMAL, chain)
        val seen = ArrayList<Int>()
        val result = ops.filter(elementInput, TypeFilter(FactTypeChecker.AlwaysAcceptFilter,
            MarkPolicy { _, level -> seen += level; level == depth })) as TaintTree
        assertEquals((0..depth).toList(), seen)
        assertTrue(result.root.leaf.isEmpty)
        val tip = requireNotNull(result.root.walk(IntArray(depth) { e }))
        assertEquals(leaf, tip.leaf)
        assertNull(tip.accessors)
    }

    @Test
    fun `filter and policy walks observe cancellation during deep operations`() {
        val depth = 32768
        var flag = FlowAlgebra.LEAF_NODE
        repeat(depth) { flag = m.flowAlg.node(FlowLeaf.NONE, intArrayOf(f), arrayOf(flag)) }
        val input = m.flowTree(x, Layer.NORMAL, empty, MarkSet.EMPTY, flag)
        var checks = 0
        val next = object : FactTypeChecker.FactApFilter {
            override fun check(accessor: Accessor): FactTypeChecker.FilterResult {
                if (++checks == 128) cancellation.cancel()
                return FactTypeChecker.FilterResult.FilterNext(this)
            }
        }
        assertFailsWith<Cancellation.Cancelled> { ops.filter(input, TypeFilter(next)) }
        cancellation.activate()
        val leaf = m.taintLeaves(m.markSetOf(t), MarkSet.EMPTY)
        var chain = m.taintAlg.leafNode(leaf)
        repeat(depth) { chain = m.taintAlg.node(leaf, intArrayOf(e), arrayOf(chain)) }
        val elementInput = m.taintTree(x, Layer.NORMAL, chain)
        assertFailsWith<Cancellation.Cancelled> {
            ops.filter(elementInput, TypeFilter(FactTypeChecker.AlwaysAcceptFilter,
                MarkPolicy { _, level -> if (level == 128) cancellation.cancel(); true }))
        }
        cancellation.activate()
        cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { ops.filter(input, TypeFilter(FactTypeChecker.AlwaysAcceptFilter)) }
    }
}
