package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.Run1RequestPipelineFixtures
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import kotlin.test.*

/** Independent scalar histories belong only to this oracle, not to the runtime emitter. */
class AddedFactRemovalTest {
    @Test fun `F78 emitter and caller replay equal a scalar history in both orders with duplicate and late suppliers`(): Unit {
        for (requestFirst in listOf(false, true)) Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val slot = Run1Slot.Base(AccessPathBase.Argument(0))
            val supplied = ArrayList<Pair<TaintMark, Tail>>()
            val requested = LinkedHashSet<TaintMark>()
            fun checkOracle() {
                val expected = LinkedHashSet<Pair<MethodKey, InitialAp>>()
                if (supplied.isNotEmpty()) expected += f.callee to slot.initial(f.ap)
                for ((mark, tail) in supplied) if (mark in requested) {
                    expected += f.callee to f.ap.initial(slot.base, null,
                        if (tail == Tail.EXACT) Tail.EXACT else Tail.STAR,
                        ExclusionSet.Empty, MarkSlot.Concrete(mark))
                }
                assertEquals(expected, h.initials)
                assertTrue(h.initials.none { it.second.tail == Tail.ANY || it.second.tail == Tail.ANY_TAINT })
            }
            fun offer(mark: TaintMark, tail: Tail = Tail.EXACT, call: Int = 1) {
                supplied += mark to tail
                h.submit(f.subscription(f.store.fact(tail = tail, mark = MarkSlot.Concrete(mark)), call = call))
                h.drain(); checkOracle()
            }
            fun require(mark: TaintMark) {
                requested += mark
                h.publish(slot, mark.id); h.drain(); checkOracle()
            }
            val t = f.store.mark("T")
            val u = f.store.mark("U")
            if (requestFirst) { require(t); offer(t) } else { offer(t); require(t) }
            offer(u)
            require(t)
            require(u)
            offer(t)
            offer(t, Tail.ANY, call = 2)
            require(t)
            assertEquals(2, h.storage().borrowRequest(slot).countMarks {})
        }
    }

    @Test fun `actual static marks admit without requests and an absent mark is never fabricated`(): Unit {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
            val field = f.store.field("g")
            val slot = Run1Slot.StaticField(cls, field)
            h.declareIdentityStaticDomain(slot)
            val t = f.store.mark("T")
            val u = f.store.mark("U")
            h.submit(f.subscription(f.store.fact(STATIC, listOf(cls), Tail.ANY_TAINT,
                MarkSlot.Concrete(u), ExclusionSet.of(field))))
            h.drain()
            val suppliedU = f.ap.initial(STATIC, f.ap.path(slot.pathData), Tail.STAR,
                ExclusionSet.Empty, MarkSlot.Concrete(u))
            assertEquals(setOf(suppliedU), h.initials.map { it.second }.filter { it.mark is MarkSlot.Concrete }.toSet())
            h.submit(f.subscription(f.store.fact(STATIC, listOf(cls), Tail.ANY_TAINT,
                MarkSlot.Concrete(t), ExclusionSet.of(field)), call = 2))
            h.drain()
            val expected = f.ap.initial(STATIC, f.ap.path(slot.pathData), Tail.STAR,
                ExclusionSet.Empty, MarkSlot.Concrete(t))
            assertEquals(setOf(suppliedU, expected), h.initials.map { it.second }.filter { it.mark is MarkSlot.Concrete }.toSet())
            assertTrue(h.storage().borrowRequest(slot).isEmpty, "No static ordinary request row was retained")
        }
    }

    @Test fun `actual static siblings retain their own geometry without a request rendezvous`(): Unit {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
            val first = Run1Slot.StaticField(cls, f.store.field("g"))
            val second = Run1Slot.StaticField(cls, f.store.field("h"))
            h.declareIdentityStaticDomain(first, second)
            val t = f.store.mark("T")
            h.submit(f.subscription(f.store.fact(STATIC, first.pathData.toList(), mark = MarkSlot.Concrete(t))))
            h.drain()
            val expected = f.ap.initial(STATIC, f.ap.path(first.pathData), Tail.EXACT,
                ExclusionSet.Empty, MarkSlot.Concrete(t))
            assertEquals(setOf(expected), h.initials.map { it.second }.filter { it.mark is MarkSlot.Concrete }.toSet())
            assertTrue(h.initials.none { it.second.pathData.contentEquals(second.pathData) },
                "No actual sibling mark was supplied")
            assertTrue(h.storage().borrowRequest(first).isEmpty)
            assertTrue(h.storage().borrowRequest(second).isEmpty)
            h.submit(f.subscription(f.store.fact(STATIC, first.pathData.toList(), mark = MarkSlot.Concrete(t)), call = 2))
            h.drain()
            assertEquals(setOf(expected), h.initials.map { it.second }.filter { it.mark is MarkSlot.Concrete }.toSet())
        }
    }

    @Test fun `many supplier origins produce one logical answer per requested mark without an emitter memo`(): Unit {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val slot = Run1Slot.Base(AccessPathBase.Argument(0))
            val marks = (0 until 128).map { f.store.mark("T$it") }
            for (origin in 0 until 8) for (mark in marks) {
                h.submit(f.subscription(f.store.fact(mark = MarkSlot.Concrete(mark)),
                    caller = f.store.key("caller$origin")))
            }
            h.drain()
            assertEquals(setOf(f.callee to slot.initial(f.ap)), h.initials)
            h.collect(f.callee, slot, RequestMarkSet.fromIds(marks.map { it.id }.toIntArray()))
            h.commit(); h.drain()
            val expected = marks.map { mark -> f.callee to f.ap.initial(slot.base, null, Tail.EXACT,
                ExclusionSet.Empty, MarkSlot.Concrete(mark)) }.toSet()
            assertEquals(expected, h.initials.filter { it.second.mark is MarkSlot.Concrete }.toSet())
            assertEquals(128, h.storage().borrowRequest(slot).countMarks {})
            val batches = h.sent.filterIsInstance<RunEvent.InitialAnswersIn>()
            assertEquals(8, batches.size, "Eight distinct caller owners have independent method-empty barriers")
            assertTrue(batches.all { batch -> batch.method == f.callee && batch.answers.size == 128 })
            assertEquals(expected, batches.flatMap { batch -> batch.answers.map { batch.method to it } }.toSet(),
                "Caller envelopes may repeat; callee canonical admission retains exactly the same128 initials")
        }
    }
}
