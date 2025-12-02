package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.TaintMark

interface FactReader {
    fun containsPosition(position: PositionAccess): Boolean
    fun createInitialFactWithTaintMark(position: PositionAccess, mark: TaintMark): InitialFactAp

    fun containsPositionWithTaintMark(position: PositionAccess, mark: TaintMark): Boolean {
        val positionWithMark = PositionAccess.Complex(position, TaintMarkAccessor(mark.name))
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
        val positionWithMark = PositionAccess.Complex(position, TaintMarkAccessor(mark.name))
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

    fun replaceFact(factAp: FinalFactAp) = FinalFactReader(factAp, apManager).also { it.refinement = refinement }

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

    fun updateRefinement(other: FinalFactReader) {
        refinement = refinement.union(other.refinement)
    }
}

class FinalFactReaderWithPrefix(
    private val reader: FinalFactReader,
    private val prefix: Accessor,
) : FactReader {
    override fun containsPosition(position: PositionAccess): Boolean =
        reader.containsPosition(position.withPrefix(prefix))

    override fun createInitialFactWithTaintMark(position: PositionAccess, mark: TaintMark): InitialFactAp =
        reader.createInitialFactWithTaintMark(position.withPrefix(prefix), mark)
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
        val positionWithMark = PositionAccess.Complex(position, TaintMarkAccessor(mark.name))
        return apManager.mkInitialAccessPath(positionWithMark, ExclusionSet.Universe)
    }
}