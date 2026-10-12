package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageIdx
import org.opentaint.dataflow.ap.ifds.access.common.CommonZ2FSet
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode as AccessTreeNode

class MethodEdgesFinalTreeApSet(
    methodInitialStatement: CommonInst,
    @Suppress("UNUSED_PARAMETER") maxInstIdx: Int,
    private val languageManager: LanguageManager,
    override val apManager: TreeApManager,
) : CommonZ2FSet<AccessTreeNode>(methodInitialStatement), TreeFinalApAccess {
    override fun createApStorage(): ApStorage<AccessTreeNode> =
        ZeroInitialFactEdges(languageManager, apManager)

    private class ZeroInitialFactEdges(
        private val languageManager: LanguageManager,
        manager: TreeApManager,
    ): TreeSetWithCompression(COLUMNS, manager), ApStorage<AccessTreeNode> {
        override fun addEdge(statement: CommonInst, accessPath: AccessTreeNode): AccessTreeNode? {
            val factSetIdx = instructionStorageIdx(statement, languageManager)
            val row = rowsForWrite(factSetIdx)
            val offset = offsetOf(row, factSetIdx)
            val factSet = row.values[offset] as AccessTreeNode?

            if (factSet == null) {
                row.values[offset] = internIfRequired(accessPath)
                return accessPath
            }

            val mergedFacts = factSet.mergeAdd(accessPath)
            if (mergedFacts === factSet) {
                return null
            }

            val storedFacts = internIfRequired(mergedFacts)
            row.values[offset] = storedFacts
            intern(storedFacts)
            return mergedFacts
        }

        override fun collectApAtStatement(statement: CommonInst, dst: MutableList<AccessTreeNode>) {
            val row = rows() ?: return
            val offset = offsetOf(row, instructionStorageIdx(statement, languageManager))
            if (offset < 0) return
            dst += row.values[offset] as AccessTreeNode? ?: return
        }

        private companion object {
            private const val COLUMNS = 1
        }
    }
}
