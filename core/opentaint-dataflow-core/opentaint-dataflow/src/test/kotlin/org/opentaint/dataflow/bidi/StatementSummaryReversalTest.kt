package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Literal geometry and annotated reference outcomes supply expectations; no micro-edge builder is used. */
class StatementSummaryReversalTest {
    private val fixture = StoreFixtures()
    private val m = fixture.manager
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val z = AccessPathBase.Argument(1)
    private val f = fixture.field("f")
    private val g = fixture.field("g")
    private val h = fixture.field("h")
    private val cls = m.accessors.index(ClassStaticAccessor("Box"))
    private val element = m.accessors.index(ElementAccessor)
    private val t = fixture.mark("T")
    private val u = fixture.mark("U")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val empty = ExclusionSet.Empty

    private fun fact(base: AccessPathBase, path: List<Int> = emptyList(), tail: Tail = Tail.STAR,
                     mark: MarkSlot = MarkSlot.STAR) = PathFact(base, path, tail, mark)
    private fun edge(from: PathFact, to: PathFact, e: ExclusionSet = empty) = PathEdge(from, to, e)
    private fun summary(touched: Set<AccessPathBase>, edges: List<MicroEdge>, conjunctions: List<ConjunctiveEdge> = emptyList()) =
        StatementSummary(touched, edges, conjunctions, emptyMap())
    private fun apply(input: Conclusion, edge: MicroEdge): EdgeOutcome =
        concat(input, edge.edge, edgeDemand = edge.may, restricted = true)

    @Test
    fun `zero and star helpers keep full path geometry and strong writes exclude only each next accessor`(): Unit {
        val zero = fact(AccessPathBase.Zero, tail = Tail.EXACT, mark = MarkSlot.Concrete(TaintMark.ZERO))
        assertEquals(zero, ZERO_FACT)
        assertEquals(Pattern(zero, empty), ZERO_PATTERN)
        assertEquals(edge(zero, zero), ZERO_KEEP)
        assertEquals(fact(x, listOf(f, element)), starSide(x, listOf(f, element)))
        assertEquals(edge(fact(x, listOf(f)), fact(x, listOf(f)), ExclusionSet.of(g)),
            keepEdge(x, listOf(f), ExclusionSet.of(g)))
        assertEquals(edge(fact(x, listOf(f, element)), fact(y, listOf(g, h))),
            starEdge(x, listOf(f, element), y, listOf(g, h)))
        assertTrue(strongKeep(x, emptyList()).isEmpty())
        val path = listOf(cls, f, element, f)
        val expected = listOf(
            edge(fact(STATIC), fact(STATIC), ExclusionSet.of(cls)),
            edge(fact(STATIC, listOf(cls)), fact(STATIC, listOf(cls)), ExclusionSet.of(f)),
            edge(fact(STATIC, listOf(cls, f)), fact(STATIC, listOf(cls, f)), ExclusionSet.of(element)),
            edge(fact(STATIC, listOf(cls, f, element)), fact(STATIC, listOf(cls, f, element)), ExclusionSet.of(f)),
        )
        assertEquals(expected, strongKeep(STATIC, path))
        assertTrue(expected.all { revEdge(it) == it })
    }

    @Test
    fun `strong keep geometry retains siblings and removes exactly the written subtree`(): Unit {
        val keeps = strongKeep(x, listOf(f, g))
        val cases = listOf(
            emptyList<Int>() to true,
            listOf(f) to true,
            listOf(h) to true,
            listOf(f, h, g) to true,
            listOf(f, g) to false,
            listOf(f, g, h) to false,
        )
        for ((path, survives) in cases) {
            val input = Conclusion(fact(x, path, Tail.EXACT, mt), empty, false)
            val actual = keeps.map { concat(input, it, restricted = true) }.filterIsInstance<EdgeOutcome.Fact>()
            assertEquals(if (survives) setOf(input) else emptySet(), actual.map { it.conclusion }.toSet(), "$path")
        }
    }

    @Test
    fun `helper paths are owned and unmodifiable after caller mutation`(): Unit {
        val path = arrayListOf(cls, f, element)
        val side = starSide(STATIC, path)
        val keep = keepEdge(STATIC, path)
        val move = starEdge(STATIC, path, STATIC, path)
        val strong = strongKeep(STATIC, path)
        path.clear()
        assertEquals(listOf(cls, f, element), side.path)
        assertEquals(listOf(cls, f, element), keep.from.path)
        assertEquals(listOf(cls, f, element), keep.to.path)
        assertEquals(listOf(cls, f, element), move.from.path)
        assertEquals(listOf(cls, f, element), move.to.path)
        assertEquals(listOf(emptyList(), listOf(cls), listOf(cls, f)), strong.map { it.from.path })
        for (owned in listOf(side.path, keep.from.path, keep.to.path, move.from.path, move.to.path, strong.last().from.path))
            assertFails { (owned as MutableList<Int>)[0] = h }
    }

    @Test
    fun `micro flags read the retained forward edge and distinguish must sources may rules and conjunction branches`(): Unit {
        val source = edge(ZERO_FACT, fact(y, listOf(g), Tail.ANY_TAINT, mt))
        val conditional = edge(fact(x, listOf(f), Tail.ANY, mu), fact(y, listOf(g), Tail.ANY_TAINT, mt))
        val may = edge(fact(x, listOf(f), Tail.EXACT, mu), fact(y, listOf(g), Tail.ANY, mt))
        val exact = edge(fact(x, listOf(f), Tail.EXACT, mu), fact(y, listOf(g), Tail.EXACT, mt))
        assertTrue(MicroEdge.of(source).isSource)
        assertFalse(MicroEdge.of(source).may)
        assertFalse(MicroEdge.of(conditional).isSource)
        assertFalse(MicroEdge.of(conditional).may)
        assertTrue(MicroEdge.of(may).may)
        assertFalse(MicroEdge.of(exact).may)
        val reversedSource = MicroEdge(revEdge(source)!!, source)
        val reversedMay = MicroEdge(revEdge(may)!!, may)
        val branch = MicroEdge(revEdge(exact)!!, exact, conjunctive = true)
        assertSame(source, reversedSource.forward)
        assertTrue(reversedSource.isSource)
        assertFalse(reversedSource.may)
        assertTrue(reversedMay.may)
        assertTrue(branch.may)
        assertTrue(branch.conjunctive)
        assertFalse(MicroEdge.of(ZERO_KEEP).isSource)
        assertTrue(MicroEdge.of(ZERO_KEEP).isIdentity)
        assertTrue(identityEdge(x).isIdentity)
        assertFalse(MicroEdge.of(keepEdge(x, e = ExclusionSet.of(f))).isIdentity)
        assertFalse(MicroEdge.of(starEdge(x, emptyList(), y, emptyList())).isIdentity)
    }

    @Test
    fun `micro forms reject every must premise and every must side of a reversed form`(): Unit {
        val mustFrom = edge(fact(x, tail = Tail.ANY_TAINT, mark = mt), fact(y, tail = Tail.EXACT, mark = mu))
        assertFails { MicroEdge.of(mustFrom) }
        val forward = edge(fact(x, tail = Tail.EXACT, mark = mu), fact(y, tail = Tail.ANY_TAINT, mark = mt))
        assertSame(forward, MicroEdge.of(forward).edge)
        val changedMustTarget = forward.copy(to = forward.to.copy(path = listOf(g)))
        assertFails { MicroEdge(changedMustTarget, forward) }
        assertFails { MicroEdge(mustFrom, forward) }
        val reversed = MicroEdge(revEdge(forward)!!, forward)
        assertTrue(reversed.edge.from.tail != Tail.ANY_TAINT && reversed.edge.to.tail != Tail.ANY_TAINT)
    }

    @Test
    fun `forward source must and reversed pass may have distinct demand effects`(): Unit {
        val mustSource = edge(ZERO_FACT, fact(y, listOf(g), Tail.ANY_TAINT, mt))
        val normalRequirement = Conclusion(fact(y, listOf(g), Tail.EXACT, mt), empty, false)
        val reversedSource = MicroEdge(revEdge(mustSource)!!, mustSource)
        assertEquals(EdgeOutcome.Fact(Conclusion(ZERO_FACT, empty, false)), apply(normalRequirement, reversedSource))
        val mayPass = edge(fact(x, listOf(f), Tail.EXACT, mu), fact(y, listOf(g), Tail.ANY, mt))
        val reversedMay = MicroEdge(revEdge(mayPass)!!, mayPass)
        assertEquals(EdgeOutcome.Fact(Conclusion(fact(x, listOf(f), Tail.EXACT, mu), empty, true)),
            apply(normalRequirement, reversedMay))
        val forwardSource = MicroEdge.of(mustSource)
        assertEquals(EdgeOutcome.Fact(Conclusion(mustSource.to, empty, false)),
            apply(Conclusion(ZERO_FACT, empty, false), forwardSource))
    }

    @Test
    fun `ordinary reversal retains each forward object and follows every permitted tail row`(): Unit {
        data class Row(val from: Tail, val to: Tail, val reversedFrom: Tail, val reversedTo: Tail)
        val rows = listOf(
            Row(Tail.STAR, Tail.STAR, Tail.STAR, Tail.STAR),
            Row(Tail.STAR, Tail.EXACT, Tail.EXACT, Tail.ANY),
            Row(Tail.STAR, Tail.ANY, Tail.ANY, Tail.ANY),
            Row(Tail.ANY, Tail.STAR, Tail.STAR, Tail.STAR),
            Row(Tail.ANY, Tail.EXACT, Tail.EXACT, Tail.ANY),
            Row(Tail.ANY, Tail.ANY, Tail.ANY, Tail.ANY),
            Row(Tail.ANY, Tail.ANY_TAINT, Tail.ANY, Tail.ANY),
            Row(Tail.EXACT, Tail.EXACT, Tail.EXACT, Tail.EXACT),
            Row(Tail.EXACT, Tail.ANY, Tail.ANY, Tail.EXACT),
            Row(Tail.EXACT, Tail.ANY_TAINT, Tail.ANY, Tail.EXACT),
        )
        val sourcePath = listOf(f, element, f)
        val targetPath = listOf(g, element, h)
        for (row in rows) {
            val abstractTarget = row.to == Tail.STAR
            val targetMark = if (abstractTarget) MarkSlot.STAR else mt
            val exclusion = if (row.from == Tail.STAR || abstractTarget) ExclusionSet.of(h) else empty
            val forwardEdge = edge(fact(x, sourcePath, row.from, mu), fact(y, targetPath, row.to, targetMark), exclusion)
            val forward = MicroEdge.of(forwardEdge)
            val before = summary(setOf(x, y), listOf(forward))
            val back = before.reversed()
            val expected = edge(
                fact(y, targetPath, row.reversedFrom, if (abstractTarget) mu else mt),
                fact(x, sourcePath, row.reversedTo, if (abstractTarget) MarkSlot.STAR else mu),
                if (row.reversedFrom == Tail.STAR || row.reversedTo == Tail.STAR) exclusion else empty,
            )
            assertEquals(listOf(expected), back.edges.map { it.edge }, "$row")
            val reverse = back.edges.single()
            assertSame(forwardEdge, reverse.forward, "$row")
            assertSame(forward, before.edges.single(), "$row")
            assertEquals(row.to == Tail.ANY, reverse.may, "$row")
            assertFalse(reverse.conjunctive, "$row")
            assertEquals(setOf(x, y), back.touched, "$row")
            assertEquals(back.edges, back.edgesOf(y), "$row")
            assertTrue(back.edgesOf(x).isEmpty(), "$row")
            assertTrue(back.conjunctions.isEmpty(), "$row")
            assertTrue(reverse.edge.from.tail != Tail.ANY_TAINT && reverse.edge.to.tail != Tail.ANY_TAINT, "$row")
        }
    }

    @Test
    fun `binding and cleaned abstract keeps reverse without changing mark exclusions or shared field exclusions`(): Unit {
        val excludedMark = MarkSlot.Star(MarkSet(intArrayOf(t.id)))
        val shared = ExclusionSet.of(h)
        val binding = starEdge(STATIC, listOf(cls, f), y, listOf(element, g))
        val clean = edge(fact(x, listOf(f)), fact(y, listOf(g), mark = excludedMark), shared)
        val back = summary(setOf(x, y, STATIC), listOf(MicroEdge.of(binding), MicroEdge.of(clean))).reversed()
        assertEquals(listOf(
            edge(fact(y, listOf(element, g)), fact(STATIC, listOf(cls, f))),
            edge(fact(y, listOf(g)), fact(x, listOf(f), mark = excludedMark), shared),
        ), back.edges.map { it.edge })
        assertSame(binding, back.edges[0].forward)
        assertSame(clean, back.edges[1].forward)
    }

    @Test
    fun `reversal fails loudly when mark or must target exclusions have no ordinary reverse`(): Unit {
        val nonReversibleMark = edge(fact(x), fact(y, tail = Tail.ANY, mark = mt))
        val mustWithShared = edge(fact(x, tail = Tail.EXACT, mark = mu),
            fact(y, tail = Tail.ANY_TAINT, mark = mt), ExclusionSet.of(g))
        val mustWithOwn = PathEdge(fact(x, tail = Tail.EXACT, mark = mu),
            fact(y, tail = Tail.ANY_TAINT, mark = mt), empty, toExclusion = ExclusionSet.of(g))
        for (e in listOf(nonReversibleMark, mustWithShared, mustWithOwn)) {
            val s = summary(setOf(x, y), listOf(MicroEdge.of(e)))
            assertFails("$e") { s.reversed() }
        }
    }

    @Test
    fun `repeated conjunction literals remain independent demand branches for exact and must targets`(): Unit {
        val exactLiteral = Pattern(fact(x, listOf(f, element), Tail.EXACT, mu), empty)
        val anyLiteral = Pattern(fact(z, listOf(g), Tail.ANY, mt), empty)
        for (targetTail in listOf(Tail.EXACT, Tail.ANY_TAINT)) {
            val target = fact(y, listOf(h, f), targetTail, mt)
            val conjunction = ConjunctiveEdge(listOf(exactLiteral, exactLiteral, anyLiteral), target)
            val before = summary(setOf(x, y, z), emptyList(), listOf(conjunction))
            val back = before.reversed()
            val expectedFromTail = if (targetTail == Tail.EXACT) Tail.EXACT else Tail.ANY
            val expectedEdges = listOf(
                edge(fact(y, target.path, expectedFromTail, mt), fact(x, exactLiteral.fact.path, Tail.EXACT, mu)),
                edge(fact(y, target.path, expectedFromTail, mt), fact(x, exactLiteral.fact.path, Tail.EXACT, mu)),
                edge(fact(y, target.path, expectedFromTail, mt), fact(z, anyLiteral.fact.path, Tail.ANY, mt)),
            )
            assertEquals(expectedEdges, back.edges.map { it.edge }, "$targetTail")
            assertNotSame(back.edges[0], back.edges[1])
            assertTrue(back.edges.all { it.conjunctive && it.may })
            assertTrue(back.conjunctions.isEmpty())
            assertSame(conjunction, before.conjunctions.single())
            for ((index, literal) in listOf(exactLiteral, exactLiteral, anyLiteral).withIndex()) {
                val me = back.edges[index]
                assertSame(literal.fact, me.forward.from)
                assertSame(target, me.forward.to)
                assertEquals(edge(literal.fact, target), me.forward)
                val input = Conclusion(target.copy(tail = Tail.EXACT), empty, false)
                assertEquals(EdgeOutcome.Fact(Conclusion(literal.fact, empty, true)), apply(input, me), "$targetTail/$index")
            }
        }
    }

    @Test
    fun `alias targets receive one pass over identity while touched and killed bases remain touched`(): Unit {
        val move = MicroEdge.of(starEdge(x, listOf(f), y, listOf(g)))
        val target = fact(STATIC, listOf(cls, h), Tail.EXACT, mt)
        val conjunction = ConjunctiveEdge(listOf(
            Pattern(fact(x, listOf(f), Tail.EXACT, mt), empty),
            Pattern(fact(z, listOf(g), Tail.EXACT, mu), empty),
        ), target)
        val before = summary(setOf(x, z), listOf(move, move), listOf(conjunction))
        assertEquals(setOf(y, STATIC), before.targets)
        val back = before.reversed()
        assertEquals(setOf(x, z, y, STATIC), back.touched)
        val identityEdges = back.edges.filter { it.isIdentity }
        assertEquals(listOf(y, STATIC), identityEdges.map { it.edge.from.base })
        for (identity in identityEdges) {
            assertEquals(keepEdge(identity.edge.from.base), identity.edge)
            assertSame(identity.edge, identity.forward)
        }
        assertSame(move.forward, back.edges[0].forward)
        assertSame(move.forward, back.edges[1].forward)
        val touchedTarget = summary(setOf(x, y, z), listOf(move)).reversed()
        assertEquals(setOf(x, y, z), touchedTarget.touched)
        assertTrue(touchedTarget.edges.none { it.isIdentity })
        assertEquals(setOf(z), summary(setOf(z), emptyList()).reversed().touched)
    }

    @Test
    fun `operand and result filters are removed together on reversal`(): Unit {
        val operand = TypeFilter(FactTypeChecker.AlwaysAcceptFilter)
        val result = TypeFilter(FactTypeChecker.AlwaysRejectFilter, MarkPolicy { _, _ -> false })
        val move = MicroEdge.of(starEdge(x, listOf(f), x, listOf(g)))
        val before = StatementSummary(setOf(x), listOf(move), emptyList(), mapOf(x to operand), mapOf(x to result))
        assertSame(operand, before.typeFilters[x])
        assertSame(result, before.resultFilters[x])
        val back = before.reversed()
        assertTrue(back.typeFilters.isEmpty())
        assertTrue(back.resultFilters.isEmpty())
        assertSame(move.forward, back.edges.single().forward)
    }

    @Test
    fun `summary snapshots own every collection while preserving supplied form identities`(): Unit {
        val me = MicroEdge.of(starEdge(x, listOf(f), y, listOf(g)))
        val cj = ConjunctiveEdge(listOf(Pattern(fact(x, tail = Tail.EXACT, mark = mt), empty),
            Pattern(fact(z, tail = Tail.EXACT, mark = mu), empty)), fact(y, tail = Tail.EXACT, mark = mt))
        val operand = TypeFilter(FactTypeChecker.AlwaysAcceptFilter)
        val result = TypeFilter(FactTypeChecker.AlwaysRejectFilter)
        val touched = linkedSetOf(x, z)
        val edges = arrayListOf(me)
        val conjunctions = arrayListOf(cj)
        val operands = linkedMapOf<AccessPathBase, TypeFilter>(x to operand)
        val results = linkedMapOf<AccessPathBase, TypeFilter>(y to result)
        val s = StatementSummary(touched, edges, conjunctions, operands, results)
        touched.clear(); edges.clear(); conjunctions.clear(); operands.clear(); results.clear()
        assertEquals(setOf(x, z), s.touched)
        assertSame(me, s.edges.single())
        assertSame(me.edge, s.edges.single().edge)
        assertSame(cj, s.conjunctions.single())
        assertSame(operand, s.typeFilters[x])
        assertSame(result, s.resultFilters[y])
        assertEquals(setOf(y), s.targets)
        assertEquals(listOf(me), s.edgesOf(x))
        assertFails { (s.touched as MutableSet<AccessPathBase>).clear() }
        assertFails { (s.edges as MutableList<MicroEdge>).clear() }
        assertFails { (s.conjunctions as MutableList<ConjunctiveEdge>).clear() }
        assertFails { (s.typeFilters as MutableMap<AccessPathBase, TypeFilter>).clear() }
        assertFails { (s.resultFilters as MutableMap<AccessPathBase, TypeFilter>).clear() }
        assertFails { (s.targets as MutableSet<AccessPathBase>).clear() }
        assertFails { (s.edgesOf(x) as MutableList<MicroEdge>).clear() }
    }

    @Test
    fun `identity augmentation owns base collections and retains all existing forms and filters`(): Unit {
        val me = MicroEdge.of(starEdge(x, emptyList(), y, emptyList()))
        val cj = ConjunctiveEdge(listOf(Pattern(fact(x, tail = Tail.EXACT, mark = mt), empty),
            Pattern(fact(z, tail = Tail.EXACT, mark = mu), empty)), fact(y, tail = Tail.EXACT, mark = mt))
        val operand = TypeFilter(FactTypeChecker.AlwaysAcceptFilter)
        val result = TypeFilter(FactTypeChecker.AlwaysRejectFilter)
        val before = StatementSummary(setOf(x), listOf(me), listOf(cj), mapOf(x to operand), mapOf(y to result))
        assertSame(before, before.withIdentities(emptyList()))
        val bases = arrayListOf(y, z)
        val augmented = before.withIdentities(bases)
        bases.clear()
        assertEquals(setOf(x, y, z), augmented.touched)
        assertEquals(listOf(me.edge, keepEdge(y), keepEdge(z)), augmented.edges.map { it.edge })
        assertSame(me, augmented.edges.first())
        assertSame(cj, augmented.conjunctions.single())
        assertSame(operand, augmented.typeFilters[x])
        assertSame(result, augmented.resultFilters[y])
        assertEquals(setOf(x), before.touched)
        assertEquals(listOf(me), before.edges)
        val identities = StatementSummary.identities(listOf(x, z))
        assertEquals(setOf(x, z), identities.touched)
        assertEquals(listOf(keepEdge(x), keepEdge(z)), identities.edges.map { it.edge })
        assertTrue(identities.edges.all { it.isIdentity && it.edge === it.forward })
        assertTrue(StatementSummary.EMPTY.touched.isEmpty())
        assertTrue(StatementSummary.EMPTY.edges.isEmpty())
    }

    @Test
    fun `full deep helper and reversal paths are retained without a field limit cut`(): Unit {
        val source = List(32768) { if (it % 2 == 0) f else element }
        val target = List(32768) { if (it % 2 == 0) g else h }
        val forward = starEdge(x, source, y, target)
        val back = summary(setOf(x, y), listOf(MicroEdge.of(forward))).reversed().edges.single()
        assertEquals(source, forward.from.path)
        assertEquals(target, forward.to.path)
        assertEquals(target, back.edge.from.path)
        assertEquals(source, back.edge.to.path)
        assertSame(forward, back.forward)
    }

    @Test
    fun `interpreter contract accepts common instructions and calls with ordered exceptional exits`(): Unit {
        val entry = fixture.inst(0)
        val normal = fixture.inst(1)
        val exceptional = fixture.inst(2)
        val rules = RuleStatement.EMPTY
        val exits = listOf(ExitNode(normal, false), ExitNode(exceptional, true))
        val interpreter = object : Interpreter {
            override fun entryNode(method: MethodKey): CommonInst = entry
            override fun exitNodes(method: MethodKey): List<ExitNode> = exits
            override fun entryRules(method: MethodKey): RuleStatement = rules
            override fun exitRules(method: MethodKey, exit: CommonInst): ExitRules = ExitRules.EMPTY
            override fun statementSummary(method: MethodKey, statement: CommonInst): StatementSummary = StatementSummary.EMPTY
            override fun callPlan(caller: MethodKey, statement: CommonInst, call: CommonCallExpr): CallPlan = error("unused")
            override fun isSummaryBase(base: AccessPathBase): Boolean = base == STATIC || base == AccessPathBase.Return
        }
        assertSame(entry, interpreter.entryNode(fixture.method))
        assertSame(rules, interpreter.entryRules(fixture.method))
        assertSame(ExitRules.EMPTY, interpreter.exitRules(fixture.method, normal))
        assertSame(StatementSummary.EMPTY, interpreter.statementSummary(fixture.method, normal))
        assertEquals(listOf(normal, exceptional), interpreter.exitNodes(fixture.method).map { it.node })
        assertEquals(listOf(false, true), interpreter.exitNodes(fixture.method).map { it.exceptional })
        assertTrue(interpreter.isSummaryBase(STATIC))
        assertFalse(interpreter.isSummaryBase(x))
    }
}
