package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.PipelineFixtures
import org.opentaint.dataflow.bidi.fixtures.QueuedProtocolSteps
import kotlin.test.*

/** Real candidate selection and complete matching must both use the component reader. */
class Run1StaticSummaryPipelineTest {
    @Test fun `broad root and class suppliers reach owned field summaries in every arrival order while E still blocks`(): Unit {
        for (root in listOf(false, true)) for (blocked in listOf(false, true))
            for (summaryFirst in listOf(false, true)) for (replayFirst in listOf(false, true)) {
                val steps = QueuedProtocolSteps()
                PipelineFixtures(limit = 1, protocol = steps).use { f ->
                    val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
                    val field = f.store.field("f")
                    val sibling = f.store.field("g")
                    val slot = Run1Slot.StaticField(cls, field)
                    f.staticFootprints[f.callee.method] = Run1StaticFootprint(setOf(slot),
                        fieldUniverse = setOf(slot, Run1Slot.StaticField(cls, sibling)))
                    val t = f.store.mark("T")
                    val exclusion = if (blocked) ExclusionSet.of(if (root) cls else field)
                        else ExclusionSet.of(if (root) f.ap.accessors.index(ClassStaticAccessor("Other")) else sibling)
                    val input = f.store.fact(STATIC, if (root) emptyList() else listOf(cls), Tail.ANY_TAINT,
                        MarkSlot.Concrete(t), exclusion, Layer.NORMAL)
                    assertEquals(exclusion, assertIs<TaintTree>(input).exclusion,
                        "The fixture must supply an actual normal must fact carrying E")
                    val sub = f.subscription(input)
                    val initial = slot.initial(f.ap)
                    val pub = Publication(initial, f.store.fact(AccessPathBase.Return, tail = Tail.STAR, mark = MarkSlot.STAR))
                    if (summaryFirst) f.storage().publish(listOf(pub))
                    f.subscriptions.subscribe(sub)
                    if (!summaryFirst) f.storage().publish(listOf(pub))
                    if (replayFirst) { steps.replayAll(); steps.runNotifications(); f.deliverAll() }
                    else { steps.runNotifications(); f.deliverAll(); steps.replayAll() }
                    if (blocked) assertTrue(f.summaries.isEmpty(), "Ordinary summary matching still respects ancestor E")
                    else {
                        assertTrue(f.summaries.isNotEmpty(), "A broad supplier needs prefix candidates and field projection")
                        for (application in f.summaries) {
                            assertSame(sub.ref, application.part.ref)
                            assertEquals(pub, application.publication)
                            assertSame(initial, application.publication.premise)
                            val leaves = f.ops.leaves(application.part.added).toList()
                            assertEquals(1, leaves.size)
                            assertEquals(if (root) emptyList() else listOf(cls), leaves.single().fact.path)
                            assertEquals(exclusion, assertIs<TaintTree>(application.part.added).exclusion)
                            assertEquals(MarkSlot.Concrete(t), leaves.single().fact.mark)
                            assertEquals(Layer.NORMAL, application.part.added.layer)
                        }
                    }
                }
            }
    }

    @Test fun `packed broad ancestor and direct field retain distinct E groups through replay and delivery`(): Unit {
        for (summaryFirst in listOf(false, true)) {
            PipelineFixtures(limit = 1).use { f ->
                val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
                val field = f.store.field("f")
                val excludedChild = f.store.field("child")
                val slot = Run1Slot.StaticField(cls, field)
                f.staticFootprints[f.callee.method] = Run1StaticFootprint(setOf(slot))
                val t = f.store.mark("T")
                val e = ExclusionSet.of(excludedChild)
                val broad = f.store.fact(STATIC, listOf(cls), Tail.ANY_TAINT, MarkSlot.Concrete(t), e, Layer.NORMAL) as TaintTree
                val direct = f.store.fact(STATIC, listOf(cls, field), Tail.ANY_TAINT, MarkSlot.Concrete(t), e, Layer.NORMAL) as TaintTree
                assertEquals(e, broad.exclusion)
                assertEquals(e, direct.exclusion)
                val input = broad.withRoot(f.ap.taintAlg.mergeAdd(broad.root, direct.root))
                val pub = Publication(slot.initial(f.ap),
                    f.store.fact(AccessPathBase.Return, tail = Tail.STAR, mark = MarkSlot.STAR))
                if (summaryFirst) f.storage().publish(listOf(pub))
                f.subscriptions.subscribe(f.subscription(input))
                if (!summaryFirst) f.storage().publish(listOf(pub))
                f.deliverAll()
                assertEquals(setOf(e), f.summaries.map { assertIs<TaintTree>(it.part.added).exclusion }.toSet(),
                    "Ordinary matching retains the original ancestor and direct-leaf header")
                assertTrue(f.summaries.all { application ->
                    f.ops.leaves(application.part.added).map { it.fact.path }.toSet() ==
                        setOf(listOf(cls),listOf(cls,field))
                })
            }
        }
    }
}
