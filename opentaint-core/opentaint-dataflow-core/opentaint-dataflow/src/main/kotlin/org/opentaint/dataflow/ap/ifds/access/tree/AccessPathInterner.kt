package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.SummaryFactStorage
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.BitSet

class AccessPathInterner(methodEntryPoint: CommonInst) {
    private var size = 0
    private val storage = BasedStorage(methodEntryPoint)

    fun getOrCreateIndex(
        base: AccessPathBase,
        node: AccessPath.AccessNode?,
        onNewIdx: (Int) -> Unit,
    ): Int {
        val interner = storage.getOrCreate(base)
        return interner.getOrCreate(node) {
            val nextIdx = size++
            onNewIdx(nextIdx)
            nextIdx
        }
    }

    fun findBaseIndices(base: AccessPathBase): BitSet? = storage.find(base)?.allIndices

    private class BasedStorage(methodEntryPoint: CommonInst) :
        SummaryFactStorage<ApInterner>(methodEntryPoint) {
        override fun createStorage(): ApInterner = ApInterner()
    }

    private class ApInterner {
        val allIndices = BitSet()
        val root = ApInternerNode()

        inline fun getOrCreate(ap: AccessPath.AccessNode?, nextIdx: () -> Int): Int {
            val node = root.getOrCreateNode(ap)
            if (node.idx != -1) return node.idx

            val idx = nextIdx()
            node.idx = idx
            allIndices.set(idx)
            return idx
        }
    }

    private class ApInternerNode : AccessBasedStorage<ApInternerNode>() {
        var idx: Int = -1
        override fun createStorage(): ApInternerNode = ApInternerNode()
    }
}
