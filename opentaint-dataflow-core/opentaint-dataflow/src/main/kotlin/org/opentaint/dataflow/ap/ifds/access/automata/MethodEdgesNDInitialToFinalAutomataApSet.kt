package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.access.common.CommonNDF2FSet
import org.opentaint.dataflow.ap.ifds.access.common.ndf2f.DefaultNDF2FSetStorage
import org.opentaint.ir.api.common.cfg.CommonInst

class MethodEdgesNDInitialToFinalAutomataApSet(
    initialStatement: CommonInst,
    languageManager: LanguageManager,
    maxInstIdx: Int,
) : CommonNDF2FSet<AccessGraph, AccessGraph>(initialStatement, languageManager, maxInstIdx),
    AutomataInitialApAccess, AutomataFinalApAccess {
    override fun createApStorage() = object : DefaultNDF2FSetStorage<AccessGraph, AccessGraph>() {
        override fun createStorage(): Storage<AccessGraph> = DefaultStorage()
    }

    private class DefaultStorage : DefaultNDF2FSetStorage.Storage<AccessGraph> {
        private val storage = hashSetOf<AccessGraph>()
        override fun add(element: AccessGraph): AccessGraph? =
            if (storage.add(element)) element else null

        override fun collect(dst: MutableList<AccessGraph>) {
            dst.addAll(storage)
        }
    }
}
