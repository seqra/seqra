package org.opentaint.dataflow.jvm.ap.ifds.trace

import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactReader
import org.opentaint.dataflow.ap.ifds.PositionAccess
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.mkInitialAccessPath

class JIRPreconditionFactReader(
    private val apManager: ApManager
) : FactReader {
    val preconditions = hashSetOf<InitialFactAp>()

    override fun containsPosition(position: PositionAccess): Boolean {
        preconditions += apManager.mkInitialAccessPath(position, ExclusionSet.Universe)
        return true
    }

    override fun createInitialFactWithTaintMark(position: PositionAccess, mark: TaintMark): InitialFactAp {
        val positionWithMark = PositionAccess.Complex(position, TaintMarkAccessor(mark))
        return apManager.mkInitialAccessPath(positionWithMark, ExclusionSet.Universe)
    }
}
