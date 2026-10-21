package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.fixtures.TestMark
import org.opentaint.dataflow.bidi.fixtures.testDeclarations
import kotlin.test.*

class PremiseCombinationTest {
    private val declarations = testDeclarations(
        TestMark("alpha", MarkGroupKey.Rule("AA")),
        TestMark("omega", MarkGroupKey.Rule("AA")),
        TestMark("other", MarkGroupKey.Rule("ZZ")),
    )
    private val fixture = StoreFixtures(markDeclarations = declarations)
    private val m = fixture.manager

    private fun initial(base: AccessPathBase, path: List<Int> = emptyList(), tail: Tail = Tail.EXACT,
                        mark: TaintMark = m.marks.full("alpha")): InitialAp =
        m.initial(base, m.path(path), tail, ExclusionSet.Empty, MarkSlot.Concrete(mark))

    @Test fun `exact policy keeps canonical unions and the manager remains exact`() {
        val a = initial(AccessPathBase.Argument(0))
        val b = initial(AccessPathBase.Argument(1))
        val exact = ExactPremises(m)
        val expected = m.union(a, b)
        assertIs<PremiseSet>(expected)
        assertSame(expected, exact.combine(listOf(b, m.zero, a, b)))
        assertSame(expected, m.union(b, a))
        assertSame(m.zero, exact.combine(listOf(m.zero, m.zero)))
    }

    @Test fun `an exact completed condition cannot have an empty input list`() {
        assertFails { ExactPremises(m).combine(emptyList()) }
    }
}
