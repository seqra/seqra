package org.opentaint.dataflow.ap.ifds.access.tree

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges
import org.opentaint.dataflow.ap.ifds.access.common.CommonF2FSet
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode.Companion.extractMatchingPrefix
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode.Companion.extractMatchingSuffix
import org.opentaint.dataflow.util.collectToListWithPostProcess
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.concurrent.atomic.AtomicInteger

class MethodEdgesInitialToFinalTreeApSet(
    methodInitialStatement: CommonInst,
    private val maxInstIdx: Int,
    private val languageManager: LanguageManager,
    override val apManager: TreeApManager,
) : CommonF2FSet<AccessPath.AccessNode?, AccessTree.AccessNode>(methodInitialStatement),
    TreeInitialApAccess, TreeFinalApAccess {

    override fun createApStorage(): ApStorage<AccessPath.AccessNode?, AccessTree.AccessNode> =
        TaintedFactAccessEdgeStorage()

    override fun mostAbstractPattern(base: AccessPathBase): AccessPath.AccessNode? = null

    private inner class TaintedFactAccessEdgeStorage : ApStorage<AccessPath.AccessNode?, AccessTree.AccessNode> {
        private val sameInitialAccessEdges = IF2FFStorage(maxInstIdx, languageManager, apManager, size = AtomicInteger(0))

        override fun add(
            statement: CommonInst,
            initial: AccessPath.AccessNode?,
            final: AccessWithExclusion<AccessTree.AccessNode>,
        ): AccessWithExclusion<AccessTree.AccessNode>? {
            val storage = sameInitialAccessEdges.getOrCreateNode(initial).current

            if (sameInitialAccessEdges.size.get() > 1000) {
                val allEdges = Object2ObjectOpenHashMap<AccessPath.AccessNode?, Object2ObjectOpenHashMap<AccessPath.AccessNode, Object2ObjectOpenHashMap<AccessTree.AccessNode, ObjectOpenHashSet<AccessPath.AccessNode?>>>>()

                val nullSuffix = Object2ObjectOpenHashMap<AccessPath.AccessNode?, Object2ObjectOpenHashMap<AccessPath.AccessNode?, ObjectOpenHashSet<AccessTree.AccessNode>>>()
                val emptyPrefix = Object2ObjectOpenHashMap<AccessPath.AccessNode?, ObjectOpenHashSet<AccessPath.AccessNode?>>()

                val ifpp2eifps2ffp2s = Object2ObjectOpenHashMap<AccessPath.AccessNode?, Object2ObjectOpenHashMap<AccessTree.AccessNode, ObjectOpenHashSet<AccessPath.AccessNode?>>>()
                val ifpp2ifps2ffp2s = Object2ObjectOpenHashMap<AccessPath.AccessNode?, Object2ObjectOpenHashMap<AccessPath.AccessNode, Object2ObjectOpenHashMap<AccessTree.AccessNode, ObjectOpenHashSet<AccessPath.AccessNode?>>>>()

                sameInitialAccessEdges.forEachNode(apManager) { initial, storage ->
                    storage.current.edges.forEach { final ->
                        final ?: return@forEach

                        val treeSplit = final.extractMatchingSuffix(initial)
                        for ((prefix, suffix) in treeSplit) {
                            run {
                                val initialPrefix = initial?.extractPrefix(suffix)

                                val sharedPrefix = prefix.extractMatchingPrefix(initial)
                                for ((prefixRemainder, commonPrefix) in sharedPrefix) {
                                    val initialPrefixRemainder = initialPrefix?.extractSuffix(commonPrefix)

                                    allEdges
                                        .getOrPut(commonPrefix, ::Object2ObjectOpenHashMap)
                                        .getOrPut(initialPrefixRemainder, ::Object2ObjectOpenHashMap)
                                        .getOrPut(prefixRemainder, ::ObjectOpenHashSet)
                                        .add(suffix)
                                }
                            }


                            if (suffix == null) {
                                val sharedPrefix = prefix.extractMatchingPrefix(initial)
                                for ((prefixRemainder, commonPrefix) in sharedPrefix) {
                                    val initialPrefixRemainder = initial?.extractSuffix(commonPrefix)

                                    nullSuffix
                                        .getOrPut(commonPrefix, ::Object2ObjectOpenHashMap)
                                        .getOrPut(initialPrefixRemainder, ::ObjectOpenHashSet)
                                        .add(prefixRemainder)
                                }
                                continue
                            }

                            val initialPrefix = initial?.extractPrefix(suffix)

                            if (prefix === apManager.abstractNode) {
                                emptyPrefix
                                    .getOrPut(initialPrefix, ::ObjectOpenHashSet)
                                    .add(suffix)
                                continue
                            }

                            val sharedPrefix = prefix.extractMatchingPrefix(initialPrefix)
                            for ((prefixRemainder, commonPrefix) in sharedPrefix) {
                                val initialPrefixRemainder = initialPrefix?.extractSuffix(commonPrefix)

                                if (initialPrefixRemainder == null) {
                                    ifpp2eifps2ffp2s
                                        .getOrPut(commonPrefix, ::Object2ObjectOpenHashMap)
                                        .getOrPut(prefixRemainder, ::ObjectOpenHashSet)
                                        .add(suffix)
                                } else {
                                    ifpp2ifps2ffp2s
                                        .getOrPut(commonPrefix, ::Object2ObjectOpenHashMap)
                                        .getOrPut(initialPrefixRemainder, ::Object2ObjectOpenHashMap)
                                        .getOrPut(prefixRemainder, ::ObjectOpenHashSet)
                                        .add(suffix)
                                }
                            }
                        }
                    }
                }

                ifpp2ifps2ffp2s.let {  }
                allEdges.let {  }
            }

            return storage.add(statement, final)
        }

        override fun filter(
            dst: MutableList<Pair<AccessPath.AccessNode?, AccessWithExclusion<AccessTree.AccessNode>>>,
            statement: CommonInst,
            finalPattern: AccessPath.AccessNode?,
        ) {
            sameInitialAccessEdges.forEachNode(apManager) { initial, storage ->
                collectToListWithPostProcess(
                    dst,
                    { storage.current.allApAtStatement(it, statement) },
                    { initial to it }
                )
            }
        }

        override fun filter(
            dst: MutableList<AccessWithExclusion<AccessTree.AccessNode>>,
            statement: CommonInst,
            initial: AccessPath.AccessNode?,
            finalPattern: AccessPath.AccessNode?,
        ) {
            val storage = sameInitialAccessEdges.find(initial)?.current ?: return
            storage.allApAtStatement(dst, statement)
        }
    }

    private class IF2FFStorage(
        val maxInstIdx: Int,
        private val languageManager: LanguageManager,
        val manager: TreeApManager,
        val size: AtomicInteger
    ) : AccessBasedStorage<IF2FFStorage>() {
        val current = EdgeNonUniverseExclusionMergingStorage(maxInstIdx, languageManager, manager)

        override fun createStorage(): IF2FFStorage {
            size.getAndIncrement()
            return IF2FFStorage(maxInstIdx, languageManager, manager, size)
        }
    }

    private class EdgeNonUniverseExclusionMergingStorage(
        maxInstIdx: Int,
        private val languageManager: LanguageManager,
        manager: TreeApManager,
    ): TreeSetWithCompression(maxInstIdx, manager) {
        private val exclusions = arrayOfNulls<ExclusionSet>(MethodAnalyzerEdges.instructionStorageSize(maxInstIdx))

        fun add(
            statement: CommonInst,
            accessWithExclusion: AccessWithExclusion<AccessTree.AccessNode>
        ): AccessWithExclusion<AccessTree.AccessNode>? {
            val edgeSetIdx = MethodAnalyzerEdges.instructionStorageIdx(statement, languageManager)
            val currentExclusion = exclusions[edgeSetIdx]

            if (currentExclusion == null) {
                exclusions[edgeSetIdx] = accessWithExclusion.exclusion
                edges[edgeSetIdx] = internIfRequired(accessWithExclusion.access)
                return accessWithExclusion
            }

            val mergedExclusion = currentExclusion.union(accessWithExclusion.exclusion)
            exclusions[edgeSetIdx] = mergedExclusion

            val currentAccess = edges[edgeSetIdx]!!
            val mergedAccess = currentAccess.mergeAdd(accessWithExclusion.access)
            if (mergedAccess === currentAccess) {
                if (mergedExclusion === currentExclusion) return null

                return AccessWithExclusion(mergedAccess, mergedExclusion)
            }

            edges[edgeSetIdx] = internIfRequired(mergedAccess)
            intern(edgeSetIdx)

            return AccessWithExclusion(mergedAccess, mergedExclusion)
        }

        fun allApAtStatement(dst: MutableList<AccessWithExclusion<AccessTree.AccessNode>>, statement: CommonInst) {
            val edgeSetIdx = MethodAnalyzerEdges.instructionStorageIdx(statement, languageManager)
            val currentExclusion = exclusions[edgeSetIdx] ?: return
            val access = edges[edgeSetIdx] ?: return
            dst += AccessWithExclusion(access, currentExclusion)
        }
    }
}
