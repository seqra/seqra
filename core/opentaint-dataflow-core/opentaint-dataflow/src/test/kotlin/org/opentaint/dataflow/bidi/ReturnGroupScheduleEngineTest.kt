package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

class ReturnGroupScheduleEngineTest {
    private val x = AccessPathBase.Argument(0)
    private fun plan(f: MethodAnalysisFixtures, raw: List<CleanStep>, scheduled: Boolean): CallPlan = CallPlan(
        setOf(x, STATIC), listOf(
            if (scheduled) CallStage.Actions.run1GroupReturnRewriter(raw, f.manager.marks)
            else CallStage.Actions(CallPoint.RETURNED, CallPoint.REWRITTEN, raw),
            CallStage.Edges(CallPoint.REWRITTEN, CallPoint.AFTER, StageKind.BIND_BACK,
                StatementSummary.identities(listOf(x, STATIC)))),
        emptyList(), CallPoint.RETURNED, CallPoint.AFTER)

    @Test fun `ordinary invocation and resumed suffix request the independent first group order`() {
        for (start in listOf<Int?>(null, 0, 1, 2)) MethodAnalysisFixtures().use { f ->
            val g = f.groupMark().mark
            val h = f.groupMark(f.mark("U")).mark
            val atoms = listOf(g, h, g)
            val raw = atoms.map { CleanStep.Clean(Cleaner(x, null, CleanReach.EXACT, it)) }
            f.call(1, plan(f, raw, scheduled = true))
            val actor = f.core()
            val services = RunMethodCallServices(actor)
            val original = f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR))
            val input = f.fact(x, tail = Tail.STAR, mark = MarkSlot.STAR)
            if (start == null) services.process(EdgeDelta(original, f.inst(1), input))
            else {
                val operation = OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(1)))
                services.resumePoint(original, input, ResumeSite.CleanerInput(operation, start, CleanerPhase.CLEAN),
                    CallResumeState(Layer.NORMAL, false))
            }
            val expected = atoms.drop(start ?: 0).distinct()
            assertEquals(expected, f.requests.map { (it.second as ReferenceRequestKind.Mark).mark })
            val outputs = actor.edges.edgesAt(f.inst(2), original).map { it.second }.toList()
            assertEquals(1, outputs.size)
            assertEquals(expected.toSet(), (outputs.single() as FlowTree).markExclusion.ids.map(::TaintMark).toSet())
            services.freeze()
        }
    }

    @Test fun `late static group answers resume raw sites and preserve unscheduled scalar summaries`() {
        fun run(scheduled: Boolean, identity: Boolean?): Pair<Set<String>, Int> = MethodAnalysisFixtures().use { f ->
            val g = f.groupMark().mark
            val h = f.groupMark(f.mark("U")).mark
            val atoms = listOf(g, h, g, h, g)
            val raw = atoms.map { CleanStep.Clean(Cleaner(x, null, CleanReach.EXACT, it)) }
            f.call(1, plan(f, raw, scheduled))
            lateinit var services: RunMethodCallServices
            val actor = f.core(callFactory = MethodCallServicesFactory {
                RunMethodCallServices(it).also { service -> services = service }
            })
            val path = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), f.field("staticField"))
            val original = f.manager.initial(f.pattern(STATIC, path, Tail.STAR, MarkSlot.STAR))
            val image = f.fact(x, listOf(f.field("image")), Tail.STAR, MarkSlot.STAR, layer = Layer.DEMAND)
            val operation = OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(1)))
            services.resumePoint(original, image, ResumeSite.CleanerInput(operation, 0, CleanerPhase.CLEAN),
                CallResumeState(Layer.NORMAL, identity))
            f.drain(actor)
            val service = checkNotNull(actor.runnerPort.staticService)
            val pending = service.statistics().pointSubscriptions
            assertEquals(if (scheduled) 2 else 5, pending)
            val replies = service.commit(StaticBatch().apply {
                supplies += RegionGroups(original.toPattern(),
                    f.manager.marks.atomsFor(GroupSet.of(listOf(f.manager.marks.groupOf(g), f.manager.marks.groupOf(h)))))
            }).filterIsInstance<StaticOwnerEffect.PointReady>()
            assertEquals(pending, replies.size)
            replies.forEach(actor::onStaticEffect)
            f.drain(actor)
            for (atom in listOf(g, h)) {
                val markedInitial = f.manager.initial(f.pattern(STATIC, path, Tail.STAR, MarkSlot.Concrete(atom)))
                assertTrue(actor.edges.edgesAt(f.inst(2), markedInitial).any { (_, value) ->
                    value.layer == Layer.DEMAND && f.ops.leaves(value).any {
                        it.fact.base == x && it.fact.mark == MarkSlot.Concrete(atom)
                    }
                }, "Late group supply must reach the caller suffix")
            }
            fun encode(p: Pattern): String {
                assertEquals(ExclusionSet.Empty, p.exclusion)
                val mark = when (val m = p.fact.mark) {
                    is MarkSlot.Concrete -> f.manager.marks.name(m.mark)
                    is MarkSlot.Star -> "FLOW" + m.excluded.ids.map { f.manager.marks.name(TaintMark(it)) }.sorted()
                }
                return listOf(p.fact.base, p.fact.path.map { f.manager.accessors.accessor(it) }, p.fact.tail, mark).toString()
            }
            val result = actor.summaries.all().flatMap { (premise, facts) ->
                val initial = premise.members.map { encode(it.toPattern()) }.sorted()
                f.ops.leaves(facts).map { "$initial -> ${encode(it)} / ${facts.layer}" }
            }.toSet()
            assertEquals(0, service.statistics().pointSubscriptions)
            result to pending
        }
        for (identity in listOf(null, false, true)) {
            val reference = run(scheduled = false, identity)
            val selected = run(scheduled = true, identity)
            assertEquals(reference.first, selected.first)
            assertTrue(selected.second < reference.second)
        }
    }
}
