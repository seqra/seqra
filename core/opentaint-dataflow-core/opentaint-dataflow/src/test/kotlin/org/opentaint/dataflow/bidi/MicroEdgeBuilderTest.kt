package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Expected forms use literal PathFacts and exclusions; transfer vectors state complete annotated conclusions. */
class MicroEdgeBuilderTest {
    private val fixture = StoreFixtures()
    private val m = fixture.manager
    private val x: AccessPathBase = AccessPathBase.Argument(0)
    private val y: AccessPathBase = AccessPathBase.Return
    private val z: AccessPathBase = AccessPathBase.Argument(1)
    private val f = fixture.field("f")
    private val g = fixture.field("g")
    private val h = fixture.field("h")
    private val cls = m.accessors.index(ClassStaticAccessor("Box"))
    private val otherClass = m.accessors.index(ClassStaticAccessor("Other"))
    private val element = m.accessors.index(ElementAccessor)
    private val t = fixture.mark("T")
    private val u = fixture.mark("U")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val empty = ExclusionSet.Empty
    private val at = Place(fixture.inst(1), statementEdge = true, sources = false)

    private fun side(base: AccessPathBase, path: List<Int> = emptyList()) = PathFact(base, path, Tail.STAR, MarkSlot.STAR)
    private fun move(from: AccessPathBase, to: AccessPathBase, fp: List<Int> = emptyList(), tp: List<Int> = emptyList()) =
        PathEdge(side(from, fp), side(to, tp), empty)
    private fun keep(base: AccessPathBase, path: List<Int> = emptyList(), e: ExclusionSet = empty) =
        PathEdge(side(base, path), side(base, path), e)
    private fun c(base: AccessPathBase, path: List<Int> = emptyList(), tail: Tail = Tail.EXACT,
                  mark: MarkSlot = mt, e: ExclusionSet = empty, demand: Boolean = false) =
        Conclusion(PathFact(base, path, tail, mark), e, demand)

    private fun checkForm(actual: StatementSummary, touched: Set<AccessPathBase>, edges: List<PathEdge>) {
        assertEquals(touched, actual.touched)
        assertEquals(edges, actual.edges.map { it.edge })
        assertTrue(actual.edges.all { it.edge === it.forward && !it.conjunctive })
        for (me in actual.edges) {
            val e = me.edge
            assertTrue(e.from.base in touched)
            assertEquals(Tail.STAR, e.from.tail)
            assertEquals(Tail.STAR, e.to.tail)
            assertEquals(MarkSlot.STAR, e.from.mark)
            assertEquals(MarkSlot.STAR, e.to.mark)
            assertEquals(empty, e.fromExclusion)
            assertEquals(empty, e.toExclusion)
            assertTrue(e.exclusion == empty || e.from == e.to)
        }
    }

    private fun effect(s: StatementSummary, input: Conclusion, run1: Boolean = false,
                       premise: Pattern = if (input.fact.base == AccessPathBase.Zero) ZERO_PATTERN
                           else Pattern(side(input.fact.base), empty)): Pair<Set<Conclusion>, List<RequestKind>> {
        val requests = ArrayList<RequestKind>()
        val algebra = ReferenceAlgebra(ApMode(run1, Direction.FORWARD, Int.MAX_VALUE),
            request = { _, request -> requests += request }, manager = m)
        val result = FormsReference(fixture.ops, algebra).apply(FormsReference.Mode.STATEMENT, s, setOf(premise), input, at)
        return result.toSet() to requests
    }

    @Test
    fun `move kill and self move have explicit root and nested target forms`(): Unit {
        data class Row(val to: AccessPathBase, val from: AccessPathBase?, val path: List<Int>,
                       val touched: Set<AccessPathBase>, val expected: List<PathEdge>)
        val constant = AccessPathBase.Constant("String", "literal")
        val rows = listOf(
            Row(y, x, emptyList(), setOf(y, x), listOf(keep(x), move(x, y))),
            Row(y, null, emptyList(), setOf(y), emptyList()),
            Row(x, x, emptyList(), setOf(x), listOf(keep(x))),
            Row(x, x, listOf(f, g), setOf(x), listOf(keep(x), move(x, x, tp = listOf(f, g)))),
            Row(y, x, listOf(f, element), setOf(y, x), listOf(keep(x), move(x, y, tp = listOf(f, element)))),
            Row(y, constant, emptyList(), setOf(y, constant), listOf(keep(constant), move(constant, y))),
            Row(AccessPathBase.Exception, x, emptyList(), setOf(AccessPathBase.Exception, x),
                listOf(keep(x), move(x, AccessPathBase.Exception))),
        )
        for (row in rows) {
            val builder = MicroEdgeBuilder()
            builder.move(row.to, row.from, row.path)
            checkForm(builder.build(), row.touched, row.expected)
        }
    }

    @Test
    fun `read and self read use only whole source identity and the exact full read path`(): Unit {
        data class Row(val to: AccessPathBase, val base: AccessPathBase, val path: List<Int>, val expected: List<PathEdge>)
        val rows = listOf(
            Row(y, x, listOf(f), listOf(keep(x), move(x, y, fp = listOf(f)))),
            Row(y, x, listOf(element), listOf(keep(x), move(x, y, fp = listOf(element)))),
            Row(x, x, listOf(f, g), listOf(move(x, x, fp = listOf(f, g)))),
            Row(y, STATIC, listOf(cls, f), listOf(keep(STATIC), move(STATIC, y, fp = listOf(cls, f)))),
            Row(x, x, emptyList(), listOf(keep(x))),
        )
        for (row in rows) {
            val builder = MicroEdgeBuilder()
            builder.read(row.to, row.base, row.path)
            val form = builder.build()
            checkForm(form, setOf(row.to, row.base), row.expected)
            assertTrue(form.edges.all { it.edge.exclusion == empty })
        }
    }

    @Test
    fun `strong weak root and self writes contain the stated prefix keeps and value transfers`(): Unit {
        data class Row(val base: AccessPathBase, val path: List<Int>, val weak: Boolean,
                       val values: List<AccessPathBase>, val touched: Set<AccessPathBase>, val expected: List<PathEdge>)
        val rows = listOf(
            Row(x, listOf(f), false, listOf(y), setOf(x, y),
                listOf(keep(x, e = ExclusionSet.of(f)), keep(y), move(y, x, tp = listOf(f)))),
            Row(x, listOf(f, g), false, listOf(y), setOf(x, y),
                listOf(keep(x, e = ExclusionSet.of(f)), keep(x, listOf(f), ExclusionSet.of(g)), keep(y),
                    move(y, x, tp = listOf(f, g)))),
            Row(x, listOf(element), true, listOf(y, z), setOf(x, y, z),
                listOf(keep(x), keep(y), move(y, x, tp = listOf(element)), keep(z), move(z, x, tp = listOf(element)))),
            Row(x, emptyList(), false, listOf(y), setOf(x, y), listOf(keep(y), move(y, x))),
            Row(x, emptyList(), false, emptyList(), setOf(x), emptyList()),
            Row(x, emptyList(), true, emptyList(), setOf(x), listOf(keep(x))),
            Row(x, listOf(f), false, listOf(x), setOf(x),
                listOf(keep(x, e = ExclusionSet.of(f)), move(x, x, tp = listOf(f)))),
            Row(STATIC, listOf(cls, f), false, listOf(y), setOf(STATIC, y),
                listOf(keep(STATIC, e = ExclusionSet.of(cls)), keep(STATIC, listOf(cls), ExclusionSet.of(f)),
                    keep(y), move(y, STATIC, tp = listOf(cls, f)))),
        )
        for (row in rows) {
            val builder = MicroEdgeBuilder()
            builder.write(row.base, row.path, row.weak, row.values, emptyList())
            checkForm(builder.build(), row.touched, row.expected)
        }
    }

    @Test
    fun `aliases add only value transfers and leave target bases untouched in the forward form`(): Unit {
        val builder = MicroEdgeBuilder()
        builder.write(x, listOf(f, element), weak = false, values = listOf(y, z), aliasPaths = listOf(
            x to listOf(h), AccessPathBase.Exception to listOf(g), AccessPathBase.Exception to listOf(g)))
        val form = builder.build()
        checkForm(form, setOf(x, y, z), listOf(
            keep(x, e = ExclusionSet.of(f)), keep(x, listOf(f), ExclusionSet.of(element)),
            keep(y), move(y, x, tp = listOf(f, element)), keep(z), move(z, x, tp = listOf(f, element)),
            move(y, AccessPathBase.Exception, tp = listOf(g, f, element)),
            move(z, AccessPathBase.Exception, tp = listOf(g, f, element)),
        ))
        assertFalse(AccessPathBase.Exception in form.touched)
        val oldAlias = c(AccessPathBase.Exception, listOf(g, f, element))
        assertEquals(setOf(oldAlias) to emptyList(), effect(form, oldAlias))
        assertEquals(listOf(AccessPathBase.Exception), form.reversed().edges.filter { it.isIdentity }
            .filter { it.edge.from.base == AccessPathBase.Exception }.map { it.edge.from.base })
    }

    @Test
    fun `alias paths stay complete beyond a run limit and retain every repeated accessor`(): Unit {
        val prefix = List(32767) { if (it % 2 == 0) f else element }
        val builder = MicroEdgeBuilder()
        builder.write(x, listOf(g), weak = false, values = listOf(y), aliasPaths = listOf(z to prefix))
        val alias = builder.build().edges.single { it.edge.to.base == z }.edge
        assertEquals(32768, alias.to.path.size)
        assertEquals(prefix + g, alias.to.path)
        assertEquals(Tail.STAR, alias.to.tail)
        assertEquals(empty, alias.exclusion)
    }

    @Test
    fun `helper generated paths own every mutable move read write and alias argument`(): Unit {
        val toPath = arrayListOf(f, element)
        val readPath = arrayListOf(g, h)
        val writePath = arrayListOf(cls, f)
        val aliasPath = arrayListOf(g, element)
        val builder = MicroEdgeBuilder()
        builder.move(y, x, toPath)
        builder.read(z, x, readPath)
        builder.write(STATIC, writePath, weak = false, values = listOf(y), aliasPaths = emptyList())
        builder.write(x, listOf(h), weak = false, values = listOf(y), aliasPaths = listOf(z to aliasPath))
        toPath.clear(); readPath.clear(); writePath.clear(); aliasPath.clear()
        val form = builder.build()
        assertTrue(move(x, y, tp = listOf(f, element)) in form.edges.map { it.edge })
        assertTrue(move(x, z, fp = listOf(g, h)) in form.edges.map { it.edge })
        assertTrue(move(y, STATIC, tp = listOf(cls, f)) in form.edges.map { it.edge })
        assertTrue(move(y, z, tp = listOf(g, element, h)) in form.edges.map { it.edge })
        for (path in form.edges.flatMap { listOf(it.edge.from.path, it.edge.to.path) }.filter { it.isNotEmpty() })
            assertFails { (path as MutableList<Int>)[0] = h }
    }

    @Test
    fun `direct edge insertion preserves supplied identity and does not implicitly touch its premise`(): Unit {
        val supplied = move(x, y, fp = listOf(f), tp = listOf(g))
        val builder = MicroEdgeBuilder()
        builder.edge(supplied)
        val untouched = builder.build()
        assertTrue(untouched.touched.isEmpty())
        assertSame(supplied, untouched.edges.single().edge)
        assertSame(supplied, untouched.edges.single().forward)
        builder.touch(x)
        val touched = builder.build()
        checkForm(touched, setOf(x), listOf(supplied))
        assertTrue(untouched.touched.isEmpty())
    }

    @Test
    fun `edge deduplication preserves first insertion order while conjunctions deduplicate by object identity`(): Unit {
        val first = move(x, y)
        val equal = move(x, y)
        val second = move(z, y)
        val literals = listOf(Pattern(PathFact(x, emptyList(), Tail.EXACT, mt), empty),
            Pattern(PathFact(z, emptyList(), Tail.EXACT, mu), empty))
        val target = PathFact(y, emptyList(), Tail.ANY_TAINT, mt)
        val joint = ConjunctiveEdge(literals, target)
        val distinct = ConjunctiveEdge(literals, target)
        val builder = MicroEdgeBuilder()
        builder.edge(first); builder.edge(second); builder.edge(equal)
        builder.conjunction(joint); builder.conjunction(joint); builder.conjunction(distinct)
        val form = builder.build()
        assertEquals(listOf(first, second), form.edges.map { it.edge })
        assertSame(first, form.edges[0].edge)
        assertEquals(listOf(joint, distinct), form.conjunctions)
        assertSame(joint, form.conjunctions[0])
        assertSame(distinct, form.conjunctions[1])
    }

    @Test
    fun `stage closure touches every plain and conjunctive endpoint while gen drops non edge state`(): Unit {
        val supplied = move(x, y)
        val joint = ConjunctiveEdge(listOf(
            Pattern(PathFact(x, emptyList(), Tail.EXACT, mt), empty),
            Pattern(PathFact(y, emptyList(), Tail.ANY, mu), empty)), PathFact(z, emptyList(), Tail.EXACT, mt))
        val filter = TypeFilter(FactTypeChecker.AlwaysAcceptFilter)
        val builder = MicroEdgeBuilder()
        builder.touch(AccessPathBase.Exception)
        builder.edge(supplied); builder.conjunction(joint)
        builder.operandFilter(x, filter); builder.resultFilter(y, filter)
        val stage = builder.buildStage()
        assertEquals(setOf(AccessPathBase.Exception, x, y, z), stage.touched)
        assertSame(supplied, stage.edges.single().edge)
        assertSame(joint, stage.conjunctions.single())
        assertSame(filter, stage.typeFilters[x])
        assertSame(filter, stage.resultFilters[y])
        assertEquals(stage.touched, builder.build().touched)
        val gen = builder.buildGen()
        assertTrue(gen.touched.isEmpty())
        assertTrue(gen.conjunctions.isEmpty())
        assertTrue(gen.typeFilters.isEmpty())
        assertTrue(gen.resultFilters.isEmpty())
        assertSame(supplied, gen.edges.single().edge)
        assertSame(supplied, gen.edges.single().forward)
        assertEquals(stage.touched, builder.build().touched)
    }

    @Test
    fun `keep zero adds only its exact concrete keep and touch alone records a kill`(): Unit {
        val builder = MicroEdgeBuilder()
        builder.touch(y); builder.touch(y)
        builder.keepZero(); builder.keepZero()
        val form = builder.build()
        val zero = PathFact(AccessPathBase.Zero, emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO))
        assertEquals(setOf(y, AccessPathBase.Zero), form.touched)
        assertEquals(listOf(PathEdge(zero, zero, empty)), form.edges.map { it.edge })
        assertSame(ZERO_KEEP, form.edges.single().edge)
        assertEquals(setOf(Conclusion(zero, empty, false)) to emptyList(), effect(form, Conclusion(zero, empty, false)))
        assertEquals(emptySet<Conclusion>() to emptyList(), effect(form, c(y)))
        assertEquals(setOf(c(x)) to emptyList(), effect(form, c(x)))
    }

    @Test
    fun `repeated convenience operations combine binary and self inputs without duplicate edges`(): Unit {
        val builder = MicroEdgeBuilder()
        builder.move(x, x); builder.move(x, z); builder.move(x, z); builder.keep(z)
        checkForm(builder.build(), setOf(x, z), listOf(keep(x), keep(z), move(z, x)))
        val source = c(z, listOf(f, g))
        assertEquals(setOf(source, c(x, listOf(f, g))) to emptyList(), effect(builder.build(), source))
        val oldSelf = c(x, listOf(f))
        assertEquals(setOf(oldSelf) to emptyList(), effect(builder.build(), oldSelf))
    }

    @Test
    fun `builder snapshots remain stable after later operations and all public containers are unmodifiable`(): Unit {
        val builder = MicroEdgeBuilder()
        val filter = TypeFilter(FactTypeChecker.AlwaysAcceptFilter)
        builder.move(y, x); builder.operandFilter(x, filter); builder.resultFilter(y, filter)
        val before = builder.build()
        val original = before.edges.toList()
        builder.touch(z); builder.move(y, z); builder.resultFilter(z, filter)
        assertEquals(setOf(y, x), before.touched)
        assertEquals(original, before.edges)
        assertEquals(setOf(x), before.typeFilters.keys)
        assertEquals(setOf(y), before.resultFilters.keys)
        assertTrue(z in builder.build().touched)
        assertTrue(builder.build().edges.size > before.edges.size)
        assertFails { (before.touched as MutableSet<AccessPathBase>).clear() }
        assertFails { (before.edges as MutableList<MicroEdge>).clear() }
        assertFails { (before.conjunctions as MutableList<ConjunctiveEdge>).clear() }
        assertFails { (before.typeFilters as MutableMap<AccessPathBase, TypeFilter>).clear() }
        assertFails { (before.resultFilters as MutableMap<AccessPathBase, TypeFilter>).clear() }
    }

    @Test
    fun `operand and result filters combine independently in arrival order including mark policies`(): Unit {
        val events = ArrayList<String>()
        fun filter(name: String) = TypeFilter(object : FactTypeChecker.FactApFilter {
            override fun check(accessor: Accessor): FactTypeChecker.FilterResult {
                events += name
                return FactTypeChecker.FilterResult.Accept
            }
        }, MarkPolicy { mark, depth -> events += "$name-policy:$depth"; mark == t })
        val op1 = filter("operand1")
        val op2 = filter("operand2")
        val res1 = filter("result1")
        val res2 = filter("result2")
        val builder = MicroEdgeBuilder()
        builder.move(y, x)
        builder.operandFilter(x, null); builder.resultFilter(y, null)
        builder.operandFilter(x, op1); builder.operandFilter(x, op2)
        builder.resultFilter(y, res1); builder.resultFilter(y, res2)
        val input = c(x, listOf(element))
        assertEquals(setOf(input, c(y, listOf(element))) to emptyList(), effect(builder.build(), input))
        assertEquals(listOf("operand1", "operand2", "operand1-policy:1", "operand2-policy:1",
            "result1", "result2", "result1-policy:1", "result2-policy:1"), events)
        events.clear()
        assertEquals(emptySet<Conclusion>() to emptyList(), effect(builder.build(), input.copy(fact = input.fact.copy(mark = mu))))
        assertEquals(listOf("operand1", "operand2", "operand1-policy:1"), events)
        val untouchedFilter = MicroEdgeBuilder()
        untouchedFilter.operandFilter(z, op1); untouchedFilter.resultFilter(z, res1)
        assertTrue(untouchedFilter.build().touched.isEmpty())
        assertSame(op1, untouchedFilter.build().typeFilters[z])
        assertSame(res1, untouchedFilter.build().resultFilters[z])
    }

    @Test
    fun `self read discards unrelated locations without a refinement and preserves full continuation geometry`(): Unit {
        val builder = MicroEdgeBuilder()
        builder.read(x, x, listOf(f))
        val form = builder.build()
        val rows = listOf(
            c(x) to emptySet(),
            c(x, listOf(g)) to emptySet(),
            c(x, listOf(f, g, f)) to setOf(c(x, listOf(g, f))),
            c(x, tail = Tail.STAR, mark = MarkSlot.STAR, e = ExclusionSet.of(g)) to
                setOf(c(x, tail = Tail.ANY, mark = MarkSlot.STAR, demand = true)),
            c(x, tail = Tail.ANY_TAINT, e = ExclusionSet.of(g)) to setOf(c(x, tail = Tail.ANY_TAINT)),
            c(x, tail = Tail.ANY_TAINT, e = ExclusionSet.of(f)) to emptySet(),
        )
        for ((input, expected) in rows) assertEquals(expected to emptyList(), effect(form, input), "$input")
    }

    @Test
    fun `self strong write excludes the old field and independently prefixes its value without feedback`(): Unit {
        val builder = MicroEdgeBuilder()
        builder.write(x, listOf(f), weak = false, values = listOf(x), aliasPaths = emptyList())
        val form = builder.build()
        val rows = listOf(
            c(x, listOf(f, g)) to setOf(c(x, listOf(f, f, g))),
            c(x, listOf(g)) to setOf(c(x, listOf(g)), c(x, listOf(f, g))),
            c(x, tail = Tail.STAR, mark = MarkSlot.STAR, e = ExclusionSet.of(g)) to setOf(
                c(x, tail = Tail.STAR, mark = MarkSlot.STAR, e = ExclusionSet.of(intArrayOf(f, g))),
                c(x, listOf(f), Tail.STAR, MarkSlot.STAR, ExclusionSet.of(g))),
            c(x, tail = Tail.ANY_TAINT, e = ExclusionSet.of(g)) to setOf(
                c(x, tail = Tail.ANY_TAINT, e = ExclusionSet.of(intArrayOf(f, g))),
                c(x, listOf(f), Tail.ANY_TAINT, e = ExclusionSet.of(g))),
        )
        for ((input, expected) in rows) assertEquals(expected to emptyList(), effect(form, input), "$input")
    }

    @Test
    fun `root strong writes kill all old target paths while weak writes retain them`(): Unit {
        val strong = MicroEdgeBuilder().apply { write(x, emptyList(), false, listOf(y), emptyList()) }.build()
        val weak = MicroEdgeBuilder().apply { write(x, emptyList(), true, listOf(y), emptyList()) }.build()
        for (input in listOf(c(x), c(x, listOf(f, g)), c(x, tail = Tail.ANY_TAINT, e = ExclusionSet.of(f)))) {
            assertEquals(emptySet<Conclusion>() to emptyList(), effect(strong, input))
            assertEquals(setOf(input) to emptyList(), effect(weak, input))
        }
        val value = c(y, listOf(f, g))
        assertEquals(setOf(value, c(x, listOf(f, g))) to emptyList(), effect(strong, value))
        assertEquals(setOf(value, c(x, listOf(f, g))) to emptyList(), effect(weak, value))
    }

    @Test
    fun `static class field writes retain siblings and request only the class from a root identity`(): Unit {
        val builder = MicroEdgeBuilder()
        builder.write(STATIC, listOf(cls, f), weak = false, values = listOf(y), aliasPaths = emptyList())
        val form = builder.build()
        for (input in listOf(c(STATIC, listOf(otherClass, f)), c(STATIC, listOf(cls, g))))
            assertEquals(setOf(input) to emptyList(), effect(form, input))
        assertEquals(emptySet<Conclusion>() to emptyList(), effect(form, c(STATIC, listOf(cls, f, g))))
        val value = c(y, listOf(g))
        assertEquals(setOf(value, c(STATIC, listOf(cls, f, g))) to emptyList(), effect(form, value))
        val root = c(STATIC, tail = Tail.STAR, mark = MarkSlot.STAR)
        assertEquals(setOf(c(STATIC, tail = Tail.STAR, mark = MarkSlot.STAR, e = ExclusionSet.of(cls))) to
            listOf(RequestKind.Position(m.path(listOf(cls))!!)), effect(form, root, run1 = true))
        val classInput = c(STATIC, listOf(cls), Tail.STAR, MarkSlot.STAR)
        assertEquals(setOf(c(STATIC, listOf(cls), Tail.STAR, MarkSlot.STAR, ExclusionSet.of(f))) to emptyList(),
            effect(form, classInput, run1 = true, premise = Pattern(side(STATIC, listOf(cls)), empty)))
    }
}
