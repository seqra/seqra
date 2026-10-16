package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.MicroEdgeBuilder
import org.opentaint.dataflow.bidi.store.CallerRef
import kotlin.test.*

/** Exercise owner admission and actual retained/live publication, without a demand row. */
class StrongQueryPipelineTest {
    private val arg = AccessPathBase.Argument(0)
    private val ret = AccessPathBase.Return

    private fun method(): MethodAnalysisFixtures = MethodAnalysisFixtures().also { f ->
        f.statement(1, MicroEdgeBuilder().apply { move(ret, arg) }.build())
    }

    private fun ref(f: MethodAnalysisFixtures): CallerRef =
        CallerRef(f.store.key("caller"), f.manager.zero, Layer.NORMAL, f.inst(1))

    @Test fun `later actual input starts absent guide and its computed opposite base is published`() {
        method().use { f ->
            val t = f.mark("T")
            val input = f.fact(arg, mark = MarkSlot.Concrete(t))
            val actor = f.core(index = 3,
                localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            assertTrue(actor.initials.all().isEmpty())
            assertTrue(actor.summaries.all().none())
            f.admit(actor, ref(f), input.layer, input)
            val actual = f.manager.initial(f.pattern(arg, mark = MarkSlot.Concrete(t)))
            assertContains(actor.initials.all(), actual)
            f.drain(actor)
            val expected = f.pattern(ret, mark = MarkSlot.Concrete(t))
            assertTrue(f.publications.flatten().any { pub ->
                pub.premise === actual && expected in f.ops.leaves(pub.conclusion).toList()
            }, "Missing old exit demand cannot veto a proven current-query response")
            assertTrue(actor.summaries.all().any { (premise, facts) ->
                premise === actual && expected in f.ops.leaves(facts).toList()
            })
        }
    }

    @Test fun `actual input after a guide computation republishes existing raw answers without restarting the initial`() {
        method().use { f ->
            val t = f.mark("T")
            val actual = f.manager.initial(f.pattern(arg, mark = MarkSlot.Concrete(t)))
            val actor = f.core(index = 3,
                localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            // A prior guide may have started the identical canonical assumption.
            actor.addInitial(actual)
            f.drain(actor)
            val expected = f.pattern(ret, mark = MarkSlot.Concrete(t))
            assertTrue(actor.summaries.all().any { (_, facts) -> expected in f.ops.leaves(facts).toList() })
            assertTrue(f.publications.isEmpty(), "No prior/current exit permission existed yet")
            val processed = actor.steps
            val input = f.fact(arg, mark = MarkSlot.Concrete(t))
            f.admit(actor, ref(f), input.layer, input)
            f.drain(actor)
            assertEquals(processed, actor.steps, "Raw response replay does not rerun the initial")
            assertTrue(f.publications.flatten().any { pub ->
                pub.premise === actual && expected in f.ops.leaves(pub.conclusion).toList()
            })
        }
    }

    @Test fun `nonreturning actual query has no manufactured answer`() {
        MethodAnalysisFixtures(successors = listOf(listOf(1), listOf(1), emptyList()), normalExit = 2).use { f ->
            val input = f.fact()
            val actor = f.core(index = 3,
                localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            f.admit(actor, ref(f), input.layer, input)
            f.drain(actor)
            assertTrue(actor.nonZeroInitial)
            assertTrue(actor.summaries.all().none())
            assertTrue(f.publications.isEmpty())
        }
    }
}
