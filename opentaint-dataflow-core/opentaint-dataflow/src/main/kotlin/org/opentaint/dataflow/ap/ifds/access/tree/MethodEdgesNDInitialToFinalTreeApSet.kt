package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.access.common.CommonNDF2FSet
import org.opentaint.dataflow.ap.ifds.access.common.ndf2f.DefaultNDF2FSetStorage
import org.opentaint.ir.api.common.cfg.CommonInst

class MethodEdgesNDInitialToFinalTreeApSet(
    methodInitialStatement: CommonInst,
    maxInstIdx: Int,
    languageManager: LanguageManager,
) : CommonNDF2FSet<AccessPath.AccessNode?, AccessTree.AccessNode>(methodInitialStatement, languageManager, maxInstIdx),
    TreeInitialApAccess, TreeFinalApAccess {

    override fun createApStorage() = object : DefaultNDF2FSetStorage<AccessPath.AccessNode?, AccessTree.AccessNode>() {
        override fun createStorage(): Storage<AccessTree.AccessNode> = DefaultStorage()
    }

    private class DefaultStorage : DefaultNDF2FSetStorage.Storage<AccessTree.AccessNode> {
        private var current: AccessTree.AccessNode? = null

        override fun add(element: AccessTree.AccessNode): AccessTree.AccessNode? {
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

        override fun collect(dst: MutableList<AccessTree.AccessNode>) {
            current?.let { dst.add(it) }
        }
    }
}
