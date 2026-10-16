package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.*
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.ELEMENT_ACCESSOR_IDX
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.ir.api.common.cfg.CommonInst
import kotlin.test.*

class FormsReferenceTest {
    private val fixture = StoreFixtures()
    private val m = fixture.manager
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val f = fixture.field("f")
    private val g = fixture.field("g")
    private val t = fixture.mark("T")
    private val u = fixture.mark("U")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val premise = setOf(fixture.pattern(x, mark = mt))
    private val at = Place(fixture.inst(1), statementEdge = true, sources = true)
    private fun exact(base: AccessPathBase, path: List<Int> = emptyList(), mark: MarkSlot = mt) =
        PathFact(base, path, Tail.EXACT, mark)
    private fun summary(vararg edges: PathEdge, touched: Set<AccessPathBase> = setOf(x)) =
        StatementSummary(touched, edges.map(MicroEdge::of), emptyList(), emptyMap())
    private fun item(c: Conclusion, candidate: Boolean? = true) = PlanItem(premise, c, candidate)
    private fun ref(mode: ApMode = ApMode(false, Direction.FORWARD, 1)) =
        FormsReference(fixture.ops, ReferenceAlgebra(mode, manager = m))

    @Test fun `all three form modes agree with tree transfers on complete leaf identities`(): Unit {
        val modes = listOf(ApMode(true, Direction.FORWARD, 1), ApMode(false, Direction.FORWARD, 2),
            ApMode(false, Direction.BACKWARD, 3))
        val marks = listOf(mt, mu, MarkSlot.STAR, MarkSlot.Star(MarkSet(intArrayOf(t.id))))
        val paths = listOf(emptyList(), listOf(f), listOf(g), listOf(f, g))
        val edges = buildList {
            for (path in listOf(emptyList(), listOf(f))) {
                add(keepEdge(x, path))
                add(keepEdge(x, path, ExclusionSet.of(g)))
                add(starEdge(x, path, y, listOf(g)))
                add(PathEdge(PathFact(x, path, Tail.STAR, mt), exact(y), ExclusionSet.Empty))
                add(PathEdge(exact(x, path), exact(y, listOf(g), mu), ExclusionSet.Empty))
                add(PathEdge(exact(x, path), PathFact(y, listOf(g), Tail.ANY, mu), ExclusionSet.Empty))
            }
            add(ZERO_KEEP)
            add(PathEdge(ZERO_FACT, PathFact(y, listOf(f), Tail.ANY_TAINT, mt), ExclusionSet.Empty))
        }
        var vectors = 0
        for (mode in modes) {
            val requests = ArrayList<RequestKind>()
            val algebra = ReferenceAlgebra(mode, request = { _, r -> requests += r }, manager = m)
            val oracle = FormsReference(fixture.ops, algebra)
            val coreRequests = ArrayList<RequestKind>()
            val tree = FormApplier(object : FactAlgebra<Set<Pattern>, Facts> {
                override fun base(c: Facts) = c.base
                override fun filter(c: Facts, filter: TypeFilter) = fixture.ops.filter(c, filter)
                override fun applyEdge(me: MicroEdge, c: Facts, premise: Set<Pattern>, statementEdge: Boolean,
                                       out: (Set<Pattern>, Facts) -> Unit) {
                    fixture.ops.applyEdge(c, m.premiseOf(premise.map(m::initial)), me.edge, statementEdge, mode,
                        object : ApOut {
                            override fun result(f: Facts) = out(premise, f)
                            override fun markRequest(mark: TaintMark) { coreRequests += RequestKind.Mark(mark) }
                        }, me.may)
                }
                override fun conjunction(cj: ConjunctiveEdge, premise: Set<Pattern>, c: Facts, node: CommonInst,
                                         out: (Set<Pattern>, Facts) -> Unit) = error("No conjunction in this vector")
                override fun allowsSource(node: CommonInst, me: MicroEdge) = true
                override fun sourceHit(node: CommonInst, me: MicroEdge) = Unit
            })
            val inputs = buildList<Facts> {
                add(Reach.NORMAL); add(Reach.DEMAND)
                for (mark in marks) for (path in paths) for (tail in Tail.entries) for (layer in Layer.entries) {
                    if (tail == Tail.EXACT && mark is MarkSlot.Star) continue
                    if (tail == Tail.ANY_TAINT && mark is MarkSlot.Star) continue
                    for (exclusion in if (layer == Layer.NORMAL && (tail == Tail.STAR || tail == Tail.ANY_TAINT))
                        listOf(ExclusionSet.Empty, ExclusionSet.of(g)) else listOf(ExclusionSet.Empty))
                        add(m.factsOf(PathFact(x, path, tail, mark), exclusion, layer))
                }
            }
            for (edge in edges) for (input in inputs) for (formMode in FormsReference.Mode.entries)
                for (touched in listOf(emptySet(), setOf(x, AccessPathBase.Zero))) {
                    if (mode.direction == Direction.BACKWARD && edge.to.tail == Tail.ANY_TAINT) continue
                    if (mode.direction == Direction.BACKWARD && input is TaintTree &&
                        input.layer == Layer.NORMAL && input.root.hasAny) continue
                    val s = summary(edge, touched = touched)
                    val output = ArrayList<Facts>()
                    requests.clear(); coreRequests.clear()
                    val pr = if (input is Reach) setOf(ZERO_PATTERN) else premise
                    when (formMode) {
                        FormsReference.Mode.STATEMENT -> tree.statement(s, pr, input, at, { _, c -> output += c })
                        FormsReference.Mode.STAGE -> tree.stage(s, pr, input, at) { _, c, _ -> output += c }
                        FormsReference.Mode.GEN -> tree.gen(s.edges, pr, input) { _, c -> output += c }
                    }
                    assertTrue(oracle.agrees(formMode, s, at, pr, input, output),
                        "mode=${mode.direction}/${mode.run1} form=$formMode input=${oracle.conclusions(input)} edge=$edge touched=$touched")
                    assertEquals(coreRequests, requests)
                    vectors++
                }
        }
        assertTrue(vectors > 10_000)
    }

    @Test fun `historical reference mark and static position requests retain their original geometry`(): Unit {
        val markRead = MicroEdge.of(PathEdge(PathFact(x, emptyList(), Tail.STAR, mt), exact(y), ExclusionSet.Empty))
        val star = Conclusion(starSide(x), ExclusionSet.Empty, false)
        val staticPremise = setOf(Pattern(starSide(STATIC), ExclusionSet.Empty))
        val static = Conclusion(starSide(STATIC), ExclusionSet.Empty, false)
        val staticRead = MicroEdge.of(starEdge(STATIC, listOf(f, g, f), y, emptyList()))
        for (run1 in listOf(false, true)) for (statement in listOf(false, true)) {
            val requests = ArrayList<Pair<Set<Pattern>, RequestKind>>()
            val algebra = ReferenceAlgebra(ApMode(run1, Direction.FORWARD, 1),
                request = { p, r -> requests += p to r }, manager = m, historicalRun1 = true)
            algebra.applyEdge(markRead, star, premise, statement) { _, _ -> fail("A star mark cannot supply T") }
            assertEquals(if (run1) listOf(premise to RequestKind.Mark(t)) else emptyList(), requests)
            requests.clear()
            val output = ArrayList<Conclusion>()
            algebra.applyEdge(staticRead, static, staticPremise, statement) { _, c -> output += c }
            assertEquals(if (run1 && statement) listOf(staticPremise to RequestKind.Position(m.path(listOf(f, g))!!))
                else emptyList(), requests)
            assertEquals(if (run1 && statement) 0 else 1, output.size)
        }
    }

    @Test fun `reference filtering matches tree path checks and root or element mark policy`(): Unit {
        val checks = ArrayList<Accessor>()
        val policy = ArrayList<Pair<TaintMark, Int>>()
        val all = object : FactTypeChecker.FactApFilter {
            override fun check(accessor: Accessor): FactTypeChecker.FilterResult {
                checks += accessor
                return FactTypeChecker.FilterResult.Accept
            }
        }
        val filter = TypeFilter(all, MarkPolicy { mark, level -> policy += mark to level; level == 1 && mark == t })
        val oracle = ReferenceAlgebra(ApMode(false, Direction.FORWARD, 1), manager = m)
        val paths = listOf(emptyList(), listOf(ELEMENT_ACCESSOR_IDX), listOf(ELEMENT_ACCESSOR_IDX, ELEMENT_ACCESSOR_IDX),
            listOf(f), listOf(ELEMENT_ACCESSOR_IDX, f))
        for (path in paths) for (mark in listOf(mt, mu, MarkSlot.STAR)) {
            val c = Conclusion(PathFact(x, path, if (mark is MarkSlot.Star) Tail.STAR else Tail.EXACT, mark),
                ExclusionSet.Empty, false)
            checks.clear(); policy.clear()
            val expected = mark is MarkSlot.Star || path.any { it != ELEMENT_ACCESSOR_IDX } || (path.size == 1 && mark == mt)
            assertEquals(expected, oracle.passes(filter, c))
            assertEquals(if (mark is MarkSlot.Concrete && path.all { it == ELEMENT_ACCESSOR_IDX })
                listOf(mark.mark to path.size) else emptyList(), policy)
            assertEquals(if (path.isEmpty()) 0 else 1, checks.size)
            assertEquals(expected, fixture.ops.filter(m.factsOf(c.fact, c.exclusion, Layer.NORMAL), filter) != null)
        }
        val reject = TypeFilter(object : FactTypeChecker.FactApFilter {
            override fun check(accessor: Accessor) = FactTypeChecker.FilterResult.Reject
        }, MarkPolicy { _, _ -> fail("Path rejection precedes the mark policy") })
        assertFalse(oracle.passes(reject, Conclusion(exact(x, listOf(f)), ExclusionSet.Empty, false)))
        val rejectNext = object : FactTypeChecker.FactApFilter {
            override fun check(accessor: Accessor) = FactTypeChecker.FilterResult.Reject
        }
        val next = TypeFilter(object : FactTypeChecker.FactApFilter {
            override fun check(accessor: Accessor) = FactTypeChecker.FilterResult.FilterNext(rejectNext)
        })
        assertTrue(oracle.passes(next, Conclusion(exact(x, listOf(f)), ExclusionSet.Empty, false)))
        assertFalse(oracle.passes(next, Conclusion(exact(x, listOf(f, g)), ExclusionSet.Empty, false)))
    }

    @Test fun `conclusions decode both taint columns all marks exclusions and layers without store absorption`(): Unit {
        val root = m.taintAlg.leafNode(m.taintLeaves(MarkSet(intArrayOf(t.id, u.id)), MarkSet(intArrayOf(t.id, u.id))))
        val oracle = ref()
        for (layer in Layer.entries) {
            val facts = m.taintTree(x, layer, root, ExclusionSet.of(f))
            val decoded = oracle.conclusions(facts).toSet()
            assertEquals(setOf(
                Conclusion(exact(x, mark = mt), ExclusionSet.Empty, layer == Layer.DEMAND),
                Conclusion(exact(x, mark = mu), ExclusionSet.Empty, layer == Layer.DEMAND),
                Conclusion(PathFact(x, emptyList(), if (layer == Layer.DEMAND) Tail.ANY else Tail.ANY_TAINT, mt),
                    if (layer == Layer.DEMAND) ExclusionSet.Empty else ExclusionSet.of(f), layer == Layer.DEMAND),
                Conclusion(PathFact(x, emptyList(), if (layer == Layer.DEMAND) Tail.ANY else Tail.ANY_TAINT, mu),
                    if (layer == Layer.DEMAND) ExclusionSet.Empty else ExclusionSet.of(f), layer == Layer.DEMAND)), decoded)
            assertEquals(listOf(Conclusion(ZERO_FACT, ExclusionSet.Empty, layer == Layer.DEMAND)),
                oracle.conclusions(Reach.of(layer)))
        }
        val input = fixture.fact(x, mark = mt)
        assertFalse(oracle.agrees(FormsReference.Mode.STATEMENT, StatementSummary.EMPTY, at, premise, input,
            listOf(fixture.fact(x, mark = mu))))
    }

    @Test fun `pending must results keep exclusions through actions and binding then demote before call cut`(): Unit {
        val cleaner = Cleaner(x, m.path(listOf(f)), CleanReach.AT_AND_BELOW, null)
        val input = item(Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, mt), ExclusionSet.of(f), false))
        for (read in listOf(f, g)) {
            val plan = CallPlan(setOf(x), listOf(
                CallStage.Actions(RETURNED, REWRITTEN, listOf(CleanStep.Clean(cleaner))),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(starEdge(x, listOf(read), y, listOf(g, f))))),
                emptyList(), BEFORE, AFTER)
            val actionSeen = ArrayList<PlanItem>()
            val exitSeen = ArrayList<PlanItem>()
            val reached = ref().run(plan, listOf(input), at, PlanHooks(
                clean = { cl, it -> actionSeen += it; cleanRes(cl, it.c).facts.map { c -> it.copy(c = c) } },
                exit = { it -> exitSeen += it; it.copy(c = limit(it.c, 1)) }), from = RETURNED, pendingDemotion = true)
            assertEquals(listOf(input.copy(identityCandidate = false)), actionSeen)
            assertEquals(ExclusionSet.of(f), reached.single { it.first == REWRITTEN }.second.c.exclusion)
            if (read == f) {
                assertTrue(exitSeen.isEmpty())
                assertTrue(reached.none { it.first == AFTER })
            } else {
                val beforeCut = exitSeen.single()
                assertEquals(Conclusion(PathFact(y, listOf(g, f), Tail.ANY_TAINT, mt), ExclusionSet.Empty, true), beforeCut.c)
                assertEquals(false, beforeCut.identityCandidate)
                assertEquals(premise, beforeCut.premise)
                val final = reached.single { it.first == AFTER }.second
                assertEquals(Conclusion(PathFact(y, listOf(g), Tail.ANY, mt), ExclusionSet.Empty, true), final.c)
            }
        }
    }

    @Test fun `pending demotion is invocation state and checks its resume point before any work`(): Unit {
        val plan = CallPlan(setOf(x), listOf(CallStage.Actions(RETURNED, AFTER, emptyList())), emptyList(), BEFORE, AFTER)
        val input = item(Conclusion(exact(x), ExclusionSet.Empty, false))
        val ordinary = ref().run(plan, listOf(input), at, from = RETURNED).single { it.first == AFTER }.second
        assertEquals(input, ordinary)
        val pending = ref().run(plan, listOf(input), at, from = RETURNED, pendingDemotion = true).single { it.first == AFTER }.second
        assertEquals(input.copy(c = input.c.copy(demand = true), identityCandidate = false), pending)
        for (point in CallPoint.entries.filter { it != RETURNED })
            assertFails { ref().run(plan, emptyList(), at, from = point, pendingDemotion = true) }
        assertFails { ref().run(plan.reversed(), emptyList(), at, from = RETURNED, pendingDemotion = true) }
        val star = item(Conclusion(starSide(x), ExclusionSet.Empty, false))
        assertFails { ref().run(plan, listOf(star), at, from = RETURNED, pendingDemotion = true) }
    }

    @Test fun `ordered field actions cut generated statements retain untouched inputs and project the temporary`(): Unit {
        val temporary = AccessPathBase.CleanerTemporary()
        val cleaner = Cleaner(temporary, null, CleanReach.AT_AND_BELOW, u)
        val field = CleanStep.Field(temporary, summary(starEdge(x, listOf(f), temporary, emptyList()), keepEdge(x)),
            cleaner, summary(starEdge(temporary, emptyList(), x, listOf(f, g)), touched = setOf(temporary, x)))
        val plan = CallPlan(setOf(x), listOf(CallStage.Actions(RETURNED, AFTER, listOf(field))), emptyList(), BEFORE, AFTER)
        val cuts = ArrayList<PlanItem>()
        val cleaned = ArrayList<PlanItem>()
        val input = item(Conclusion(exact(x, listOf(f), mt), ExclusionSet.Empty, false))
        val reached = ref().run(plan, listOf(input), at, PlanHooks(
            clean = { cl, it -> cleaned += it; cleanRes(cl, it.c).facts.map { c -> it.copy(c = c) } },
            statementCut = { it -> cuts += it; it.copy(c = limit(it.c, 1)) }), from = RETURNED)
        assertEquals(setOf(temporary, x), cleaned.map { it.c.fact.base }.toSet())
        assertEquals(3, cuts.size)
        assertEquals(listOf(Conclusion(PathFact(x, listOf(f), Tail.ANY, mt), ExclusionSet.Empty, true)),
            reached.filter { it.first == AFTER }.map { it.second.c })
        assertTrue(reached.none { it.first == AFTER && it.second.c.fact.base == temporary })
        val unrelated = input.copy(c = Conclusion(exact(y), ExclusionSet.Empty, false))
        cuts.clear()
        assertEquals(unrelated, ref().run(plan, listOf(unrelated), at, PlanHooks(statementCut = { cuts += it; it }),
            from = RETURNED).single { it.first == AFTER }.second)
        assertTrue(cuts.isEmpty())
    }

    @Test fun `every sibling stage reads the same input and callee hooks receive unchanged metadata`(): Unit {
        val calls = CallStage.Callees(BOUND, RETURNED, listOf(fixture.method))
        val plan = CallPlan(setOf(x), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x))),
            CallStage.Edges(BOUND, AFTER, BIND_BACK, summary(starEdge(x, emptyList(), y, emptyList()))),
            calls), emptyList(), BEFORE, AFTER)
        val input = item(Conclusion(exact(x, listOf(f)), ExclusionSet.Empty, true), null)
        val observed = ArrayList<PlanItem>()
        val reached = ref().run(plan, listOf(input, input), at, PlanHooks(callees = { stage, value ->
            assertSame(calls, stage); observed += value; listOf(value) }))
        assertEquals(listOf(input), observed)
        assertEquals(input, reached.single { it.first == RETURNED }.second)
        assertEquals(input.copy(c = input.c.copy(fact = input.c.fact.copy(base = y))),
            reached.single { it.first == AFTER }.second)
    }
}
