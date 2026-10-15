package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.PipelineFixtures
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import kotlin.test.*

class MarkQuestionKeyTest {
    @Test fun `query owns its path and keeps tail exclusion and role separate from the initial slot`(): Unit {
        PipelineFixtures().use { f ->
            val a = f.store.field("a")
            val b = f.store.field("b")
            val path = arrayListOf(a)
            val initial = f.ap.initial(AccessPathBase.Argument(0), null, Tail.STAR,
                ExclusionSet.Empty, MarkSlot.STAR)
            val pattern = f.store.pattern(path = path, tail = Tail.ANY_TAINT,
                exclusion = ExclusionSet.of(b))
            val key = MarkQuestionKey(initial, pattern, MarkQuestionRole.SINK)
            val hash = key.hashCode()
            path += b
            assertEquals(listOf(a), key.pattern.fact.path)
            assertEquals(MarkSlot.STAR, key.pattern.fact.mark)
            assertEquals(ExclusionSet.of(b), key.pattern.exclusion)
            assertEquals(hash, key.hashCode())
            assertNotEquals(key, MarkQuestionKey(initial, key.pattern, MarkQuestionRole.SOURCE))
            assertNotEquals(key, MarkQuestionKey(initial,
                key.pattern.copy(fact = key.pattern.fact.copy(tail = Tail.EXACT)), key.role))
            assertNotEquals(key, MarkQuestionKey(initial, key.pattern.copy(exclusion = ExclusionSet.Empty), key.role))
        }
    }

    @Test fun `dynamic factory keeps the original abstract initial and new answers own their batch`(): Unit {
        PipelineFixtures().use { f ->
            val initial = f.ops.policy(f.store.fact())
            val key = MarkQuestionKey.dynamic(initial)
            assertSame(initial, key.initial)
            assertEquals(initial.toPattern(), key.pattern)
            assertEquals(MarkQuestionRole.DYNAMIC, key.role)
            val mutable = arrayListOf(initial)
            val event = RunEvent.InitialAnswersIn(f.callee, mutable)
            mutable.clear()
            assertEquals(listOf(initial), event.answers)
            assertEquals(f.callee, event.target())
        }
    }

    @Test fun `publication retains one current immutable root and notifies only fresh mark memberships`(): Unit {
        PipelineFixtures().use { f ->
            val storage = f.storage()
            val key = MarkQuestionKey.dynamic(f.ops.policy(f.store.fact()))
            val t = f.store.mark("T")
            val u = f.store.mark("U")
            storage.publishQuestions(QuestionDelta(key, RequestMarkSet.of(t)))
            val old = storage.questions()
            storage.publishQuestions(QuestionDelta(key, RequestMarkSet.fromIds(intArrayOf(t.id, u.id))))
            assertEquals(1, old.size)
            assertEquals(RequestMarkSet.of(t), old[key])
            assertEquals(RequestMarkSet.fromIds(intArrayOf(t.id, u.id)), storage.questions()[key])
            assertEquals(1, storage.questions().size)
        }
    }
}
