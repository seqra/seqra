package org.opentaint.dataflow.ap.ifds.access.cactus

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.access.common.CommonNDF2FSet
import org.opentaint.dataflow.ap.ifds.access.common.ndf2f.DefaultNDF2FSetStorage
import org.opentaint.ir.api.common.cfg.CommonInst

class MethodEdgesNDInitialToFinalCactusApSet(
    initialStatement: CommonInst,
    languageManager: LanguageManager,
    maxInstIdx: Int,
) : CommonNDF2FSet<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode>(
    initialStatement, languageManager, maxInstIdx
), CactusFinalApAccess, CactusInitialApAccess {
    override fun createApStorage() =
        object : DefaultNDF2FSetStorage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode>() {
            override fun createStorage(): Storage<AccessCactus.AccessNode> = DefaultStorage()
        }

    override fun mostAbstractPattern(base: AccessPathBase): AccessPathWithCycles.AccessNode? = null

    private class DefaultStorage : DefaultNDF2FSetStorage.Storage<AccessCactus.AccessNode> {
        private var current: AccessCactus.AccessNode? = null

        override fun add(element: AccessCactus.AccessNode): AccessCactus.AccessNode? {
            val cur = current
            if (cur == null) {
                current = element
                return element
            }

            val mergedAccess = cur.mergeAdd(element)
            if (mergedAccess === cur) return null
            current = mergedAccess
            return mergedAccess
        }

        override fun collect(dst: MutableList<AccessCactus.AccessNode>) {
            current?.let { dst.add(it) }
        }
    }
}
