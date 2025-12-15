package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.access.common.CommonZ2FSummary
import org.opentaint.dataflow.util.collectToListWithPostProcess
import org.opentaint.ir.api.common.cfg.CommonInst

class MethodFinalAutomataApSummariesStorage(methodEntryPoint: CommonInst) :
    CommonZ2FSummary<AccessGraph>(methodEntryPoint),
    AutomataFinalApAccess {

    override fun createStorage(): Storage<AccessGraph> = ApStorage()

    private class ApStorage : Storage<AccessGraph> {
        private val storage = AccessGraphStorageWithCompression()

        override fun add(edges: List<AccessGraph>, added: MutableList<Z2FBBuilder<AccessGraph>>) {
            edges.forEach { storage.add(it) }
            storage.mapAndResetDelta {
                added += ZeroToFactEdgeBuilderBuilder().setNode(it)
            }
        }

        override fun collectEdges(dst: MutableList<Z2FBBuilder<AccessGraph>>) {
            collectToListWithPostProcess(
                dst,
                { storage.allGraphsTo(it) },
                { ZeroToFactEdgeBuilderBuilder().setNode(it) }
            )
        }
    }

    private class ZeroToFactEdgeBuilderBuilder: Z2FBBuilder<AccessGraph>(), AutomataFinalApAccess
}
