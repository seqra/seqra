package org.opentaint.dataflow.jvm.ap.ifds.alias

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntIntImmutablePair
import it.unimi.dsi.fastutil.ints.IntIntMutablePair
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.opentaint.dataflow.jvm.ap.ifds.alias.DSUAliasAnalysis.DsuMergeStrategy
import org.opentaint.dataflow.util.forEachInt
import org.opentaint.dataflow.util.mapIntTo
import kotlin.collections.forEach

interface ImmutableState {
    fun mutableCopy(): State
}

enum class MergeType{
    May, Must
}

class State private constructor(
    val manager: AAInfoManager,
    val aliasGroups: IntDisjointSets,
) : ImmutableState {

    override fun hashCode(): Int = error("Unsupported operation")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is State) return false

        /**
         * We don't need to align heap instances here.
         * Since set repr selection is deterministic due to strategy,
         * if sets are equal their repr are also equal.
         * So, heap instances must be equal.
         * */
        return aliasGroups == other.aliasGroups
    }

    fun asImmutable(): ImmutableState = this

    override fun mutableCopy(): State = State(manager, aliasGroups.mutableCopy())

    fun removeUnsafe(infos: IntOpenHashSet): State {
        if (infos.isEmpty()) return this

        val normalizedInfos = fixHeapElementInstance(infos)
        val result = aliasGroups.mutableCopy()

        val removedInstances = IntOpenHashSet()
        result.prepareRemoveAll(normalizedInfos, removedInstances)

        val removeAfterHeapFix = IntOpenHashSet()
        restoreHeapInvariant(manager, result, removeAfterHeapFix)

        // since we use prepare-remove, old replaced roots are still in the DSU
        removeAfterHeapFix.addAll(normalizedInfos)
        result.removeAll(removeAfterHeapFix)

        if (removedInstances.isEmpty()) {
            return State(manager, result)
        }

        val removedHeap = IntOpenHashSet()
        result.allElements().forEachInt {
            if (!manager.isHeapAlias(it)) return@forEachInt

            val heapElement = manager.getHeapRefUnchecked(it)
            if (removedInstances.contains(heapElement.instance)) {
                removedHeap.add(it)
            }
        }

        return State(manager, result).removeUnsafe(removedHeap)
    }

    fun aliasGroupId(info: Int): Int = aliasGroups.find(info)
    fun aliasGroupRepr(groupId: Int): Int = aliasGroups.find(groupId)

    fun mergeAliasSets(aliasSets: IntOpenHashSet): State {
        if (aliasSets.size < 2) return this

        val firstRepr = aliasSets.intIterator().nextInt()
        val relations = mutableListOf<IntIntMutablePair>()
        aliasSets.forEachInt {
            if (it == firstRepr) return@forEachInt
            relations += IntIntMutablePair(firstRepr, it)
        }

        val result = aliasGroups.mutableCopy()
        mergeUnionRelations(relations, result, manager)

        return State(manager, result)
    }

    fun forEachAliasInSet(info: Int, body: (Int) -> Unit) = forEachAliasInSetWithBreak(info, body)

    fun forEachAliasInSetWithBreak(info: Int, body: (Int) -> Unit?) {
        aliasGroups.forEachElementInSet(info, body)
    }

    fun allAliasSets(): Collection<IntOpenHashSet> = aliasGroups.allSets()

    fun allSetElements(): IntOpenHashSet = aliasGroups.allElements()

    override fun toString(): String = buildString {
        for (aliasSet in allAliasSets()) {
            appendLine("{")
            aliasSet.forEachInt {
                appendLine("\t($it) -> ${manager.getElementUncheck(it)}")
            }
            appendLine("}")
        }
    }

    private fun fixHeapElementInstance(elements: IntOpenHashSet) =
        elements.mapIntTo(IntOpenHashSet(elements.size)) {
            ensureHeapElementCorrect(it, aliasGroups, manager)
        }

    companion object {
        fun empty(manager: AAInfoManager, strategy: DsuMergeStrategy): State =
            State(manager, IntDisjointSets(strategy))

        private fun restoreHeapInvariant(
            manager: AAInfoManager,
            state: IntDisjointSets,
            elementsToRemove: IntOpenHashSet,
        ) {
            while (true) {
                val replacements = mutableListOf<IntIntImmutablePair>()

                state.allElements().forEachInt { elementIdx ->
                    if (elementsToRemove.contains(elementIdx)) return@forEachInt

                    val fixedHeap = ensureHeapElementCorrect(elementIdx, state, manager)
                    if (fixedHeap == elementIdx) return@forEachInt

                    replacements += IntIntImmutablePair(elementIdx, fixedHeap)
                }

                if (replacements.isEmpty()) return

                for (replacement in replacements) {
                    elementsToRemove.add(replacement.leftInt())
                    state.union(replacement.leftInt(), replacement.rightInt())
                }
            }
        }

        private fun ensureHeapElementCorrect(element: Int, state: IntDisjointSets, manager: AAInfoManager): Int {
            if (!manager.isHeapAlias(element)) return element

            val heapElement = manager.getHeapRefUnchecked(element)
            val heapInstanceRepr = state.find(heapElement.instance)
            if (heapInstanceRepr == heapElement.instance) return element

            return manager.replaceHeapInstance(element, heapInstanceRepr)
        }

        fun merge(manager: AAInfoManager, strategy: DsuMergeStrategy, states: List<ImmutableState>, mergeType: MergeType): State {
            val allAliasGroups = states.map { (it as State).aliasGroups }

            val relations = when (mergeType) {
                MergeType.May -> mergeMay(allAliasGroups)
                MergeType.Must -> mergeMust(allAliasGroups)
            }

            val result = IntDisjointSets(strategy)
            mergeUnionRelations(relations, result, manager)

            return State(manager, result)
        }

        fun mergeMay(allAliasGroups: List<IntDisjointSets>): List<IntIntMutablePair> {
            val allElementParentRelations = mutableListOf<IntIntMutablePair>()
            allAliasGroups.forEach { a ->
                a.collectElementParentPairs(allElementParentRelations)
            }
            return allElementParentRelations
        }

        fun mergeMust(allAliasGroups: List<IntDisjointSets>): List<IntIntMutablePair> {
            val elementsInAll = IntOpenHashSet()
            val allSetElements = allAliasGroups.map { it.allElements() }
            var first = true
            allSetElements.forEach { set ->
                if (first) {
                    elementsInAll.addAll(set)
                    first = false
                    return@forEach
                }
                elementsInAll.removeAll { element -> !set.contains(element) }
            }

            if (elementsInAll.isEmpty()) return emptyList()

            val resultRelations = mutableListOf<IntIntMutablePair>()
            val map = Object2IntOpenHashMap<IntArrayList>()
            val totalStates = allAliasGroups.size
            elementsInAll.forEachInt { element ->
                val elementSignature = IntArrayList(totalStates)
                allAliasGroups.forEach { aliasGroup ->
                    elementSignature.add(aliasGroup.find(element))
                }
                if (map.contains(elementSignature)) {
                    val parent = map.getInt(elementSignature)
                    resultRelations.add(IntIntMutablePair(parent, element))
                }
                else {
                    map.put(elementSignature, element)
                }
            }

            return resultRelations
        }

        private fun mergeUnionRelations(
            relations: List<IntIntMutablePair>,
            result: IntDisjointSets,
            manager: AAInfoManager
        ) {
            val removedElements = IntOpenHashSet()
            while (true) {
                var modified = false
                relations.forEach {
                    val status = result.union(it.leftInt(), it.rightInt())
                    modified = modified or status
                }

                if (!modified) break

                restoreHeapInvariant(manager, result, removedElements)

                relations.forEach { relation ->
                    val fixedLeft = ensureHeapElementCorrect(relation.leftInt(), result, manager)
                    val fixedRight = ensureHeapElementCorrect(relation.rightInt(), result, manager)

                    relation.left(fixedLeft)
                    relation.right(fixedRight)
                }
            }
            result.removeAll(removedElements)
        }
    }
}
