package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.PipelineFixtures
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import kotlin.test.*

/** The old full-query/role key has been removed; F78 stores only the two slot forms. */
class MarkQuestionKeyTest {
    @Test fun `only normal abstract root and static field initials become request slots`(): Unit {
        PipelineFixtures(limit = 1).use { f ->
            val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
            val field = f.store.field("f")
            val ordinary = Run1Slot.Base(AccessPathBase.Argument(0))
            val static = Run1Slot.StaticField(cls, field)
            assertEquals(ordinary, normalRun1Slot(ordinary.initial(f.ap)))
            assertEquals(static, normalRun1Slot(static.initial(f.ap)))
            assertNull(normalRun1Slot(f.ap.zero))
            assertNull(normalRun1Slot(f.ap.initial(STATIC, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)))
            assertNull(normalRun1Slot(f.ap.initial(STATIC, f.ap.path(listOf(cls)), Tail.STAR,
                ExclusionSet.Empty, MarkSlot.STAR)))
            assertNull(normalRun1Slot(f.ap.initial(ordinary.base, f.ap.path(listOf(field)), Tail.STAR,
                ExclusionSet.Empty, MarkSlot.STAR)))
            assertNull(normalRun1Slot(f.ap.initial(ordinary.base, null, Tail.ANY,
                ExclusionSet.Empty, MarkSlot.STAR)))
            assertNull(normalRun1Slot(f.ap.initial(ordinary.base, null, Tail.STAR,
                ExclusionSet.of(field), MarkSlot.STAR)))
            assertNull(normalRun1Slot(f.ap.initial(ordinary.base, null, Tail.STAR,
                ExclusionSet.Empty, MarkSlot.Concrete(f.store.mark("T")))))
            assertFailsWith<IllegalArgumentException> { Run1Slot.Base(STATIC) }
            assertFailsWith<IllegalArgumentException> { Run1Slot.Base(AccessPathBase.Zero) }
        }
    }

    @Test fun `concrete answers own their detached batch and target the full method key`(): Unit {
        PipelineFixtures(limit = 1).use { f ->
            val initial = f.ap.initial(f.store.pattern())
            val mutable = arrayListOf(initial)
            val event = RunEvent.InitialAnswersIn(f.callee, mutable)
            mutable.clear()
            assertEquals(listOf(initial), event.answers)
            assertEquals(f.callee, event.target())
            assertNotEquals(f.caller, event.target())
            assertFails { (event.answers as MutableList<InitialAp>).clear() }
        }
    }

    @Test fun `borrowing an authoritative row keeps old immutable segments while current marks accumulate`(): Unit {
        PipelineFixtures(limit = 1).use { f ->
            val storage = f.storage()
            val slot = Run1Slot.Base(AccessPathBase.Argument(0))
            val t = f.store.mark("T")
            val u = f.store.mark("U")
            val first = RequestStorage().apply { addAll(slot, RequestMarkSet.of(t)) }
            storage.commit(first)
            val old = storage.borrowRequest(slot)
            val second = RequestStorage().apply { addAll(slot, RequestMarkSet.fromIds(intArrayOf(t.id, u.id))) }
            storage.commit(second)
            assertEquals(RequestMarkSet.of(t), old.dynamic)
            assertTrue(old.precomputed.isEmpty)
            assertEquals(RequestMarkSet.fromIds(intArrayOf(t.id, u.id)), storage.borrowRequest(slot).dynamic)
            assertEquals(2, storage.borrowRequest(slot).countMarks {})
        }
    }
}
