package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.engine.Triggers
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.bidi.interp.SinkRule
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.engine.Publication
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import kotlin.test.*

class MethodServicesTest {
    private val f = EngineShellFixtures()

    @Test fun `actual publication batches own their content and retain fact identity across later publication`(): Unit {
        MethodAnalysisFixtures().use { method ->
            val core = method.core()
            val batches = ArrayList<List<Publication>>()
            method.onPublishInput = { _, batch ->
                assertFailsWith<UnsupportedOperationException> { (batch as MutableList).clear() }
                batches += batch
            }
            val first = method.run1Fact()
            core.emitAfter(method.manager.zero, method.inst(2), first)
            method.drain(core)
            val publication = batches.single().single()
            assertEquals(first, publication.conclusion)
            val publishedFact = publication.conclusion
            core.emitAfter(method.manager.zero, method.inst(2),
                method.run1Fact(full = method.mark("next")))
            method.drain(core)
            assertEquals(2, batches.size)
            assertEquals(1, batches[0].size)
            assertSame(publication, batches[0].single())
            assertSame(publishedFact, batches[0].single().conclusion)
        }
    }

    @Test fun `trigger result owns containers and retains exact fact objects`(): Unit {
        val facts = arrayListOf(f.fact())
        val fired = arrayListOf<Pair<SinkRule, Layer>>()
        val result = Triggers(fired, facts)
        val original = facts.single()
        facts.clear()
        assertSame(original, result.parts.single())
        assertTrue(result.fired.isEmpty())
        assertFailsWith<UnsupportedOperationException> { (result.parts as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (result.fired as MutableList).clear() }
    }

    @Test fun `combining trigger results retains ordered literal parts without merging their facts`(): Unit {
        val a = f.fact()
        val b = f.fact(layer = Layer.DEMAND)
        val combined = Triggers(emptyList(), listOf(a)) + Triggers(emptyList(), listOf(b, a))
        assertEquals(3, combined.parts.size)
        assertSame(a, combined.parts[0])
        assertSame(b, combined.parts[1])
        assertSame(a, combined.parts[2])
        assertTrue((combined + Triggers.NONE).fired.isEmpty())
        assertEquals(combined.parts, (Triggers.NONE + combined).parts)
    }
}
