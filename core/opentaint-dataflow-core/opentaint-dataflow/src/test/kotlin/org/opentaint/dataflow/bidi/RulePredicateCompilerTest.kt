package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.RulePredicate
import kotlin.random.Random
import kotlin.test.*

class RulePredicateCompilerTest {
    private val fixture = StoreFixtures()
    private fun pattern(i: Int) = fixture.pattern(base = AccessPathBase.Argument(i), mark = MarkSlot.Concrete(fixture.mark("M$i")))

    private sealed interface Expr {
        data class Leaf(val index: Int) : Expr
        data class And(val args: List<Expr>) : Expr
        data class Or(val args: List<Expr>) : Expr
        data class Constant(val value: Boolean) : Expr
    }

    private fun compile(expr: Expr): RulePredicate {
        val builder = RulePredicate.Builder(fixture.cancellation)
        fun bind(e: Expr): Int = when (e) {
            is Expr.Leaf -> builder.leaf(pattern(e.index))
            is Expr.And -> builder.and(e.args.map(::bind))
            is Expr.Or -> builder.or(e.args.map(::bind))
            is Expr.Constant -> if (e.value) builder.trueNode else builder.falseNode
        }
        return builder.build(bind(expr))
    }

    /** Deliberately small test-only expansion, independent of the production metadata algorithm. */
    private fun oracle(e: Expr): Set<Set<Int>> = when (e) {
        is Expr.Leaf -> setOf(setOf(e.index))
        is Expr.Constant -> if (e.value) setOf(emptySet()) else emptySet()
        is Expr.Or -> e.args.flatMapTo(HashSet()) { oracle(it) }
        is Expr.And -> e.args.fold(setOf(emptySet())) { products, child ->
            products.flatMapTo(HashSet()) { a -> oracle(child).map { b -> a + b } }
        }
    }

    /** Apply the approved duplicate-operand fold before the independent test-only expansion. */
    private fun fold(e: Expr): Expr = when (e) {
        is Expr.Leaf, is Expr.Constant -> e
        is Expr.And -> e.args.map(::fold).distinct().let { args ->
            if (args.any { it == Expr.Constant(false) }) Expr.Constant(false)
            else args.filterNot { it == Expr.Constant(true) }.let { live ->
                when (live.size) { 0 -> Expr.Constant(true); 1 -> live[0]; else -> Expr.And(live) }
            }
        }
        is Expr.Or -> e.args.map(::fold).distinct().let { args ->
            if (args.any { it == Expr.Constant(true) }) Expr.Constant(true)
            else args.filterNot { it == Expr.Constant(false) }.let { live ->
                when (live.size) { 0 -> Expr.Constant(false); 1 -> live[0]; else -> Expr.Or(live) }
            }
        }
    }

    @Test fun `unary and multi metadata match distinct requirements in small cube oracle`() {
        val random = Random(7165)
        fun generate(depth: Int): Expr {
            if (depth == 0 || random.nextInt(4) == 0) return Expr.Leaf(random.nextInt(4))
            val args = List(random.nextInt(1, 4)) { generate(depth - 1) }
            return if (random.nextBoolean()) Expr.And(args) else Expr.Or(args)
        }
        repeat(2000) {
            val expr = generate(4)
            val expected = oracle(fold(expr))
            val predicate = compile(expr)
            for (leaf in predicate.leaves.indices) {
                val key = (predicate.leaves[leaf].fact.base as AccessPathBase.Argument).idx
                assertEquals(expected.any { it == setOf(key) }, predicate.isUnary(leaf), "unary $key in $expr")
                assertEquals(expected.any { key in it && it.size >= 2 }, predicate.isMulti(leaf), "multi $key in $expr")
            }
        }
    }

    @Test fun `base and mark indexes select every path once while FLOW keeps every positive mark`(): Unit {
        val arg0 = AccessPathBase.Argument(0)
        val arg1 = AccessPathBase.Argument(1)
        val t = fixture.mark("T")
        val u = fixture.mark("U")
        val absent = fixture.mark("absent")
        val field = fixture.field("index-field")
        val suppliedPath = arrayListOf(field)
        val inputs = listOf(
            fixture.pattern(base = arg0, mark = MarkSlot.Concrete(t)),
            fixture.pattern(base = arg0, path = suppliedPath, mark = MarkSlot.Concrete(t)),
            fixture.pattern(base = arg0, mark = MarkSlot.Concrete(u)),
            fixture.pattern(base = arg1, mark = MarkSlot.Concrete(t)),
        )
        val builder = RulePredicate.Builder(fixture.cancellation)
        val root = builder.or(inputs.map(builder::leaf))
        val predicate = builder.build(root)
        suppliedPath.clear()
        fun selected(base: AccessPathBase, mark: org.opentaint.dataflow.bidi.ap.TaintMark?) =
            ArrayList<Pair<Int, org.opentaint.dataflow.bidi.ap.Pattern>>().also { out ->
                predicate.matchingLeaves(base, mark) { ordinal, pattern -> out += ordinal to pattern }
            }
        val expected = inputs.map { it.copy(fact = it.fact.copy(path = if (it === inputs[1]) listOf(field) else emptyList())) }
        assertEquals(listOf(0 to expected[0], 1 to expected[1]), selected(arg0, t))
        assertEquals(listOf(2 to expected[2]), selected(arg0, u))
        assertEquals(listOf(3 to expected[3]), selected(arg1, t))
        assertEquals(expected.take(3).mapIndexed { i, pattern -> i to pattern }, selected(arg0, null))
        assertEquals(listOf(3 to expected[3]), selected(arg1, null))
        assertTrue(selected(arg0, absent).isEmpty())
        assertTrue(selected(AccessPathBase.Return, t).isEmpty())
        assertTrue(selected(AccessPathBase.Return, null).isEmpty())
        assertFails { (predicate.leaves as MutableList).clear() }
        assertFails { (predicate.leaves[1].fact.path as MutableList).clear() }
    }

    @Test fun `AND of many OR pairs compiles in expression size without potential proofs`() {
        val builder = RulePredicate.Builder(fixture.cancellation)
        val root = builder.and(List(64) { pair ->
            builder.or(listOf(builder.leaf(pattern(pair * 2)), builder.leaf(pattern(pair * 2 + 1))))
        })
        val predicate = builder.build(root)
        assertEquals(193, predicate.nodeCount)
        assertEquals(192, predicate.childCount)
        assertEquals(128, predicate.leaves.size)
        assertTrue(predicate.leaves.indices.all { !predicate.isUnary(it) && predicate.isMulti(it) })
    }

    @Test fun `false branches have no active reads and TRUE has no mark reads`() {
        val builder = RulePredicate.Builder(fixture.cancellation)
        val dead = builder.and(listOf(builder.leaf(pattern(1)), builder.falseNode))
        val live = builder.leaf(pattern(0))
        val predicate = builder.build(builder.or(listOf(dead, live)))
        assertEquals(listOf(pattern(0)), predicate.leaves)
        assertEquals(1, predicate.nodeCount)
        assertTrue(builder.build(dead).isFalse)
        assertTrue(builder.build(dead).leaves.isEmpty())
        assertTrue(builder.build(builder.or(listOf(live, builder.trueNode))).leaves.isEmpty())
    }

    @Test fun `repeated requirements share leaf nodes and retain both transfer classes`() {
        val a = Expr.Leaf(0)
        val predicate = compile(Expr.Or(listOf(a, Expr.And(listOf(a, Expr.Leaf(1))))))
        val aIndex = predicate.leaves.indexOf(pattern(0))
        val bIndex = predicate.leaves.indexOf(pattern(1))
        assertTrue(predicate.isUnary(aIndex))
        assertTrue(predicate.isMulti(aIndex))
        assertFalse(predicate.isUnary(bIndex))
        assertTrue(predicate.isMulti(bIndex))
        val parents = ArrayList<Int>()
        predicate.parentsOf(predicate.leafNode(aIndex), parents::add)
        assertEquals(2, parents.size)
        val repeated = compile(Expr.And(listOf(a, a)))
        assertEquals(1, repeated.nodeCount)
        assertFalse(repeated.isMulti(0))
        val choice = Expr.Or(listOf(a, Expr.Leaf(1)))
        val duplicateChoice = compile(Expr.And(listOf(choice, choice)))
        assertEquals(oracle(fold(Expr.And(listOf(choice, choice)))), setOf(setOf(0), setOf(1)))
        assertTrue(duplicateChoice.leaves.indices.all { duplicateChoice.isUnary(it) && !duplicateChoice.isMulti(it) })
    }

    @Test fun `compiler owns leaf paths and rebuilds deterministic topology`() {
        val path = arrayListOf(fixture.field("field"))
        val builder = RulePredicate.Builder(fixture.cancellation)
        val predicate = builder.build(builder.leaf(fixture.pattern(path = path)))
        path.clear()
        assertEquals(1, predicate.leaves.single().fact.path.size)
        assertFails { (predicate.leaves.single().fact.path as MutableList).clear() }
        val expression = Expr.And(listOf(Expr.Or(listOf(Expr.Leaf(1), Expr.Leaf(2))), Expr.Leaf(0)))
        val a = compile(expression)
        val b = compile(expression)
        assertEquals(a.leaves, b.leaves)
        for (node in 0 until a.nodeCount) {
            assertEquals(a.kind(node), b.kind(node))
            assertEquals(a.leafOrdinal(node), b.leafOrdinal(node))
            val ac = ArrayList<Int>(); val bc = ArrayList<Int>()
            a.childrenOf(node, ac::add); b.childrenOf(node, bc::add)
            assertEquals(ac, bc)
        }
    }

    @Test fun `builder and metadata traversal handle deeply nested nontrivial DAG`() {
        val builder = RulePredicate.Builder(fixture.cancellation)
        val a = builder.leaf(pattern(0)); val b = builder.leaf(pattern(1))
        var root = a
        repeat(10000) { i -> root = if (i % 2 == 0) builder.or(listOf(root, b)) else builder.and(listOf(root, a)) }
        val predicate = builder.build(root)
        assertEquals(10002, predicate.nodeCount)
        assertTrue(predicate.isUnary(0))
        assertTrue(predicate.isMulti(0))
    }
}
