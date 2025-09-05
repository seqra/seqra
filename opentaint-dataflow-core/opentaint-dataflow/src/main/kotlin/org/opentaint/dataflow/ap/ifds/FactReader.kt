package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

interface FactReader {
    fun containsPosition(position: PositionAccess): Boolean
    fun createInitialFactWithTaintMark(position: PositionAccess, mark: TaintMark): InitialFactAp

    fun containsPositionWithTaintMark(position: PositionAccess, mark: TaintMark): Boolean {
        val positionWithMark = PositionAccess.Complex(position, TaintMarkAccessor(mark))
        val finalPositionWithMark = PositionAccess.Complex(positionWithMark, FinalAccessor)
        return containsPosition(finalPositionWithMark)
    }
}

class FinalFactReader(
    val factAp: FinalFactAp,
    val apManager: ApManager
): FactReader {
    private var refinement: ExclusionSet = ExclusionSet.Empty
    val hasRefinement: Boolean get() = refinement !is ExclusionSet.Empty

    override fun createInitialFactWithTaintMark(position: PositionAccess, mark: TaintMark): InitialFactAp {
        val positionWithMark = PositionAccess.Complex(position, TaintMarkAccessor(mark))
        return apManager.mkInitialAccessPath(positionWithMark, ExclusionSet.Universe)
    }

    override fun containsPosition(position: PositionAccess): Boolean =
        readPosition(
            ap = factAp,
            position = position,
            onMismatch = { node, accessor ->
                if (accessor != null && node.isAbstract()) {
                    refinement = refinement.add(accessor)
                }
                false
            },
            matchedNode = { true }
        )

    fun refineFact(factAp: InitialFactAp): InitialFactAp {
        if (!hasRefinement) return factAp
        val refinedAp = factAp.replaceExclusions(factAp.exclusions.union(refinement))
        return refinedAp
    }

    fun refineFact(factAp: FinalFactAp): FinalFactAp {
        if (!hasRefinement) return factAp
        val refinedAp = factAp.replaceExclusions(factAp.exclusions.union(refinement))
        return refinedAp
    }
}

class InitialFactReader(val fact: InitialFactAp, val apManager: ApManager): FactReader {
    override fun containsPosition(position: PositionAccess): Boolean =
        readPosition(
            ap = fact,
            position = position,
            onMismatch = { _, _ -> false },
            matchedNode = { true }
        )

    override fun createInitialFactWithTaintMark(position: PositionAccess, mark: TaintMark): InitialFactAp {
        val positionWithMark = PositionAccess.Complex(position, TaintMarkAccessor(mark))
        return apManager.mkInitialAccessPath(positionWithMark, ExclusionSet.Universe)
    }
}