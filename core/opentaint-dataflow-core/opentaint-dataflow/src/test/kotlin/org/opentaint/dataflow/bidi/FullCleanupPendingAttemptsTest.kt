package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

/** Old raw-step rows can merge images that new active-entry rows keep separate. */
class FullCleanupPendingAttemptsTest {
    private fun execute(bulk: Boolean): Set<String> = MethodAnalysisFixtures().use { f ->
        val x = AccessPathBase.Argument(0)
        val t = f.mark("T")
        val u = f.mark("U")
        val v = f.mark("V")
        val raw = listOf(u, t, v, t).map { CleanStep.Clean(Cleaner(x, null, CleanReach.EXACT, it)) }
        val actions = if (bulk) CallStage.Actions.run1FullReturnRewriter(raw, f.manager.marks)
            else CallStage.Actions(CallPoint.RETURNED, CallPoint.REWRITTEN, raw)
        f.call(1, CallPlan(setOf(x), listOf(actions,
            CallStage.Edges(CallPoint.REWRITTEN, CallPoint.AFTER, StageKind.BIND_BACK,
                StatementSummary.identities(listOf(x)))), emptyList(), CallPoint.RETURNED, CallPoint.AFTER))
        lateinit var calls: RunMethodCallServices
        val actor = f.core(RunConfig(1, 1, null, f.records, SeedIndex.EMPTY, listOf(f.key),
            run1MarkMode = MarkMode.FULL), callFactory = MethodCallServicesFactory {
            RunMethodCallServices(it).also { service -> calls = service }
        })
        val path = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), f.field("f"))
        val initial = f.manager.initial(f.pattern(STATIC, path, Tail.STAR, MarkSlot.STAR))
        val operation = OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(1)))
        fun offer(entry: Int, name: String) {
            val image = f.fact(x, listOf(f.field(name)), Tail.STAR, MarkSlot.STAR, layer = Layer.DEMAND)
            calls.resumePoint(initial, image, ResumeSite.CleanerInput(operation, entry, CleanerPhase.CLEAN),
                CallResumeState(Layer.NORMAL, false))
        }
        offer(0, "left")
        offer(1, "right")
        offer(0, "late")
        f.drain(actor)
        val service = checkNotNull(actor.runnerPort.staticService)
        val replies = service.commit(StaticBatch().apply {
            supplies += RegionGroups(initial.toPattern(), MarkSet.of(t) + u + v)
        }).filterIsInstance<StaticOwnerEffect.PointReady>()
        assertTrue(replies.isNotEmpty())
        // Deliver after every pending registration has committed. A reply must detach before resumption.
        replies.reversed().forEach(actor::onStaticEffect)
        f.drain(actor)
        assertEquals(0, service.statistics().pointSubscriptions)
        // The old rows are detached. A fresh image must register again and receive the known supply.
        offer(1, "afterReply")
        f.drain(actor)
        assertEquals(0, service.statistics().pointSubscriptions)
        val result = actor.summaries.all().flatMap { (premise, facts) ->
            f.ops.leaves(facts).map { leaf ->
                val atom = (leaf.fact.mark as? MarkSlot.Concrete)?.mark
                listOf(premise.members.map { (it.mark as? MarkSlot.Concrete)?.mark?.let(f.manager.marks::name) },
                    leaf.fact.base, leaf.fact.path.map { f.manager.accessors.accessor(it) }, leaf.fact.tail,
                    leaf.exclusion, atom?.let(f.manager.marks::name), facts.layer).toString()
            }
        }.toSet()
        for (name in listOf("left", "right", "late", "afterReply")) {
            assertTrue(result.any { name in it && "T" in it }, "The saved $name image needs its T continuation")
        }
        calls.freeze()
        result
    }

    @Test fun `overlapping raw entries and late images preserve all scalar exits without a row bijection`() {
        assertEquals(execute(false), execute(true))
    }
}
