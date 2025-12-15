package org.opentaint.dataflow.ap.ifds.access.common

import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges.EdgeStorage
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.MethodEdgesFinalApSet
import org.opentaint.dataflow.util.collectToListWithPostProcess
import org.opentaint.ir.api.common.cfg.CommonInst

abstract class CommonZ2FSet<AP>(
    methodInitialStatement: CommonInst,
): MethodEdgesFinalApSet, FinalApAccess<AP> {
    private val storage = BasedStorage(methodInitialStatement)

    interface ApStorage<AP> {
        fun addEdge(statement: CommonInst, accessPath: AP): AP?
        fun collectApAtStatement(statement: CommonInst, dst: MutableList<AP>)
    }

    private inner class BasedStorage(initialStatement: CommonInst) : EdgeStorage<ApStorage<AP>>(initialStatement) {
        override fun createStorage(): ApStorage<AP> = createApStorage()
    }

    abstract fun createApStorage(): ApStorage<AP>

    override fun add(statement: CommonInst, ap: FinalFactAp): FinalFactAp? {
        val edgeSet = storage.getOrCreate(ap.base)

        val edgeAccess = getFinalAccess(ap)
        val addedAccess = edgeSet.addEdge(statement, edgeAccess) ?: return null
        if (addedAccess === edgeAccess) return ap

        return createFinal(ap.base, addedAccess, ExclusionSet.Universe)
    }

    override fun collectApAtStatement(
        collection: MutableList<FinalFactAp>,
        statement: CommonInst,
        finalFactPattern: InitialFactAp,
    ) {
        val base = finalFactPattern.base
        val edgeStorage = storage.find(base) ?: return
        collectToListWithPostProcess(
            collection,
            { edgeStorage.collectApAtStatement(statement, it) },
            { createFinal(base, it, ExclusionSet.Universe) }
        )
    }
}
