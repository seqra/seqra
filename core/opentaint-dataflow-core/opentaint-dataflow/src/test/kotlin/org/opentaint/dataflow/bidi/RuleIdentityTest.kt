package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

class RuleIdentityTest {
    private val fixture = StoreFixtures()
    private val owner = FormOwner()
    private fun form(context: Boolean = true) = FormKey(owner, fixture.commonMethod,
        if (context) fixture.method else null, fixture.inst(2))
    private fun action(role: ActionRole = ActionRole.BOUND_CLEANER, ordinal: Int = 0) =
        ActionId(RuleOccurrenceId(form(), RuleKind.CLEANER, 0), role, ordinal)

    @Test fun `generated bases are equal across rebuilds while manual bases stay fresh`() {
        val first = AccessPathBase.CleanerTemporary.keyed(TemporaryKey(action()))
        val rebuilt = AccessPathBase.CleanerTemporary.keyed(TemporaryKey(action()))
        assertNotSame(first, rebuilt)
        assertEquals(first, rebuilt)
        assertEquals(first.hashCode(), rebuilt.hashCode())
        assertNotEquals(AccessPathBase.CleanerTemporary(), AccessPathBase.CleanerTemporary())
        assertNotEquals(first, AccessPathBase.CleanerTemporary())
    }

    @Test fun `stage action mark and generated step keep separate keys`() {
        val ordinary = action()
        val variants = listOf(ordinary, action(ActionRole.RETURN_REWRITER), action(ordinal = 1),
            ordinary.copy(relevantMarkOrdinal = 1), ordinary.copy(generatedStep = 1))
        assertEquals(variants.size, variants.map { AccessPathBase.CleanerTemporary.keyed(TemporaryKey(it)) }.toSet().size)
        assertNotEquals(TemporaryKey(ordinary), TemporaryKey(ordinary, substep = 1))
    }

    @Test fun `configuration owner context and occurrence ordinal keep rules separate`() {
        val original = RuleOccurrenceId(form(), RuleKind.CALL_SINK, 0)
        assertEquals(original, RuleOccurrenceId(form(), RuleKind.CALL_SINK, 0))
        assertNotEquals(original, original.copy(ordinal = 1))
        assertNotEquals(original, original.copy(kind = RuleKind.EXIT_SINK))
        assertNotEquals(original, original.copy(form = form(false)))
        assertNotEquals(original, original.copy(form = form().copy(owner = FormOwner())))
    }
}
