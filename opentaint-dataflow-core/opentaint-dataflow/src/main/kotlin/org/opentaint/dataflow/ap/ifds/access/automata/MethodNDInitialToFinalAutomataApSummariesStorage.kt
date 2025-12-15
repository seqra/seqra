package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.access.common.CommonNDF2FSummary
import org.opentaint.dataflow.ap.ifds.access.common.ndf2f.DefaultNDF2FSummaryStorageWithAp
import org.opentaint.ir.api.common.cfg.CommonInst

class MethodNDInitialToFinalAutomataApSummariesStorage(methodEntryPoint: CommonInst) :
    CommonNDF2FSummary<AccessGraph>(methodEntryPoint), AutomataFinalApAccess {
    private class Builder : NDF2FBBuilder<AccessGraph>(), AutomataFinalApAccess

    override fun createStorage(): Storage<AccessGraph> =
        object : DefaultNDF2FSummaryStorageWithAp<AccessGraph, AccessGraph>(methodEntryPoint), AutomataInitialApAccess {
            override fun createBuilder(): NDF2FBBuilder<AccessGraph> = Builder()

            override fun createStorage(idx: Int): Storage<AccessGraph, AccessGraph> = FactStorage(idx)

            private inner class FactStorage(
                override val storageIdx: Int,
            ) : Storage<AccessGraph, AccessGraph> {
                private val agStorage = AccessGraphStorageWithCompression()

                override fun add(element: AccessGraph): Storage<AccessGraph, AccessGraph>? {
                    if (agStorage.add(element)) return this
                    return null
                }

                override fun getAndResetDelta(delta: MutableList<AccessGraph>) {
                    agStorage.mapAndResetDelta { delta.add(it) }
                }

                override fun collectTo(dst: MutableList<AccessGraph>) {
                    agStorage.allGraphsTo(dst)
                }
            }
        }
}
