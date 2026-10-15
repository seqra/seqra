package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.PipelineFixtures
import org.opentaint.dataflow.bidi.fixtures.QueuedProtocolSteps
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

class MarkQuestionProtocolTest {
    private class Fixture(val steps: ProtocolSteps = ProtocolSteps.Inline) : AutoCloseable {
        val f = PipelineFixtures(protocol = steps)
        val statistics = Run1AbstractionStatistics()
        val batchStatistics = RequestBatchStatistics()
        val events = ArrayDeque<RunEvent>()
        val answers = linkedSetOf<Pair<MethodKey, InitialAp>>()
        val climbs = linkedSetOf<Triple<MethodKey, InitialAp, Int>>()
        var acceptSends = true
        var cancelOnSend = false
        var covered: (MethodKey, InitialAp, TaintMark) -> Boolean = { _, _, _ -> false }
        var onAnswer: (() -> Unit)? = null
        val port = object : SubscriptionPort by f.port {
            override val abstractionStatistics get() = this@Fixture.statistics
            override val requestBatchStatistics get() = this@Fixture.batchStatistics
            override fun post(event: RunEvent.Delivery): Boolean { events += event; return true }
            override fun post(event: RunEvent.QuestionDelivery): Boolean { events += event; return true }
            override fun send(event: RunEvent): Boolean {
                if (cancelOnSend) f.ap.cancellation.cancel()
                if (!acceptSends) return false
                events += event
                return true
            }
            override fun covered(method: MethodKey, initial: InitialAp, mark: TaintMark): Boolean =
                this@Fixture.covered(method, initial, mark)
        }
        val subscriptions = SubscriptionManager(port)

        fun publish(key: MarkQuestionKey, vararg ids: Int) =
            f.storage().publishQuestions(QuestionDelta(key, RequestMarkSet.fromIds(ids)))

        fun drain() {
            var count = 0
            while (events.isNotEmpty()) {
                check(++count < 10000) { "Question closure did not finish" }
                when (val event = events.removeFirst()) {
                    is RunEvent.QuestionDelivery -> subscriptions.onQuestions(event.callee, event.delta)
                    is RunEvent.Delivery -> subscriptions.onDelivery(event.callee, event.publications)
                    is RunEvent.InitialAnswersIn -> {
                        event.answers.forEach { answers += event.method to it }
                        onAnswer?.invoke()
                    }
                    is RunEvent.MarkRequestsIn -> event.marks.forEachId { climbs += Triple(event.method, event.premise, it) }
                    else -> error("Unexpected protocol event: $event")
                }
            }
        }

        override fun close() { subscriptions.freeze(); f.close() }
    }

    @Test fun `both arrival orders and deferred replay notify orders retain the exact concrete answer`(): Unit {
        for (questionFirst in listOf(false, true)) for (replayFirst in listOf(false, true)) {
            val steps = QueuedProtocolSteps()
            Fixture(steps).use { h ->
                val f = h.f
                val t = f.store.mark("T")
                val fact = f.store.fact(mark = MarkSlot.Concrete(t))
                val key = MarkQuestionKey.dynamic(f.ops.policy(fact))
                if (questionFirst) h.publish(key, t.id)
                h.subscriptions.subscribe(f.subscription(fact))
                if (!questionFirst) h.publish(key, t.id)
                if (replayFirst) { steps.replayAll(); steps.runNotifications() }
                else { steps.runNotifications(); h.drain(); steps.replayAll() }
                h.drain()
                val expected = f.ap.initial(key.initial.base, null, Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(t))
                assertEquals(setOf(f.callee to expected), h.answers)
                assertTrue(h.climbs.isEmpty())
                assertTrue(h.events.isEmpty())
                h.subscriptions.subscribe(f.subscription(fact))
                h.publish(key, t.id)
                steps.replayAll(); steps.runNotifications(); h.drain()
                assertEquals(setOf(f.callee to expected), h.answers)
            }
        }
    }

    @Test fun `late ancestor FLOW uses full geometry E mx and distinct original caller initials`(): Unit {
        Fixture().use { h ->
            val f = h.f
            val a = f.store.field("a")
            val t = f.store.mark("T")
            val u = f.store.mark("U")
            val base = AccessPathBase.Argument(0)
            val slot = f.ap.initial(base, f.ap.path(listOf(a)), Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
            val key = MarkQuestionKey(slot, slot.toPattern(), MarkQuestionRole.DYNAMIC)
            fun flow(e: ExclusionSet = ExclusionSet.Empty, mx: MarkSet = MarkSet.EMPTY,
                     child: Boolean = false): Facts {
                val root = if (child) FlowAlgebra.mergeAdd(FlowAlgebra.LEAF_NODE,
                    FlowAlgebra.prepend(f.ap, f.ap.path(listOf(a)), FlowAlgebra.LEAF_NODE)) else FlowAlgebra.LEAF_NODE
                return f.ap.flowTree(base, Layer.NORMAL, e, mx, root)
            }
            val origin1 = f.ap.initial(AccessPathBase.Argument(4), null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
            val origin2 = f.ap.initial(AccessPathBase.This, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
            val caller1 = f.store.key("first", entry = 1)
            val caller2 = f.store.key("second", entry = 2)
            h.subscriptions.subscribe(f.subscription(flow(mx = f.ap.markSetOf(t)), origin1, caller = caller1))
            h.subscriptions.subscribe(f.subscription(flow(e = ExclusionSet.of(a)), origin2, caller = caller2))
            h.publish(key, t.id, u.id)
            h.drain()
            assertEquals(setOf(Triple(caller1, origin1, u.id)), h.climbs)
            h.subscriptions.subscribe(f.subscription(flow(e = ExclusionSet.of(a), child = true), origin2,
                caller = caller2, call = 2))
            h.drain()
            assertEquals(setOf(Triple(caller1, origin1, u.id), Triple(caller2, origin2, t.id),
                Triple(caller2, origin2, u.id)), h.climbs)
        }
    }

    @Test fun `new full catalog geometry replays old inputs without using the short initial slot as the query`(): Unit {
        Fixture().use { h ->
            val f = h.f
            val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
            val field = f.store.field("value")
            val a = f.store.field("a")
            val b = f.store.field("b")
            val slotPath = listOf(cls, field)
            val t = f.store.mark("T")
            val initial = f.ap.initial(STATIC, f.ap.path(slotPath), Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
            fun query(suffix: Int) = MarkQuestionKey(initial,
                f.store.pattern(STATIC, slotPath + suffix, Tail.EXACT, MarkSlot.Concrete(t)), MarkQuestionRole.SINK)
            val actual = f.store.fact(STATIC, slotPath + b, mark = MarkSlot.Concrete(t))
            h.subscriptions.subscribe(f.subscription(actual))
            h.publish(query(a), t.id); h.drain()
            assertTrue(h.answers.isEmpty())
            h.publish(query(b), t.id); h.drain()
            val expected = f.ap.initial(STATIC, f.ap.path(slotPath + b), Tail.EXACT,
                ExclusionSet.Empty, MarkSlot.Concrete(t))
            assertEquals(setOf(f.callee to expected), h.answers)
        }
    }

    @Test fun `question replay keeps the admitted nonempty E when a fresh exact delta clears it`(): Unit {
        Fixture().use { h ->
            val f = h.f
            val a = f.store.field("a")
            val t = f.store.mark("T")
            val u = f.store.mark("U")
            val old = f.store.fact(tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(t),
                exclusion = ExclusionSet.of(a)) as TaintTree
            val exact = f.store.fact(mark = MarkSlot.Concrete(u)) as TaintTree
            val grown = f.ap.taintTree(old.base, old.layer,
                f.ap.taintAlg.mergeAdd(old.root, exact.root), old.exclusion)
            val directory = CalleeSubscriptions(f.ops)
            directory.add(f.subscription(old))
            val admitted = assertNotNull(directory.admit(f.subscription(grown)))
            assertEquals(ExclusionSet.Empty, (admitted.fresh.added as TaintTree).exclusion)
            assertEquals(ExclusionSet.of(a), (admitted.current.added as TaintTree).exclusion)
            assertTrue((admitted.current.added as TaintTree).root.hasAny)
            directory.clear()
            h.subscriptions.subscribe(f.subscription(old))
            val initial = f.ap.initial(old.base, f.ap.path(listOf(a)), Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
            h.publish(MarkQuestionKey.dynamic(initial), t.id)
            h.drain()
            h.subscriptions.subscribe(f.subscription(grown)); h.drain()
            assertTrue(h.answers.isEmpty(), "The old excluded ancestor remains excluded during full-value replay")
        }
    }

    @Test fun `one indexed group is visited once and queued local answers may safely admit another input`(): Unit {
        Fixture().use { h ->
            val f = h.f
            val a = f.store.field("a")
            val t = f.store.mark("T")
            val leaf = f.store.fact(mark = MarkSlot.Concrete(t)) as TaintTree
            val root = f.ap.taintAlg.mergeAdd(leaf.root, f.ap.taintAlg.prepend(f.ap, f.ap.path(listOf(a)), leaf.root))
            val input = leaf.withRoot(root)
            val sub = f.subscription(input)
            h.subscriptions.subscribe(sub)
            h.publish(MarkQuestionKey.dynamic(f.ops.policy(input)), t.id)
            assertTrue(h.answers.isEmpty(), "Publisher only enqueues; it cannot run a local actor")
            var reentered = false
            h.onAnswer = {
                if (!reentered) {
                    reentered = true
                    h.subscriptions.subscribe(f.subscription(input, call = 2))
                }
            }
            h.drain()
            assertTrue(reentered)
            assertEquals(2L, h.statistics.snapshot().replayCandidates,
                "One original group and one reentrant new origin; multiple leaf postings do not duplicate a visit")
            assertEquals(2, h.answers.size, "Both exact and chain answers for T survive")
            val batch = h.batchStatistics.snapshot()
            assertEquals(2L, batch.groupGeometryTests)
            assertTrue(batch.groupGeometrySteps > 0)
            assertTrue(batch.memberScans > 0)
            assertEquals(4L, batch.answers)
        }
    }

    @Test fun `a live current-value question visit rejects mutating reentry and releases its visit guard`(): Unit {
        Fixture().use { h ->
            val f = h.f
            val input = f.store.fact()
            val sub = f.subscription(input)
            val directory = CalleeSubscriptions(f.ops)
            directory.add(sub)
            val key = MarkQuestionKey.dynamic(f.ops.policy(input))
            assertFailsWith<IllegalStateException> {
                directory.visitQuestion(key) { directory.add(f.subscription(input, call = 2)) }
            }
            assertNotNull(directory.add(f.subscription(input, call = 2)))
            var visited = 0
            directory.visitQuestion(key) { visited++ }
            assertEquals(2, visited)
            directory.clear()
        }
    }

    @Test fun `covered full FLOW targets suppress transport while uncovered original targets remain`(): Unit {
        Fixture().use { h ->
            val f = h.f
            val t = f.store.mark("T")
            val input = f.store.fact(tail = Tail.STAR, mark = MarkSlot.STAR)
            val origin = f.ops.policy(input)
            h.covered = { method, initial, mark -> method == f.caller && initial === origin && mark == t }
            h.subscriptions.subscribe(f.subscription(input, origin))
            h.publish(MarkQuestionKey.dynamic(origin), t.id)
            h.drain()
            assertTrue(h.climbs.isEmpty())
            assertEquals(1L, h.statistics.snapshot().coveredMarks)
            assertEquals(1L, h.statistics.snapshot().replayClimbs, "Eligible climb count precedes coverage suppression")
        }
    }

    @Test fun `rejected queued result without cancellation fails instead of losing the answer`(): Unit {
        Fixture().use { h ->
            val f = h.f
            val t = f.store.mark("T")
            val input = f.store.fact(mark = MarkSlot.Concrete(t))
            h.subscriptions.subscribe(f.subscription(input))
            h.publish(MarkQuestionKey.dynamic(f.ops.policy(input)), t.id)
            h.acceptSends = false
            assertFailsWith<IllegalStateException> { h.drain() }
            assertEquals(1L, h.statistics.snapshot().replayAnswers)
            assertTrue(h.answers.isEmpty())
        }
    }

    @Test fun `cancellation after result enqueue aborts the replay and records partial numeric work`(): Unit {
        Fixture().use { h ->
            val f = h.f
            val t = f.store.mark("T")
            val input = f.store.fact(mark = MarkSlot.Concrete(t))
            h.subscriptions.subscribe(f.subscription(input))
            h.publish(MarkQuestionKey.dynamic(f.ops.policy(input)), t.id)
            h.cancelOnSend = true
            assertFailsWith<Cancellation.Cancelled> { h.drain() }
            val counts = h.statistics.snapshot()
            assertEquals(1L, counts.replayVisits)
            assertEquals(1L, counts.replayTests)
            assertEquals(1L, counts.replayAnswers)
            assertTrue(counts.maxReplayVisitNanos > 0)
        }
    }
}
