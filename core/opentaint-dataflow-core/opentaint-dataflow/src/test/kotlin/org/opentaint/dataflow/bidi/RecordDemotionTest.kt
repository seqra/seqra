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

class RecordDemotionTest {
    private val x = AccessPathBase.Argument(0)
    private val returned = AccessPathBase.Return
    private fun summary(vararg edges: PathEdge): StatementSummary =
        StatementSummary(edges.map { it.from.base }.toSet(), edges.map(MicroEdge::of), emptyList(), emptyMap())
    private fun plan(f: MethodAnalysisFixtures, actions: List<CleanStep> = emptyList(),
                     bindBack: StatementSummary = summary(starEdge(returned, emptyList(), x, emptyList())),
                     aliases: StatementSummary? = null): CallPlan {
        val stages = arrayListOf<CallStage>(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x), ZERO_KEEP)),
            CallStage.Actions(BOUND, ADDED, emptyList()),
            CallStage.Callees(ADDED, RETURNED, listOf(f.store.key("callee"))),
            CallStage.Actions(RETURNED, REWRITTEN, actions),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, bindBack))
        if (aliases != null) stages += CallStage.Edges(REWRITTEN, AFTER, ALIASES, aliases, Guard.MemoryEffect)
        return CallPlan(setOf(x, returned), stages, emptyList(), BEFORE, AFTER)
    }
    private fun core(f: MethodAnalysisFixtures, fieldLimit: Int = 4): RunMethodAnalyzer = f.core(index = fieldLimit + 1, L = fieldLimit,
        localFactory = MethodLocalServicesFactory(::RunMethodLocalServices),
        callFactory = MethodCallServicesFactory(::RunMethodCallServices))
    private fun leaves(f: MethodAnalysisFixtures, values: List<Facts>): Set<Conclusion> =
        values.flatMap(FormsReference(f.ops)::conclusions).toSet()
    private fun part(f: MethodAnalysisFixtures, record: Record, added: Facts): Subscription =
        Subscription(record.method, CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(1)), added.layer, added)
    private fun batches(f: MethodAnalysisFixtures, actor: RunMethodAnalyzer, record: Record, sub: Subscription): Pair<List<PlanItem>, List<PlanItem>> {
        val native = ArrayList<PlanItem>()
        val pending = ArrayList<PlanItem>()
        val pr = sub.ref.premise.members.map { it.toPattern() }.toSet()
        for (input in leaves(f, listOf(sub.added))) {
            val a = Pattern(input.fact, input.exclusion)
            assertTrue(applicable(record.premise.toPattern(), a) || inside(record.premise.toPattern(), a))
            val destination = if (inside(record.premise.toPattern(), a)) native else pending
            for (g in leaves(f, listOf(record.conclusion))) {
                val identity = !record.premise.isZero && record.conclusion.layer == Layer.NORMAL &&
                    g.fact == record.premise.toPattern().fact && g.exclusion == record.premise.exclusion
                when (val out = concat(input, summaryEdge(record.premise.toPattern(), g), restricted = actor.mode.restricted)) {
                    is EdgeOutcome.Fact -> destination += PlanItem(pr, out.conclusion, identity)
                    EdgeOutcome.None -> Unit
                    else -> error("A native concrete transfer cannot request")
                }
            }
        }
        return native to pending
    }
    private fun reference(f: MethodAnalysisFixtures, actor: RunMethodAnalyzer, plan: CallPlan,
                          inputs: List<PlanItem>, pending: Boolean): Set<Conclusion> =
        FormsReference(f.ops, ReferenceAlgebra(actor.mode, manager = f.manager)).run(plan, inputs,
            Place(f.inst(1), actor.mode.run1, sources = false), PlanHooks(guards = true,
                clean = { cleaner, item -> cleanRes(cleaner, item.c).facts.map { item.copy(c = it) } },
                statementCut = { it.copy(c = limit(it.c, actor.mode.fieldLimit)) },
                exit = { it.copy(c = limit(it.c, actor.mode.fieldLimit)) }), from = RETURNED, pendingDemotion = pending)
            .filter { it.first == AFTER }.map { it.second.c }.toSet()
    private fun expected(f: MethodAnalysisFixtures, actor: RunMethodAnalyzer, plan: CallPlan, record: Record, sub: Subscription): Set<Conclusion> {
        val (inside, only) = batches(f, actor, record, sub)
        return reference(f, actor, plan, inside, false) + reference(f, actor, plan, only, true)
    }
    private fun actual(f: MethodAnalysisFixtures, actor: RunMethodAnalyzer): Set<Conclusion> =
        leaves(f, actor.edges.edgesAt(f.inst(2), f.manager.zero).map { it.second }.toList())

    @Test
    fun `applicable-only must record resumes normal then demotes surviving results immediately before the CALL cut`(): Unit {
        MethodAnalysisFixtures().use { f ->
            f.call(1, plan(f))
            val a = f.field("a")
            val b = f.field("b")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val e = ExclusionSet.of(b)
            val j = f.manager.initial(f.pattern(tail = Tail.ANY_TAINT, mark = t, exclusion = e))
            val record = Record(f.store.key("callee"), Direction.FORWARD, j,
                f.fact(base = returned, tail = Tail.ANY_TAINT, mark = u, exclusion = e))
            val added = f.fact(path = listOf(a), tail = Tail.ANY_TAINT, mark = t)
            assertTrue(applicable(j.toPattern(), f.pattern(path = listOf(a), tail = Tail.ANY_TAINT, mark = t)))
            assertFalse(inside(j.toPattern(), f.pattern(path = listOf(a), tail = Tail.ANY_TAINT, mark = t)))
            val sub = Subscription(record.method, CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(1)), added.layer, added)
            val actor = core(f)
            actor.applyRecord(sub, record)
            val expected = setOf(Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, u), e, true))
            assertEquals(expected, leaves(f, actor.edges.edgesAt(f.inst(2), f.manager.zero).map { it.second }.toList()))
            assertEquals(expected, leaves(f, f.cuts.filter { it.first == Cut.CALL }.map { it.second }))
            assertEquals(1L, actor.recordCrossings)
            assertEquals(Layer.NORMAL, record.conclusion.layer)
            assertEquals(e, (record.conclusion as TaintTree).exclusion)
        }
    }

    @Test
    fun `mixed inside and applicable-only batches retain normal identity and force pending identity aliases before demotion`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val a = f.field("a")
            val b = f.field("b")
            val alias = AccessPathBase.Argument(7)
            val t = MarkSlot.Concrete(f.mark("T"))
            val e = ExclusionSet.of(b)
            val j = f.manager.initial(f.pattern(tail = Tail.ANY_TAINT, mark = t, exclusion = e))
            val record = Record(f.store.key("callee"), Direction.FORWARD, j, f.fact(tail = Tail.ANY_TAINT, mark = t, exclusion = e))
            val root = f.fact(tail = Tail.ANY_TAINT, mark = t)
            val below = f.fact(path = listOf(a), tail = Tail.ANY_TAINT, mark = t)
            val input = f.manager.mergeAddDelta(root, below).first
            val sub = part(f, record, input)
            val callPlan = plan(f, bindBack = summary(keepEdge(x)), aliases = summary(starEdge(x, emptyList(), alias, emptyList())))
            f.call(1, callPlan)
            val actor = core(f)
            val (insideBatch, pendingBatch) = batches(f, actor, record, sub)
            assertEquals(1, insideBatch.size)
            assertEquals(1, pendingBatch.size)
            assertTrue(insideBatch.single().identityCandidate == true && pendingBatch.single().identityCandidate == true)
            val wanted = setOf(Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, t), e, false),
                Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, t), e, true),
                Conclusion(PathFact(alias, emptyList(), Tail.ANY_TAINT, t), e, true))
            assertEquals(wanted, expected(f, actor, callPlan, record, sub))
            actor.applyRecord(sub, record)
            assertEquals(wanted, actual(f, actor))
            assertEquals(wanted, leaves(f, f.cuts.filter { it.first == Cut.CALL }.map { it.second }))
            assertEquals(1L, actor.recordCrossings, "Two nonempty batches are one native record crossing")
            assertTrue(f.cuts.filter { it.first == Cut.CALL }.all { (_, value) ->
                (value as TaintTree).exclusion == e
            })
        }
    }

    @Test
    fun `pending record field exclusions govern returned reads before late demotion`(): Unit {
        for (blocked in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val a = f.field("a")
            val b = f.field("b")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val e = ExclusionSet.of(b)
            val j = f.manager.initial(f.pattern(tail = Tail.ANY_TAINT, mark = t))
            val record = Record(f.store.key("callee"), Direction.FORWARD, j,
                f.fact(base = returned, tail = Tail.ANY_TAINT, mark = u, exclusion = e))
            val sub = part(f, record, f.fact(path = listOf(a), tail = Tail.ANY_TAINT, mark = t))
            val cleaner = CleanStep.Clean(Cleaner(returned, null, CleanReach.AT_AND_BELOW, f.mark("unrelated")))
            val callPlan = plan(f, listOf(cleaner), summary(starEdge(returned, listOf(if (blocked) b else a), x, emptyList())))
            f.call(1, callPlan)
            val actor = core(f)
            val initial = batches(f, actor, record, sub).second.single()
            assertEquals(e, initial.c.exclusion)
            assertFalse(initial.c.demand)
            val wanted = if (blocked) emptySet() else setOf(Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, u), ExclusionSet.Empty, true))
            assertEquals(wanted, expected(f, actor, callPlan, record, sub))
            actor.applyRecord(sub, record)
            assertEquals(wanted, actual(f, actor))
            assertEquals(1L, actor.recordCrossings)
            assertEquals(if (blocked) 0 else 1, f.cuts.count { it.first == Cut.CALL })
        }
    }

    @Test
    fun `returned strong writes extend pending E and prevent later reads of the overwritten field`(): Unit {
        for (readWritten in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val a = f.field("written")
            val b = f.field("record blocked")
            val c = f.field("untouched")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val j = f.manager.initial(f.pattern(tail = Tail.ANY_TAINT, mark = t))
            val record = Record(f.store.key("callee"), Direction.FORWARD, j,
                f.fact(base = returned, tail = Tail.ANY_TAINT, mark = u, exclusion = ExclusionSet.of(b)))
            val sub = part(f, record, f.fact(path = listOf(c), tail = Tail.ANY_TAINT, mark = t))
            val write = CleanStep.Kill(summary(keepEdge(returned, e = ExclusionSet.of(a))))
            val callPlan = plan(f, listOf(write), summary(starEdge(returned, listOf(if (readWritten) a else c), x, emptyList())))
            f.call(1, callPlan)
            val actor = core(f)
            val wanted = if (readWritten) emptySet() else setOf(Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, u), ExclusionSet.Empty, true))
            assertEquals(wanted, expected(f, actor, callPlan, record, sub))
            actor.applyRecord(sub, record)
            assertEquals(wanted, actual(f, actor))
            assertEquals(1L, actor.recordCrossings)
        }
    }

    @Test
    fun `returned field actions retain pending normal facts through both statement cuts and project the temporary`(): Unit {
        for (blocked in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val a = f.field("a")
            val b = f.field("blocked")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val temporary = AccessPathBase.CleanerTemporary()
            val alias = AccessPathBase.Argument(7)
            val read = if (blocked) b else a
            val action = CleanStep.Field(temporary, summary(starEdge(returned, listOf(read), temporary, emptyList())),
                Cleaner(temporary, null, CleanReach.AT_AND_BELOW, f.mark("other")),
                summary(starEdge(temporary, emptyList(), returned, listOf(read))))
            val j = f.manager.initial(f.pattern(tail = Tail.ANY_TAINT, mark = t))
            val record = Record(f.store.key("callee"), Direction.FORWARD, j,
                f.fact(base = returned, tail = Tail.ANY_TAINT, mark = u, exclusion = ExclusionSet.of(b)))
            val sub = part(f, record, f.fact(path = listOf(a), tail = Tail.ANY_TAINT, mark = t))
            val callPlan = plan(f, listOf(action), aliases = summary(starEdge(returned, emptyList(), alias, emptyList())))
            f.call(1, callPlan)
            val actor = core(f)
            val wanted = if (blocked) emptySet() else setOf(Conclusion(PathFact(x, listOf(a), Tail.ANY_TAINT, u), ExclusionSet.Empty, true),
                Conclusion(PathFact(alias, listOf(a), Tail.ANY_TAINT, u), ExclusionSet.Empty, true))
            assertEquals(wanted, expected(f, actor, callPlan, record, sub))
            actor.applyRecord(sub, record)
            assertEquals(wanted, actual(f, actor))
            val statements = f.cuts.filter { it.first == Cut.STATEMENT }.map { it.second }
            assertEquals(if (blocked) 0 else 2, statements.size)
            assertTrue(statements.all { it.layer == Layer.NORMAL && (it as TaintTree).root.hasAny })
            if (!blocked) assertSame(temporary, statements.first().base)
            assertTrue(actual(f, actor).none { it.fact.base is AccessPathBase.CleanerTemporary })
            assertEquals(1L, actor.recordCrossings)
        }
    }

    @Test
    fun `pending exact and any results are demoted before the field cut while already-demand results stay demand`(): Unit {
        for (kind in listOf("must", "exact")) for (addedDemand in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val a = f.field("a")
            val b = f.field("b")
            val c = f.field("c")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val j = f.manager.initial(f.pattern(tail = Tail.ANY_TAINT, mark = t))
            val g = when (kind) {
                "exact" -> f.fact(base = returned, path = listOf(a, b, c), mark = u)
                else -> f.fact(base = returned, path = listOf(a, b, c), tail = Tail.ANY_TAINT, mark = u, exclusion = ExclusionSet.of(b))
            }
            val record = Record(f.store.key("callee"), Direction.FORWARD, j, g)
            val added = f.fact(path = listOf(a), tail = if (addedDemand) Tail.ANY else Tail.ANY_TAINT, mark = t,
                layer = if (addedDemand) Layer.DEMAND else Layer.NORMAL)
            val sub = part(f, record, added)
            val callPlan = plan(f, bindBack = summary(starEdge(returned, emptyList(), x, emptyList()), ZERO_KEEP))
            f.call(1, callPlan)
            val actor = core(f, fieldLimit = 2)
            val wanted = setOf(Conclusion(PathFact(x, listOf(a, b), Tail.ANY, u), ExclusionSet.Empty, true))
            assertEquals(wanted, expected(f, actor, callPlan, record, sub))
            actor.applyRecord(sub, record)
            assertEquals(wanted, actual(f, actor))
            val beforeCallCut = leaves(f, f.cuts.filter { it.first == Cut.CALL }.map { it.second })
            assertTrue(beforeCallCut.all { it.demand })
            assertEquals(if (kind == "must") setOf(ExclusionSet.of(b)) else setOf(ExclusionSet.Empty),
                beforeCallCut.map { it.exclusion }.toSet())
            assertEquals(setOf(listOf(a, b, c)), beforeCallCut.map { it.fact.path }.toSet())
            assertEquals(1L, actor.recordCrossings)
        }
    }

    @Test
    fun `pending state is local to one resume and cannot demote a later ordinary native invocation`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val a = f.field("a")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val must = f.manager.initial(f.pattern(tail = Tail.ANY_TAINT, mark = t))
            val ordinary = f.manager.initial(f.pattern(mark = u))
            val pendingRecord = Record(f.store.key("callee"), Direction.FORWARD, must,
                f.fact(base = returned, tail = Tail.ANY_TAINT, mark = t))
            val nativeRecord = Record(f.store.key("callee"), Direction.FORWARD, ordinary, f.fact(base = returned, mark = u))
            val callPlan = plan(f)
            f.call(1, callPlan)
            val actor = core(f)
            val pendingSub = part(f, pendingRecord, f.fact(path = listOf(a), tail = Tail.ANY_TAINT, mark = t))
            val nativeSub = part(f, nativeRecord, f.fact(mark = u))
            val pendingExpected = expected(f, actor, callPlan, pendingRecord, pendingSub)
            actor.applyRecord(pendingSub, pendingRecord)
            val count = f.cuts.size
            val nativeExpected = reference(f, actor, callPlan,
                listOf(PlanItem(setOf(ZERO_PATTERN), Conclusion(PathFact(returned, emptyList(), Tail.EXACT, u), ExclusionSet.Empty, false), false)), false)
            actor.applyRecord(nativeSub, nativeRecord)
            assertEquals(pendingExpected + nativeExpected, actual(f, actor))
            assertEquals(setOf(Conclusion(PathFact(x, emptyList(), Tail.EXACT, u), ExclusionSet.Empty, false)), nativeExpected)
            assertEquals(nativeExpected, leaves(f, f.cuts.drop(count).filter { it.first == Cut.CALL }.map { it.second }))
            assertEquals(2L, actor.recordCrossings)
        }
    }

    @Test
    fun `both must batches count before a returned kill and pending filter paths cannot leak a survivor`(): Unit {
        for (insideInput in listOf(false, true)) for (filter in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val a = f.field("a")
            val t = MarkSlot.Concrete(f.mark("T"))
            val j = f.manager.initial(f.pattern(tail = Tail.ANY_TAINT, mark = t))
            val record = Record(f.store.key("callee"), Direction.FORWARD, j, f.fact(base = returned, path = listOf(a), tail = Tail.ANY_TAINT, mark = t))
            val sub = part(f, record, f.fact(path = if (insideInput) emptyList() else listOf(a), tail = Tail.ANY_TAINT, mark = t))
            val reject = TypeFilter(object : FactTypeChecker.FactApFilter {
                override fun check(accessor: Accessor) = FactTypeChecker.FilterResult.Reject
            })
            val bind = starEdge(returned, emptyList(), x, emptyList())
            val actions = if (filter) emptyList() else listOf(CleanStep.Kill(StatementSummary(setOf(returned), emptyList(), emptyList(), emptyMap())))
            val mapping = if (filter) StatementSummary(setOf(returned), listOf(MicroEdge.of(bind)), emptyList(), mapOf(returned to reject)) else summary(bind)
            val callPlan = plan(f, actions, mapping)
            f.call(1, callPlan)
            val actor = core(f)
            val (native, pending) = batches(f, actor, record, sub)
            assertTrue(native.isNotEmpty() || pending.isNotEmpty())
            assertTrue(expected(f, actor, callPlan, record, sub).isEmpty())
            actor.applyRecord(sub, record)
            assertTrue(actual(f, actor).isEmpty())
            assertTrue(f.cuts.none { it.first == Cut.CALL })
            assertEquals(1L, actor.recordCrossings)
        }
    }
}
