package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.ClassStatic
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.PositionAccessor
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.dataflow.jvm.ap.ifds.taint.resolveAp
import org.opentaint.dataflow.taint.base
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JIRInternalBaseResolutionTest {
    private val positions: List<Position> = listOf(
        Argument(0), This, Result, ClassStatic("Box"),
        PositionWithAccess(Argument(0), PositionAccessor.ElementAccessor),
    )

    @Test
    fun `explicit base resolution rejects internal bases for every position shape`() {
        for (position in positions) {
            for (base in listOf(AccessPathBase.Zero, AccessPathBase.CleanerTemporary())) {
                assertFailsWith<IllegalStateException> { position.resolveAp(base) }
            }
        }
    }

    @Test
    fun `explicit program base mappings still retain the supplied base`() {
        for (position in positions) {
            assertEquals(AccessPathBase.LocalVar(1), position.resolveAp(AccessPathBase.LocalVar(1)).base())
        }
        assertEquals(AccessPathBase.Argument(0), Argument(0).resolveAp().base())
    }
}
