package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.Record
import kotlin.test.*

class RecordReplayTest {
    private val x = AccessPathBase.Argument(0)
    private val returned = AccessPathBase.Return
    private fun core(f: MethodAnalysisFixtures, index: Int = 5): RunMethodAnalyzer = f.core(index = index,
        localFactory = MethodLocalServicesFactory(::RunMethodLocalServices),
        callFactory = MethodCallServicesFactory(::RunMethodCallServices))
    private fun summary(vararg edges: PathEdge): StatementSummary =
        StatementSummary(edges.map { it.from.base }.toSet(), edges.map(MicroEdge::of), emptyList(), emptyMap())
    private fun plan(f: MethodAnalysisFixtures, returnedActions: List<CleanStep> = emptyList(),
                     bindBack: StatementSummary = summary(starEdge(returned, emptyList(), x, emptyList())),
                     aliases: StatementSummary? = null): CallPlan {
        val stages = arrayListOf<CallStage>(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x), ZERO_KEEP)),
            CallStage.Actions(BOUND, ADDED, emptyList()),
            CallStage.Callees(ADDED, RETURNED, listOf(f.store.key("callee"))),
            CallStage.Actions(RETURNED, REWRITTEN, returnedActions),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, bindBack))
        if (aliases != null) stages += CallStage.Edges(REWRITTEN, AFTER, ALIASES, aliases, Guard.MemoryEffect)
        return CallPlan(setOf(x, returned), stages, emptyList(), BEFORE, AFTER)
    }
    private fun part(f: MethodAnalysisFixtures, added: Facts, callerLayer: Layer = Layer.NORMAL,
                     premise: PremiseKey = f.manager.zero, call: Int = 1, callee: MethodKey = f.store.key("callee")) =
        Subscription(callee, CallerRef(f.key, premise, callerLayer, f.inst(call)), added.layer, added)
    private fun leaves(f: MethodAnalysisFixtures, values: List<Facts>): Set<Conclusion> =
        values.flatMap(FormsReference(f.ops)::conclusions).toSet()
    private fun atSuccessor(f: MethodAnalysisFixtures, actor: RunMethodAnalyzer, premise: PremiseKey): List<Facts> =
        actor.edges.edgesAt(f.inst(2), premise).map { it.second }.toList()
    private fun transferred(f: MethodAnalysisFixtures, actor: RunMethodAnalyzer, sub: Subscription,
                            j: InitialAp, g: Facts): List<PlanItem> {
        val pr = sub.ref.premise.members.map { it.toPattern() }.toSet()
        return leaves(f, listOf(sub.added)).flatMap { input -> leaves(f, listOf(g)).mapNotNull { output ->
            val candidate = !j.isZero && g.layer == Layer.NORMAL && output.fact == j.toPattern().fact && output.exclusion == j.exclusion
            when (val outcome = concat(input, summaryEdge(j.toPattern(), output), output.demand, restricted = actor.mode.restricted)) {
                EdgeOutcome.None -> null
                is EdgeOutcome.Fact -> PlanItem(pr, outcome.conclusion, candidate)
                else -> error("An admitted concrete/native transfer cannot request: $outcome")
            }
        } }
    }
    private fun reference(f: MethodAnalysisFixtures, actor: RunMethodAnalyzer, plan: CallPlan, inputs: List<PlanItem>,
                          from: CallPoint = RETURNED): Set<Conclusion> =
        FormsReference(f.ops, ReferenceAlgebra(actor.mode, manager = f.manager)).run(plan, inputs,
            Place(f.inst(1), actor.mode.run1, sources = false), PlanHooks(guards = actor.forward,
                clean = { cleaner, item -> cleanRes(cleaner, item.c).facts.map { item.copy(c = it) } },
                statementCut = { it.copy(c = limit(it.c, actor.mode.fieldLimit)) },
                exit = { it.copy(c = limit(it.c, actor.mode.fieldLimit)) }), from = from)
            .filter { it.first == plan.exit }.map { it.second.c }.toSet()

    @Test
    fun `native exact record resumes returned stages with the original caller premise and one crossing`(): Unit {
        MethodAnalysisFixtures().use { f ->
            f.call(1, plan(f))
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val j = f.manager.initial(f.pattern(mark = t))
            val record = Record(f.store.key("callee"), Direction.FORWARD, j, f.fact(base = returned, mark = u))
            val sub = part(f, f.fact(mark = t), callerLayer = Layer.DEMAND)
            val actor = core(f)
            actor.applyRecord(sub, record)
            val expected = setOf(Conclusion(PathFact(x, emptyList(), Tail.EXACT, u), ExclusionSet.Empty, false))
            assertEquals(expected, leaves(f, atSuccessor(f, actor, sub.ref.premise)))
            assertEquals(expected, leaves(f, f.cuts.filter { it.first == Cut.CALL }.map { it.second }))
            assertEquals(1L, actor.recordCrossings)
            assertTrue(f.requests.isEmpty())
            assertTrue(f.subscriptions.isEmpty())
            assertTrue(f.sentLinks.isEmpty())
        }
    }

    @Test
    fun `ordinary summaries and native exact records run the same returned forms while only records count crossings`(): Unit {
        for (recordPath in listOf(false, true)) for (layer in Layer.entries) MethodAnalysisFixtures().use { f ->
            val a = f.field("a")
            val b = f.field("b")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val callPlan = plan(f, bindBack = summary(starEdge(returned, emptyList(), x, listOf(b))))
            f.call(1, callPlan)
            val j = f.manager.initial(f.pattern(path = listOf(a), mark = t))
            val g = f.fact(base = returned, path = listOf(a), mark = u)
            val sub = part(f, f.fact(path = listOf(a), mark = t, layer = layer), callerLayer = Layer.DEMAND)
            val actor = core(f)
            val expected = reference(f, actor, callPlan, transferred(f, actor, sub, j, g))
            if (recordPath) actor.applyRecord(sub, Record(sub.callee, Direction.FORWARD, j, g))
            else actor.applySummary(sub, Publication(j, g), 0)
            assertEquals(setOf(Conclusion(PathFact(x, listOf(b, a), Tail.EXACT, u), ExclusionSet.Empty, layer == Layer.DEMAND)), expected)
            assertEquals(expected, leaves(f, atSuccessor(f, actor, sub.ref.premise)))
            assertEquals(if (recordPath) 1L else 0L, actor.recordCrossings)
            assertTrue(f.subscriptions.isEmpty())
            assertTrue(f.sentLinks.isEmpty())
        }
    }

    @Test
    fun `normal native identity leaves skip memory aliases while effects and demand inputs keep all alias paths`(): Unit {
        for (layer in Layer.entries) MethodAnalysisFixtures().use { f ->
            val a = f.field("a")
            val alias = AccessPathBase.Argument(7)
            val t = MarkSlot.Concrete(f.mark("T"))
            val g = f.ops.manager.mergeAddDelta(f.fact(mark = t), f.fact(path = listOf(a), mark = t)).first
            val j = f.manager.initial(f.pattern(mark = t))
            val sub = part(f, f.fact(mark = t, layer = layer))
            val callPlan = plan(f, bindBack = summary(keepEdge(x)), aliases = summary(starEdge(x, emptyList(), alias, emptyList())))
            f.call(1, callPlan)
            val actor = core(f)
            val expected = reference(f, actor, callPlan, transferred(f, actor, sub, j, g))
            actor.applyRecord(sub, Record(sub.callee, Direction.FORWARD, j, g))
            val wanted = buildSet {
                for (path in listOf(emptyList(), listOf(a))) add(Conclusion(PathFact(x, path, Tail.EXACT, t), ExclusionSet.Empty, layer == Layer.DEMAND))
                add(Conclusion(PathFact(alias, listOf(a), Tail.EXACT, t), ExclusionSet.Empty, layer == Layer.DEMAND))
                if (layer == Layer.DEMAND) add(Conclusion(PathFact(alias, emptyList(), Tail.EXACT, t), ExclusionSet.Empty, true))
            }
            assertEquals(wanted, expected)
            assertEquals(expected, leaves(f, atSuccessor(f, actor, sub.ref.premise)))
            assertEquals(1L, actor.recordCrossings)
        }
    }

    @Test
    fun `native identity classification keeps the full must E and the exact-versus-must distinction`(): Unit {
        for (sameE in listOf(false, true)) for (exact in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val a = f.field("initial blocked")
            val b = f.field("result blocked")
            val alias = AccessPathBase.Argument(7)
            val t = MarkSlot.Concrete(f.mark("T"))
            val ej = ExclusionSet.of(a)
            val eg = if (sameE) ej else ExclusionSet.of(b)
            val j = f.manager.initial(f.pattern(tail = Tail.ANY_TAINT, mark = t, exclusion = ej))
            val g = f.fact(tail = if (exact) Tail.EXACT else Tail.ANY_TAINT, mark = t,
                exclusion = if (exact) ExclusionSet.Empty else eg)
            val sub = part(f, f.fact(tail = Tail.ANY_TAINT, mark = t))
            val callPlan = plan(f, bindBack = summary(keepEdge(x)), aliases = summary(starEdge(x, emptyList(), alias, emptyList())))
            f.call(1, callPlan)
            val actor = core(f)
            val wanted = buildSet {
                val tail = if (exact) Tail.EXACT else Tail.ANY_TAINT
                val e = if (exact) ExclusionSet.Empty else eg
                add(Conclusion(PathFact(x, emptyList(), tail, t), e, false))
                if (exact || !sameE) add(Conclusion(PathFact(alias, emptyList(), tail, t), e, false))
            }
            assertEquals(wanted, reference(f, actor, callPlan, transferred(f, actor, sub, j, g)))
            actor.applyRecord(sub, Record(sub.callee, Direction.FORWARD, j, g))
            assertEquals(wanted, leaves(f, atSuccessor(f, actor, sub.ref.premise)))
            assertEquals(1L, actor.recordCrossings)
        }
    }

    @Test
    fun `FLOW field and mark annotations make a native return an effect rather than a bare policy identity`(): Unit {
        for (annotated in listOf("identity", "field", "mark")) MethodAnalysisFixtures().use { f ->
            val a = f.field("a")
            val b = f.field("b")
            val alias = AccessPathBase.Argument(7)
            val t = f.mark("T")
            val u = f.mark("U")
            val j = f.manager.initial(f.pattern(tail = Tail.STAR, mark = MarkSlot.STAR))
            val g = f.fact(tail = Tail.STAR,
                mark = if (annotated == "mark") MarkSlot.Star(f.manager.markSetOf(u)) else MarkSlot.STAR,
                exclusion = if (annotated == "field") ExclusionSet.of(a) else ExclusionSet.Empty)
            val added = f.fact(tail = Tail.STAR, mark = MarkSlot.Star(f.manager.markSetOf(t)), exclusion = ExclusionSet.of(b))
            val sub = part(f, added, premise = j)
            val callPlan = plan(f, bindBack = summary(keepEdge(x)), aliases = summary(starEdge(x, emptyList(), alias, emptyList())))
            f.call(1, callPlan)
            val actor = core(f)
            val mark = MarkSlot.Star(if (annotated == "mark") f.manager.markSetOf(t) + f.manager.markSetOf(u) else f.manager.markSetOf(t))
            val e = if (annotated == "field") ExclusionSet.of(intArrayOf(a, b)) else ExclusionSet.of(b)
            val wanted = buildSet {
                add(Conclusion(PathFact(x, emptyList(), Tail.STAR, mark), e, false))
                if (annotated != "identity") add(Conclusion(PathFact(alias, emptyList(), Tail.STAR, mark), e, false))
            }
            assertEquals(wanted, reference(f, actor, callPlan, transferred(f, actor, sub, j, g)))
            actor.applyRecord(sub, Record(sub.callee, Direction.FORWARD, j, g))
            assertEquals(wanted, leaves(f, atSuccessor(f, actor, sub.ref.premise)))
            assertEquals(1L, actor.recordCrossings)
            assertTrue(f.requests.isEmpty())
        }
    }

    @Test
    fun `a demand publication identity is an effect candidate and admits normal-source aliases`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val alias = AccessPathBase.Argument(7)
            val j = f.manager.initial(f.pattern())
            val g = f.fact(layer = Layer.DEMAND)
            val sub = part(f, f.fact())
            val callPlan = plan(f, bindBack = summary(keepEdge(x)), aliases = summary(starEdge(x, emptyList(), alias, emptyList())))
            f.call(1, callPlan)
            val actor = core(f)
            val expected = reference(f, actor, callPlan, transferred(f, actor, sub, j, g))
            actor.applySummary(sub, Publication(j, g), 0)
            assertEquals(setOf(Conclusion(PathFact(x, emptyList(), Tail.EXACT, MarkSlot.Concrete(f.mark("T"))), ExclusionSet.Empty, true),
                Conclusion(PathFact(alias, emptyList(), Tail.EXACT, MarkSlot.Concrete(f.mark("T"))), ExclusionSet.Empty, true)), expected)
            assertEquals(expected, leaves(f, atSuccessor(f, actor, sub.ref.premise)))
            assertEquals(0L, actor.recordCrossings)
        }
    }

    @Test
    fun `native crossing counts a generated batch before result filtering or a returned kill removes it`(): Unit {
        for (kill in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val a = f.field("a")
            val reject = TypeFilter(object : FactTypeChecker.FactApFilter {
                override fun check(accessor: Accessor) = FactTypeChecker.FilterResult.Reject
            })
            val mapping = starEdge(returned, emptyList(), x, emptyList())
            val bind = if (kill) summary(mapping) else StatementSummary(setOf(returned), listOf(MicroEdge.of(mapping)), emptyList(), emptyMap(), mapOf(x to reject))
            val actions = if (kill) listOf(CleanStep.Kill(StatementSummary(setOf(returned), emptyList(), emptyList(), emptyMap()))) else emptyList()
            val callPlan = plan(f, actions, bind)
            f.call(1, callPlan)
            val j = f.manager.initial(f.pattern())
            val g = f.fact(base = returned, path = listOf(a))
            val sub = part(f, f.fact())
            val actor = core(f)
            assertTrue(transferred(f, actor, sub, j, g).isNotEmpty())
            assertTrue(reference(f, actor, callPlan, transferred(f, actor, sub, j, g)).isEmpty())
            actor.applyRecord(sub, Record(sub.callee, Direction.FORWARD, j, g))
            assertTrue(atSuccessor(f, actor, sub.ref.premise).isEmpty())
            assertTrue(f.cuts.none { it.first == Cut.CALL })
            assertEquals(1L, actor.recordCrossings)
            val wrong = sub.copy(added = f.fact(mark = MarkSlot.Concrete(f.mark("wrong"))))
            actor.applyRecord(wrong, Record(sub.callee, Direction.FORWARD, j, g))
            assertEquals(1L, actor.recordCrossings, "An empty initial transfer is not a crossing")
        }
    }

    @Test
    fun `F76 transient views preserve input layer output E gates distinct marks and exact-target suffix dropping`(): Unit {
        for ((oldMust, newMust) in listOf(false to true, true to false, true to true))
            for (layer in Layer.entries) for (blocked in listOf(false, true)) MethodAnalysisFixtures().use { f ->
                val a = f.field("a")
                val b = f.field("b")
                val outMark = MarkSlot.Concrete(f.mark("output"))
                val inMark = MarkSlot.Concrete(f.mark("input"))
                val e = ExclusionSet.of(b)
                val view = RecordLeafView(Pattern(PathFact(returned, emptyList(), if (newMust) Tail.STAR else Tail.EXACT, outMark),
                    if (newMust) e else ExclusionSet.Empty),
                    Conclusion(PathFact(x, listOf(a), if (oldMust) Tail.ANY else Tail.EXACT, inMark), ExclusionSet.Empty, oldMust))
                val inputPath = if (newMust) listOf(if (blocked) b else a, a) else emptyList()
                val input = f.fact(base = returned, path = inputPath, mark = if (blocked && !newMust) inMark else outMark, layer = layer)
                val sub = part(f, input)
                val forwardPlan = plan(f)
                f.call(1, forwardPlan)
                val actor = core(f, index = 2)
                val initial = when (val outcome = concat(leaves(f, listOf(input)).single(), summaryEdge(view.premise, view.result),
                    view.result.demand, restricted = true)) {
                    is EdgeOutcome.Fact -> listOf(PlanItem(setOf(ZERO_PATTERN), outcome.conclusion, false))
                    EdgeOutcome.None -> emptyList()
                    else -> error("A restricted view cannot request")
                }
                val expected = reference(f, actor, forwardPlan.reversed(), initial, from = ADDED)
                actor.applyRecordView(sub, view)
                val wanted = if (blocked) emptySet() else setOf(Conclusion(PathFact(x, listOf(a), if (oldMust) Tail.ANY else Tail.EXACT, inMark),
                    ExclusionSet.Empty, oldMust || layer == Layer.DEMAND))
                assertEquals(wanted, expected)
                assertEquals(expected, leaves(f, actor.edges.edgesAt(f.inst(0), sub.ref.premise).map { it.second }.toList()))
                assertEquals(if (blocked) 0L else 1L, actor.recordCrossings)
                assertTrue(f.requests.isEmpty())
                assertTrue(f.subscriptions.isEmpty())
                assertTrue(f.sentLinks.isEmpty())
            }
    }

    @Test
    fun `complete caller premise contexts and layers survive independent native record resumptions`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val a = f.field("a")
            val callerPremise = f.manager.premiseOf(listOf(f.manager.initial(f.pattern()), f.manager.initial(f.pattern(path = listOf(a)))))
            val j = f.manager.initial(f.pattern())
            val g = f.fact(base = returned)
            f.call(1, plan(f))
            for (callerLayer in Layer.entries) for (addedLayer in Layer.entries) {
                val callerKey = f.store.key("$callerLayer-$addedLayer")
                val actor = f.core(index = 5, L = 4, key = callerKey,
                    localFactory = MethodLocalServicesFactory(::RunMethodLocalServices), callFactory = MethodCallServicesFactory(::RunMethodCallServices))
                val sub = Subscription(f.store.key("callee"), CallerRef(callerKey, callerPremise, callerLayer, f.inst(1)),
                    addedLayer, f.fact(layer = addedLayer))
                actor.applyRecord(sub, Record(sub.callee, Direction.FORWARD, j, g))
                assertEquals(setOf(callerPremise), actor.edges.edgesAt(f.inst(2)).map { it.first }.toSet())
                assertEquals(leaves(f, listOf(f.fact(layer = addedLayer))), leaves(f, atSuccessor(f, actor, callerPremise)))
                assertEquals(1L, actor.recordCrossings)
            }
        }
    }
}
