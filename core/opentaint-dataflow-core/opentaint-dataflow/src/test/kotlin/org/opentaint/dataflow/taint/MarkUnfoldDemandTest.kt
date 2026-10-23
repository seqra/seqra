package org.opentaint.dataflow.taint

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonInstLocation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the demand remembers, and -- the part that matters -- what it deliberately forgets.
 *
 * The key is the asking frame, the base and the mark. It is NOT the fact, and that is the whole
 * point: the fact being refined is itself the product of earlier answers, so keying on it makes
 * every round of the iteration its own question and the filter a no-op. Keying without it stops
 * the iteration, and gives up telling `arg0.*` apart from `arg0.x.*`.
 *
 * Both halves are pinned here, so a later change that "fixes" the conflation is seen for what
 * it is: the loop coming back.
 */
class MarkUnfoldDemandTest {

    private val frameA = MethodEntryPoint(EmptyMethodContext, FakeInst("a"))
    private val frameB = MethodEntryPoint(EmptyMethodContext, FakeInst("b"))

    private val mark = TaintMarkAccessor("tainted")
    private val otherMark = TaintMarkAccessor("other")

    private val x: Accessor = FieldAccessor("C", "x", "C")
    private val y: Accessor = FieldAccessor("C", "y", "C")

    private val base = AccessPathBase.Argument(0)
    private val otherBase = AccessPathBase.Argument(1)

    /**
     * The loss, stated as a test rather than as a comment: the two calls stand for the same
     * frame asking about the same base and mark, one of them about `arg0.*` and the other about
     * `arg0.y.*`. Nothing in the signature can tell them apart -- there is no fact parameter --
     * so the second is told `x` has been demanded already, and `arg0.y.x` is never asked for.
     */
    @Test
    fun `an accessor is fresh once, whatever the fact asking for it`() {
        val demand = MarkUnfoldDemand()

        assertEquals(listOf(x), demand.demand(frameA, base, mark, listOf(x)))
        assertEquals(emptyList(), demand.demand(frameA, base, mark, listOf(x)))
    }

    @Test
    fun `only the part that is new comes back`() {
        val demand = MarkUnfoldDemand()

        demand.demand(frameA, base, mark, listOf(x))
        assertEquals(listOf(y), demand.demand(frameA, base, mark, listOf(x, y)))
    }

    @Test
    fun `the frame, the base and the mark each separate questions`() {
        val demand = MarkUnfoldDemand()
        demand.demand(frameA, base, mark, listOf(x))

        assertEquals(listOf(x), demand.demand(frameB, base, mark, listOf(x)))
        assertEquals(listOf(x), demand.demand(frameA, otherBase, mark, listOf(x)))
        assertEquals(listOf(x), demand.demand(frameA, base, otherMark, listOf(x)))
    }

    @Test
    fun `nothing demanded is nothing fresh`() {
        val demand = MarkUnfoldDemand()

        assertEquals(emptyList(), demand.demand(frameA, base, mark, emptyList()))
    }

    /** The demand only ever uses an entry point as a map key, so identity is all it needs. */
    private class FakeInst(private val id: String) : CommonInst {
        override val location: CommonInstLocation get() = error("not used as a location")
        override fun toString(): String = id
    }
}
