package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as Kind
import org.opentaint.dataflow.bidi.ap.ReferenceRequestAction as RequestAction
import kotlin.test.*

/** The oracle keeps scalar input history in the test, independent of the packed answer state. */
class AddedFactRemovalTest {
    @Test
    fun `packed pending marks refine scalar history for both arrival orders duplicates and late inputs`() {
        for (requestFirst in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val root = f.manager.initial(AccessPathBase.Argument(0), null, Tail.STAR,
                ExclusionSet.Empty, MarkSlot.STAR)
            val ref = CallerRef(f.store.key("supplier"), f.manager.zero, Layer.NORMAL, f.inst(1))
            val supplied = ArrayList<Pattern>()
            val requested = LinkedHashSet<TaintMark>()
            val observed = LinkedHashSet<InitialAp>()
            val stats = Run1AbstractionStatistics()
            val abstraction = Run1Abstraction(f.ops, null, observed::add, publish = { delta ->
                // Exact history replay is supplied independently, as caller N2 does in production.
                delta.marks.forEachMark { mark ->
                    for (leaf in supplied) when (val action = f.ops.referenceRequestAction(delta.key.initial,
                        Kind.Mark(mark), leaf, ref)) {
                        is RequestAction.Answer -> observed.add(action.initial)
                        else -> Unit
                    }
                }
            }, statistics = null, policyStatistics = stats)
            fun flush() { while (!abstraction.flush(1)) Unit }
            fun checkOracle() {
                flush()
                val expected = LinkedHashSet<InitialAp>()
                if (supplied.isNotEmpty()) expected += root
                for (mark in requested) for (leaf in supplied) {
                    val action = f.ops.referenceRequestAction(root, Kind.Mark(mark), leaf, ref)
                    if (action is RequestAction.Answer) expected += action.initial
                }
                assertEquals(expected, observed)
            }
            fun offer(mark: TaintMark) {
                val facts = f.fact(mark = MarkSlot.Concrete(mark))
                supplied += f.ops.leaves(facts).toList()
                abstraction.emit(ref, facts)
                checkOracle()
            }
            fun require(mark: TaintMark) {
                requested += mark
                abstraction.requireMark(root, mark)
                checkOracle()
            }
            val t = f.mark("T")
            val u = f.mark("U")
            if (requestFirst) { require(t); offer(t) } else {
                offer(t)
                assertEquals(1L, abstraction.pendingAnswerShapeCount)
                assertEquals(1L, abstraction.pendingAnswerMarkCount)
                require(t)
            }
            assertEquals(0L, abstraction.pendingAnswerMarkCount)
            offer(u)
            assertEquals(1L, abstraction.pendingAnswerMarkCount)
            require(t)
            assertEquals(1L, abstraction.pendingAnswerMarkCount, "Another mark must not consume U")
            require(u)
            assertEquals(0L, abstraction.pendingAnswerShapeCount)
            offer(t)
            require(t)
            assertEquals(0L, abstraction.pendingAnswerMarkCount, "Persistent interest answers late suppliers without a memo row")
            abstraction.release()
            assertEquals(0L, stats.snapshot().currentPendingMarks)
            assertEquals(0L, stats.snapshot().currentPolicyMarkCells)
        }
    }

    @Test
    fun `supplier exclusion is checked before projecting into a requested initial`() {
        MethodAnalysisFixtures().use { f ->
            val g = f.field("g")
            val t = f.mark("T")
            val q = f.manager.initial(AccessPathBase.Argument(0), f.manager.path(listOf(g)),
                Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
            val ref = CallerRef(f.store.key("supplier"), f.manager.zero, Layer.NORMAL, f.inst(1))
            val observed = LinkedHashSet<InitialAp>()
            val abstraction = Run1Abstraction(f.ops, null, observed::add, {}, null, null)
            abstraction.requireMark(q, t)
            abstraction.emit(ref, f.fact(tail = Tail.ANY_TAINT,
                mark = MarkSlot.Concrete(t), exclusion = ExclusionSet.of(g)))
            while (!abstraction.flush(1)) Unit
            assertTrue(observed.none { it.mark is MarkSlot.Concrete })
            abstraction.emit(ref, f.fact(path = listOf(g), mark = MarkSlot.Concrete(t)))
            while (!abstraction.flush(1)) Unit
            assertContains(observed, f.manager.initial(q.base, q.path, Tail.EXACT,
                ExclusionSet.Empty, MarkSlot.Concrete(t)))
            abstraction.release()
        }
    }

    @Test
    fun `new geometry cannot reuse a projected broad answer or consume its mark`() {
        MethodAnalysisFixtures().use { f ->
            val t = f.mark("T")
            val g = f.field("g")
            val h = f.field("h")
            val ref = CallerRef(f.store.key("supplier"), f.manager.zero, Layer.NORMAL, f.inst(1))
            val input = f.fact(path = listOf(g), mark = MarkSlot.Concrete(t))
            val root = f.ops.policy(input)
            val unrelated = f.manager.initial(root.base, f.manager.path(listOf(h)), Tail.STAR,
                ExclusionSet.of(g), MarkSlot.STAR)
            val outputs = LinkedHashSet<InitialAp>()
            val published = ArrayList<QuestionDelta>()
            val abstraction = Run1Abstraction(f.ops, null, outputs::add, published::add, null, null)
            abstraction.emit(ref, input)
            abstraction.requireMark(unrelated, t)
            while (!abstraction.flush(1)) Unit
            assertEquals(1L, abstraction.pendingAnswerMarkCount)
            assertTrue(outputs.none { it.mark is MarkSlot.Concrete })
            assertEquals(ExclusionSet.Empty, published.single().key.initial.exclusion)
            assertEquals(listOf(h), published.single().key.pattern.fact.path)
            abstraction.requireMark(root, t)
            while (!abstraction.flush(1)) Unit
            assertEquals(0L, abstraction.pendingAnswerMarkCount)
            assertContains(outputs, f.manager.initial(root.base, null, Tail.STAR,
                ExclusionSet.Empty, MarkSlot.Concrete(t)))
            abstraction.release()
        }
    }

    @Test
    fun `many marks pack into one shape without retaining supplier multiplicity`() {
        MethodAnalysisFixtures().use { f ->
            val outputs = ArrayList<InitialAp>()
            val abstraction = Run1Abstraction(f.ops, null, outputs::add, {}, null, null)
            val marks = (0 until 128).map { f.mark("T$it") }
            for (origin in 0 until 8) for (mark in marks) {
                val ref = CallerRef(f.store.key("caller$origin"), f.manager.zero,
                    Layer.NORMAL, f.inst(1))
                abstraction.emit(ref, f.fact(mark = MarkSlot.Concrete(mark)))
            }
            assertEquals(1L, abstraction.pendingAnswerShapeCount)
            assertEquals(128L, abstraction.pendingAnswerMarkCount)
            assertTrue(outputs.none { it.mark is MarkSlot.Concrete })
            abstraction.requireMarks(f.ops.policy(f.fact()), RequestMarkSet.fromIds(marks.map { it.id }.toIntArray()))
            while (!abstraction.flush(7)) Unit
            assertEquals(128, outputs.count { it.mark is MarkSlot.Concrete })
            assertEquals(0L, abstraction.pendingAnswerMarkCount)
            abstraction.release()
        }
    }
}
