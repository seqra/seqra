package org.opentaint.dataflow.bidi.fixtures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BidiFixtureTest {
    @Test
    fun `raw fact identity retains every annotation`() {
        val original = BidiFixtures.annotatedFlow
        val p = original.pattern
        val changes = listOf(
            original.copy(pattern = p.copy(base = BidiFixtures.y)),
            original.copy(pattern = p.copy(path = listOf(BidiFixtures.g))),
            original.copy(pattern = p.copy(tail = FixtureTail.MAY)),
            original.copy(pattern = p.copy(fieldExclusion = emptySet())),
            original.copy(pattern = p.copy(mark = FixtureMark.Concrete(BidiFixtures.T))),
            original.copy(pattern = p.copy(mark = FixtureMark.Abstract(setOf(BidiFixtures.U)))),
            original.copy(layer = FixtureLayer.DEMAND),
        )
        for (changed in changes) assertNotEquals(original, changed)
        assertEquals(changes.size + 1, (changes + original).toSet().size)
    }

    @Test
    fun `output order is independent but duplicate facts and requests remain visible`() {
        val a = BidiFixtures.annotatedFlow
        val b = BidiFixtures.must
        val request = BidiFixtures.staticPositionRequest
        val first = FixtureResult(listOf(a, b, a), setOf(request))
        val reordered = FixtureResult(listOf(a, a, b), setOf(request))
        assertEquals(first.factCounts(), reordered.factCounts())
        assertNotEquals(first.factCounts(), FixtureResult(listOf(a, b)).factCounts())
        assertNotEquals(first.requests, FixtureResult(first.facts).requests)
    }

    @Test
    fun `requests retain method context incoming premise and static position`() {
        val request = BidiFixtures.staticPositionRequest
        assertEquals(BidiFixtures.flowPremise, request.premise)
        assertEquals(listOf(BidiFixtures.classAccessor, BidiFixtures.f),
            (request.kind as FixtureRequestKind.Position).path)
        assertNotEquals(request, request.copy(method = BidiFixtures.otherContext))
        assertNotEquals(request, request.copy(premise = BidiFixtures.a))
        assertNotEquals(request, request.copy(kind = FixtureRequestKind.Mark(BidiFixtures.T)))
    }

    @Test
    fun `method keys keep context and forward entry independently`() {
        assertEquals(3, setOf(BidiFixtures.method, BidiFixtures.otherContext, BidiFixtures.otherEntry).size)
        assertEquals(BidiFixtures.method.method, BidiFixtures.otherContext.method)
        assertEquals(BidiFixtures.method.forwardEntry, BidiFixtures.otherContext.forwardEntry)
    }

    @Test
    fun `statement and exit forms are stable per method in both directions`() {
        val forms = StableFixtureForms()
        for (kind in listOf(FixtureFormKind.STATEMENT, FixtureFormKind.EXIT)) {
            for (direction in FixtureDirection.entries) {
                val first = forms.get(BidiFixtures.method, kind, "node-1", direction)
                assertSame(first, forms.get(BidiFixtures.method, kind, "node-1", direction))
                assertSame(first, forms.get(BidiFixtures.otherContext, kind, "node-1", direction))
                assertSame(first, forms.get(BidiFixtures.otherEntry, kind, "node-1", direction))
            }
            assertNotSame(forms.get(BidiFixtures.method, kind, "node-1", FixtureDirection.FORWARD),
                forms.get(BidiFixtures.method, kind, "node-1", FixtureDirection.BACKWARD))
        }
    }

    @Test
    fun `call and entry forms keep method key call site direction and alternative`() {
        val forms = StableFixtureForms()
        for (kind in listOf(FixtureFormKind.CALL, FixtureFormKind.ENTRY)) {
            val first = forms.get(BidiFixtures.method, kind, "call-1", alternative = "rule-A")
            assertSame(first, forms.get(BidiFixtures.method, kind, "call-1", alternative = "rule-A"))
            assertNotSame(first, forms.get(BidiFixtures.otherContext, kind, "call-1", alternative = "rule-A"))
            assertNotSame(first, forms.get(BidiFixtures.otherEntry, kind, "call-1", alternative = "rule-A"))
            assertNotSame(first, forms.get(BidiFixtures.method, kind, "call-2", alternative = "rule-A"))
            assertNotSame(first, forms.get(BidiFixtures.method, kind, "call-1", alternative = "rule-B"))
            assertNotSame(first, forms.get(BidiFixtures.method, kind, "call-1", FixtureDirection.BACKWARD, "rule-A"))
        }
    }

    @Test
    fun `links keep caller layer separately from added fact and link layer`() {
        val caller = FixtureCaller(BidiFixtures.method, setOf(BidiFixtures.flowPremise), FixtureLayer.NORMAL, "call-1")
        val link = FixtureLink(BidiFixtures.otherContext, caller, FixtureLayer.DEMAND, BidiFixtures.may)
        assertEquals(FixtureLayer.NORMAL, link.caller.layer)
        assertEquals(FixtureLayer.DEMAND, link.linkLayer)
        assertEquals(FixtureLayer.DEMAND, link.added.layer)
        assertNotEquals(link, link.copy(caller = caller.copy(call = "call-2")))
        assertNotEquals(link, link.copy(caller = caller.copy(layer = FixtureLayer.DEMAND)))
        assertNotEquals(link, link.copy(linkLayer = FixtureLayer.NORMAL))
    }

    @Test
    fun `current must demotion and handoff data remove field exclusion`() {
        assertEquals(FixtureTail.MUST, BidiFixtures.must.pattern.tail)
        assertEquals(setOf(BidiFixtures.g), BidiFixtures.must.pattern.fieldExclusion)
        assertEquals(FixtureLayer.NORMAL, BidiFixtures.must.layer)
        assertEquals(FixtureTail.MAY, BidiFixtures.mustDemotion.facts.single().pattern.tail)
        assertEquals(FixtureLayer.DEMAND, BidiFixtures.mustDemotion.facts.single().layer)
        assertTrue(BidiFixtures.mustDemotion.facts.single().pattern.fieldExclusion.isEmpty())
        assertTrue(BidiFixtures.selectedMustHandOff.all { it.entry == BidiFixtures.may.pattern })
        assertEquals(BidiFixtures.normalMustConjunction.premises,
            BidiFixtures.selectedMustHandOff.map { it.exit }.toSet())
    }

    @Test
    fun `normalization data retain star field exclusion and forget abstract mark exclusion`() {
        val normalized = BidiFixtures.normalizedFlowHandOff.entry
        assertEquals(BidiFixtures.annotatedFlow.pattern.fieldExclusion, normalized.fieldExclusion)
        assertEquals(FixtureTail.STAR, normalized.tail)
        assertEquals(FixtureMark.Abstract(), normalized.mark)
    }

    @Test
    fun `same-base cleaner data retain geometry and request already excluded mark`() {
        val input = BidiFixtures.sameBaseCleanerInput
        val result = BidiFixtures.sameBaseCleanerExpected
        assertEquals(input.pattern.copy(mark = FixtureMark.Abstract(setOf(BidiFixtures.T, BidiFixtures.U))),
            result.facts.single().pattern)
        assertEquals(input.layer, result.facts.single().layer)
        assertEquals(setOf(FixtureRequest(BidiFixtures.method, BidiFixtures.flowPremise,
            FixtureRequestKind.Mark(BidiFixtures.T))), result.requests)
        assertEquals(result.requests, BidiFixtures.alreadyExcludedCleanerExpected.requests)
        assertEquals(listOf(BidiFixtures.annotatedFlow), BidiFixtures.alreadyExcludedCleanerExpected.facts)
    }

    @Test
    fun `normal conjunction data preserve layer and make one handoff per member`() {
        val summary = BidiFixtures.normalConjunction
        assertEquals(FixtureLayer.NORMAL, summary.conclusion.layer)
        assertEquals(summary.premises, BidiFixtures.conjunctionHandOff.map { it.exit }.toSet())
        assertTrue(BidiFixtures.conjunctionHandOff.all { it.entry == summary.conclusion.pattern })
        assertEquals(setOf(BidiFixtures.a, BidiFixtures.b),
            BidiFixtures.reversedConjunctionBranches.facts.map { it.pattern }.toSet())
        assertTrue(BidiFixtures.reversedConjunctionBranches.facts.all { it.layer == FixtureLayer.DEMAND })
    }

    @Test
    fun `native must reader data retain exclusion distinct marks and exact target`() {
        assertEquals(FixtureTail.STAR, BidiFixtures.nativeReadPremise.tail)
        assertEquals(BidiFixtures.must.pattern.fieldExclusion, BidiFixtures.nativeReadPremise.fieldExclusion)
        assertTrue(BidiFixtures.nativeReadIncoming.pattern.path.size > BidiFixtures.nativeReadPremise.path.size)
        assertNotEquals(BidiFixtures.nativeReadPremise.mark, BidiFixtures.nativeReadTarget.pattern.mark)
        assertEquals(BidiFixtures.nativeInput, BidiFixtures.nativeReadExpected.facts.single().pattern)
        assertEquals(FixtureTail.EXACT, BidiFixtures.nativeReadExpected.facts.single().pattern.tail)
        assertEquals(FixtureLayer.NORMAL, BidiFixtures.nativeReadExpected.facts.single().layer)
    }
}
