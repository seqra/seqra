package org.opentaint.dataflow.ap.ifds.access.cactus

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges
import org.opentaint.dataflow.ap.ifds.access.common.CommonF2FSet
import org.opentaint.dataflow.util.collectToListWithPostProcess
import org.opentaint.ir.api.common.cfg.CommonInst

class MethodEdgesInitialToFinalCactusApSet(
    methodInitialStatement: CommonInst,
    private val maxInstIdx: Int,
    private val languageManager: LanguageManager
) : CommonF2FSet<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode>(methodInitialStatement),
    CactusInitialApAccess, CactusFinalApAccess {
    override fun createApStorage(): ApStorage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode> =
        TaintedFactAccessEdgeStorage()

    override fun mostAbstractPattern(base: AccessPathBase): AccessPathWithCycles.AccessNode? = null

    private inner class TaintedFactAccessEdgeStorage :
        ApStorage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode> {
        val sameInitialAccessEdges =
            Object2ObjectOpenHashMap<AccessPathWithCycles.AccessNode?, EdgeNonUniverseExclusionMergingStorage>()

        override fun add(
            statement: CommonInst,
            initial: AccessPathWithCycles.AccessNode?,
            final: AccessWithExclusion<AccessCactus.AccessNode>
        ): AccessWithExclusion<AccessCactus.AccessNode>? {
            val storage = sameInitialAccessEdges.getOrPut(initial) {
                EdgeNonUniverseExclusionMergingStorage(maxInstIdx, languageManager)
            }

            return storage.add(statement, final)
        }

        override fun filter(
            dst: MutableList<Pair<AccessPathWithCycles.AccessNode?, AccessWithExclusion<AccessCactus.AccessNode>>>,
            statement: CommonInst,
            finalPattern: AccessPathWithCycles.AccessNode?,
        ) {
            sameInitialAccessEdges.forEach { (initial, storage) ->
                collectToListWithPostProcess(
                    dst,
                    { storage.allApAtStatement(it, statement) },
                    { initial to it }
                )
            }
        }

        override fun filter(
            dst: MutableList<AccessWithExclusion<AccessCactus.AccessNode>>,
            statement: CommonInst,
            initial: AccessPathWithCycles.AccessNode?,
            finalPattern: AccessPathWithCycles.AccessNode?,
        ) {
            val storage = sameInitialAccessEdges[initial] ?: return
            storage.allApAtStatement(dst, statement)
        }
    }

    private class EdgeNonUniverseExclusionMergingStorage(
        maxInstIdx: Int, private val languageManager: LanguageManager
    ) {
        private val exclusions = arrayOfNulls<ExclusionSet>(MethodAnalyzerEdges.instructionStorageSize(maxInstIdx))
        private val edges = arrayOfNulls<AccessCactus.AccessNode>(MethodAnalyzerEdges.instructionStorageSize(maxInstIdx))

        fun add(
            statement: CommonInst,
            accessWithExclusion: AccessWithExclusion<AccessCactus.AccessNode>,
        ): AccessWithExclusion<AccessCactus.AccessNode>? {
            val edgeSetIdx = MethodAnalyzerEdges.instructionStorageIdx(statement, languageManager)
            val currentExclusion = exclusions[edgeSetIdx]

            if (currentExclusion == null) {
                exclusions[edgeSetIdx] = accessWithExclusion.exclusion
                edges[edgeSetIdx] = accessWithExclusion.access
                return accessWithExclusion
            }

            val currentAccess = edges[edgeSetIdx]!!
            val mergedExclusion = currentExclusion.union(accessWithExclusion.exclusion)
            exclusions[edgeSetIdx] = mergedExclusion

            val mergedAccess = currentAccess.mergeAdd(accessWithExclusion.access)
            if (mergedAccess === currentAccess) return null

            edges[edgeSetIdx] = mergedAccess
            return AccessWithExclusion(mergedAccess, mergedExclusion)
        }

        fun allApAtStatement(dst: MutableList<AccessWithExclusion<AccessCactus.AccessNode>>, statement: CommonInst) {
            val edgeSetIdx = MethodAnalyzerEdges.instructionStorageIdx(statement, languageManager)
            val currentExclusion = exclusions[edgeSetIdx] ?: return
            val access = edges[edgeSetIdx] ?: return
            dst += AccessWithExclusion(access, currentExclusion)
        }
    }
}
