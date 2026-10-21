package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.GroupedScalarMarks
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import kotlin.test.*

/** Compare the actor with the unchanged scalar walk, which keeps list multiplicity. */
class CleanerStepDeduplicationTest {
    private val x = AccessPathBase.Argument(0)
    private fun summary(vararg edges: PathEdge) = StatementSummary(
        edges.mapTo(LinkedHashSet()) { it.from.base }, edges.map(MicroEdge::of), emptyList(), emptyMap())

    @Test fun `finished field results lose multiplicity before the next step but retain scalar observations`() {
        for (index in listOf(1, 3)) MethodAnalysisFixtures().use { f ->
            val a = f.field("a")
            val b = f.field("b")
            val fieldLimit = maxOf(1, index - 1)
            val prefix = if (fieldLimit == 1) listOf(a) else listOf(a, b)
            val selected = if (index == 1) f.groupMark(f.mark("U")).mark else f.mark("U")
            val temporary = AccessPathBase.CleanerTemporary()
            val field = CleanStep.Field(temporary,
                summary(starEdge(x, prefix, temporary, emptyList()), keepEdge(x)),
                Cleaner(temporary, null, CleanReach.AT_AND_BELOW, selected),
                summary(starEdge(temporary, emptyList(), x, prefix + f.field("overflow"))))
            val stage = CallStage.Actions(RETURNED, AFTER, List(3) { field })
            val plan = CallPlan(setOf(x), listOf(stage), emptyList(), RETURNED, AFTER)
            f.call(1, plan)
            val actor = f.core(index = index, L = fieldLimit)
            val services = RunMethodCallServices(actor)
            val input = if (index == 1) f.run1Fact(x, prefix, Tail.ANY)
                else f.fact(x, prefix, Tail.ANY, layer = Layer.DEMAND)
            val reference = FormsReference(f.ops, ReferenceAlgebra(actor.mode, manager = f.manager))
            val initial = PlanItem(setOf(ZERO_PATTERN), reference.conclusions(input).single(), true)
            var scalarCuts = 0
            val groups = GroupedScalarMarks(f.manager.marks)
            val expected = reference.run(plan, listOf(initial), Place(f.inst(1), index == 1, false),
                PlanHooks(clean = { cleaner, item ->
                    val result = if (index == 1) groups.clean(cleaner, item.c) else cleanRes(cleaner, item.c)
                    result.facts.map { item.copy(c = it) }
                }, statementCut = {
                    scalarCuts++
                    it.copy(c = limit(it.c, fieldLimit))
                }, exit = { it.copy(c = limit(it.c, fieldLimit)) }), from = RETURNED)
                .filter { it.first == AFTER }.map { it.second.c }.toSet()
            val operation = OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(1)))
            services.resumePoint(f.manager.zero, input, ResumeSite.CleanerInput(operation, 0, CleanerPhase.INPUT),
                CallResumeState(Layer.NORMAL, true))
            val actual = actor.edges.edgesAt(f.inst(2), f.manager.zero).flatMap { reference.conclusions(it.second) }.toSet()
            assertEquals(expected, actual)
            val actualCuts = f.cuts.count { it.first == Cut.STATEMENT }
            if (index == 1) assertTrue(actualCuts < scalarCuts,
                "The scalar baseline must contain duplicated future work: actual=$actualCuts scalar=$scalarCuts")
            else assertEquals(scalarCuts, actualCuts, "Later FULL keeps the original list walk")
            services.freeze()
        }
    }

    @Test fun `separate invocations and originals still execute every same-step callback`() {
        MethodAnalysisFixtures().use { f ->
            val group = f.groupMark().mark
            val stage = CallStage.Actions(RETURNED, AFTER, listOf(
                CleanStep.Clean(Cleaner(x, null, CleanReach.AT_AND_BELOW, group))))
            f.call(1, CallPlan(setOf(x), listOf(stage), emptyList(), RETURNED, AFTER))
            val actor = f.core()
            val services = RunMethodCallServices(actor)
            val input = f.fact(x, tail = Tail.STAR, mark = MarkSlot.STAR)
            val site = ResumeSite.CleanerInput(
                OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(1))), 0, CleanerPhase.INPUT)
            val originals = listOf(x, AccessPathBase.Argument(1), x).map {
                f.manager.initial(f.pattern(it, tail = Tail.STAR, mark = MarkSlot.STAR))
            }
            for ((i, original) in originals.withIndex()) {
                services.resumePoint(original, input, site,
                    CallResumeState(Layer.NORMAL, listOf(null, true, false)[i]))
            }
            assertEquals(originals, f.requests.map { it.first }, "No cross-invocation visited history")
            assertTrue(originals.all { original -> actor.edges.edgesAt(f.inst(2), original).any() })
            services.freeze()
        }
    }

    @Test fun `duplicate field images preserve future group requests with an intervening distinct image`() {
        for (reverse in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val a = f.field("a")
            val b = f.field("b")
            val c = f.field("c")
            val u = f.groupMark(f.mark("U")).mark
            val v = f.groupMark(f.mark("V")).mark
            val temporary = AccessPathBase.CleanerTemporary()
            // The untouched a image and the two written a images are equal only
            // after the field cut. c is a distinct intervening result.
            val writes = listOf(listOf(a, b), listOf(c, b), listOf(a, c))
                .let { if (reverse) it.reversed() else it }
            val field = CleanStep.Field(temporary,
                summary(starEdge(x, listOf(a), temporary, emptyList()), keepEdge(x)),
                Cleaner(temporary, null, CleanReach.AT_AND_BELOW, u),
                summary(*writes.map { starEdge(temporary, emptyList(), x, it) }.toTypedArray()))
            val stage = CallStage.Actions(RETURNED, AFTER, listOf(field,
                CleanStep.Clean(Cleaner(x, null, CleanReach.AT_AND_BELOW, v))))
            val plan = CallPlan(setOf(x), listOf(stage), emptyList(), RETURNED, AFTER)
            f.call(1, plan)
            val actor = f.core()
            val services = RunMethodCallServices(actor)
            val original = f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR))
            val input = f.fact(x, listOf(a), Tail.STAR,
                MarkSlot.Star(MarkExclusions.of(f.manager.marks, u)))
            val reference = FormsReference(f.ops, ReferenceAlgebra(actor.mode, manager = f.manager))
            val scalarRequests = ArrayList<TaintMark>()
            val groups = GroupedScalarMarks(f.manager.marks)
            val expected = reference.run(plan,
                listOf(PlanItem(setOf(original.toPattern()), reference.conclusions(input).single(), false)),
                Place(f.inst(1), true, false), PlanHooks(clean = { cleaner, item ->
                    val result = groups.clean(cleaner, item.c)
                    result.request?.let(scalarRequests::add)
                    result.facts.map { item.copy(c = it) }
                }, statementCut = { it.copy(c = limit(it.c, 1)) },
                    exit = { it.copy(c = limit(it.c, 1)) }), from = RETURNED)
                .filter { it.first == AFTER }.map { it.second.c }.toSet()
            val site = ResumeSite.CleanerInput(
                OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(1))), 0, CleanerPhase.INPUT)
            services.resumePoint(original, input, site, CallResumeState(Layer.NORMAL, false))
            assertEquals(expected, actor.edges.edgesAt(f.inst(2), original)
                .flatMap { reference.conclusions(it.second) }.toSet())
            val actualRequests = f.requests.map { (it.second as ReferenceRequestKind.Mark).mark }
            assertEquals(scalarRequests.toSet(), actualRequests.toSet())
            assertTrue(actualRequests.size < scalarRequests.size,
                "Equal images skip future callbacks, including nonadjacent duplicates")
            assertEquals(3, actualRequests.count { it == v },
                "The normal a image, demand a image and demand c image remain distinct")
            services.freeze()
        }
    }
}
