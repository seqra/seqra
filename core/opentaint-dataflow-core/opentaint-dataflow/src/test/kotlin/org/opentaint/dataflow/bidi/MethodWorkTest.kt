package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.interp.MicroEdge
import org.opentaint.dataflow.bidi.interp.ZERO_FACT
import org.opentaint.dataflow.bidi.interp.keepEdge
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

class MethodWorkTest {
    @Test fun `cancellation during final publication prevents completed work`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val core = f.core()
            core.emitAfter(f.manager.zero, f.inst(2), f.fact())
            f.onPublish = { _, _ -> f.cancellation.cancel() }
            assertFailsWith<Cancellation.Cancelled> { core.work(10) }
            assertEquals(1, f.publications.size)
        }
    }

    @Test fun `GEN checks cancellation on a skipped candidate after its previous output`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val core = f.core()
            val target = PathFact(AccessPathBase.Return, emptyList(), Tail.EXACT, MarkSlot.Concrete(f.mark("T")))
            val source = MicroEdge.of(PathEdge(ZERO_FACT, target, ExclusionSet.Empty))
            val skipped = MicroEdge.of(keepEdge(AccessPathBase.Argument(0)))
            var outputs = 0
            assertFailsWith<Cancellation.Cancelled> {
                core.applier.gen(listOf(source, skipped), f.manager.zero, Reach.NORMAL) { _, _ ->
                    outputs++
                    f.cancellation.cancel()
                }
            }
            assertEquals(1, outputs)
        }
    }

    @Test fun `actual unchanged cycles terminate across quanta and a later burst can repeat`(): Unit {
        for (index in listOf(1, 2)) MethodAnalysisFixtures(listOf(listOf(1), listOf(1, 2), emptyList())).use { f ->
            val core = f.core(index = index)
            core.addRootZero()
            val attempts = f.drain(core, quantum = 1)
            assertTrue(attempts >= 3)
            assertEquals(setOf(0, 1, 2), f.processed.map { it.node.location.index }.toSet())
            assertTrue(f.cuts.none { it.first == Cut.STATEMENT })
            val previousSteps = core.steps
            val previousDeltas = core.edgeDeltas
            val first = f.processed.first()
            core.emitUnchanged(first)
            f.drain(core, quantum = 1)
            assertTrue(core.steps > previousSteps)
            assertEquals(previousDeltas, core.edgeDeltas)
            assertTrue(core.work(1))
        }
    }

    @Test fun `cancellation on an untouched statement stops the actor before successor propagation`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val core = f.core()
            core.addRootZero()
            f.onProcess = { _, _ -> f.cancellation.cancel() }
            assertFailsWith<Cancellation.Cancelled> { core.work(1000) }
            assertEquals(1L, core.steps)
            assertTrue(f.publications.isEmpty())
            assertFailsWith<Cancellation.Cancelled> { core.summaries.all().none() }
            f.cancellation.activate()
            assertTrue(core.summaries.all().none())
        }
    }

    @Test fun `publication failure propagates and cannot report completion or lose its pending batch`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val core = f.core()
            val facts = f.run1Fact()
            val failure = AssertionError("publication")
            core.emitAfter(f.manager.zero, f.inst(2), facts)
            f.onPublish = { _, _ -> throw failure }
            assertSame(failure, assertFailsWith<AssertionError> { core.work(10) })
            assertEquals(1, f.publications.size)
            f.onPublish = null
            assertTrue(core.work(10))
            assertEquals(2, f.publications.size)
            assertEquals(facts, f.publications[0].single().conclusion)
            assertSame(f.publications[0].single(), f.publications[1].single())
            assertEquals(1L, core.edgeDeltas)
        }
    }

    @Test fun `freeze rejects pending work and work quantum is positive`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val core = f.core()
            assertFailsWith<IllegalArgumentException> { core.work(0) }
            assertFailsWith<IllegalArgumentException> { core.work(-1) }
            core.addRootZero()
            assertFails { core.freeze() }
            f.drain(core)
            core.freeze()
            assertFalse(core.hasZeroWork)
            assertFails { core.work(1) }
            assertFails { core.runnerPort }
            assertFails { core.context }
            assertFails { core.config }
            assertFails { core.localServices }
        }
    }
}
