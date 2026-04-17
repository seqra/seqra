package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageIdx
import org.opentaint.dataflow.ap.ifds.access.common.CommonZ2FSet
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode as AccessTreeNode

class MethodEdgesFinalTreeApSet(
    methodInitialStatement: CommonInst,
    private val maxInstIdx: Int,
    private val languageManager: LanguageManager,
    override val apManager: TreeApManager,
) : CommonZ2FSet<AccessTreeNode>(methodInitialStatement), TreeFinalApAccess {
    override fun createApStorage(): ApStorage<AccessTreeNode> =
        ZeroInitialFactEdges(maxInstIdx, languageManager, apManager)

    private class ZeroInitialFactEdges(
        maxInstIdx: Int,
        private val languageManager: LanguageManager,
        manager: TreeApManager,
    ): TreeSetWithCompression(maxInstIdx, manager), ApStorage<AccessTreeNode> {
        override fun addEdge(statement: CommonInst, accessPath: AccessTreeNode): AccessTreeNode? {
            val factSetIdx = instructionStorageIdx(statement, languageManager)
            val factSet = edges[factSetIdx]

            if (factSet == null) {
                edges[factSetIdx] = internIfRequired(accessPath)
                return accessPath
            }

            val mergedFacts = factSet.mergeAdd(accessPath)
            if (mergedFacts === factSet) {
                return null
            }

            edges[factSetIdx] = internIfRequired(mergedFacts)
            intern(factSetIdx)
            return mergedFacts
        }

        override fun collectApAtStatement(statement: CommonInst, dst: MutableList<AccessTreeNode>) {
            dst += edges[instructionStorageIdx(statement, languageManager)] ?: return
        }
    }
}
