package org.opentaint.dataflow.jvm.ap.ifds.trace

import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.TaintMark
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.PositionAccess
import org.opentaint.dataflow.jvm.ap.ifds.taint.mkInitialAccessPath

class JIRPreconditionFactReader(
    private val apManager: ApManager
) : FactReader {
    val preconditions = hashSetOf<InitialFactAp>()

    override fun containsPosition(position: PositionAccess): Boolean {
        var normalizedPosition = position
        if (position is PositionAccess.Complex && position.accessor is FinalAccessor) {
            // mkInitialAccessPath starts with final ap
            normalizedPosition = position.base
        }
        preconditions += apManager.mkInitialAccessPath(normalizedPosition, ExclusionSet.Universe)
        return true
    }

    override fun createInitialFactWithTaintMark(position: PositionAccess, mark: TaintMark): InitialFactAp {
        val positionWithMark = PositionAccess.Complex(position, TaintMarkAccessor(mark.name))
        return apManager.mkInitialAccessPath(positionWithMark, ExclusionSet.Universe)
    }
}
