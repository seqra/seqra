package org.opentaint.dataflow.jvm.ap.ifds.alias

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor.Field
import org.opentaint.dataflow.jvm.ap.ifds.alias.DSUAliasAnalysis.State
import org.opentaint.dataflow.jvm.ap.ifds.alias.LocalAlias.SimpleLoc
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals

class DSUAliasAnalysisStateTest {

    private val manager = AAInfoManager()
    private val strategy = DSUAliasAnalysis.DsuMergeStrategy(manager)

    private class StateBuilder(
        private val manager: AAInfoManager,
        private val strategy: DSUAliasAnalysis.DsuMergeStrategy
    ) {
        private var state = State.empty(manager, strategy)

        private val created = IdentityHashMap<AAInfo, Unit>()

        fun local(idx: Int): LocalAlias = create(
            SimpleLoc(RefValue.Local(idx, ContextInfo.rootContext))
        )

        fun unknown(originalIdx: Int): Unknown = create(
            Unknown(Stmt.Return(value = null, originalIdx = originalIdx), ContextInfo.rootContext)
        )

        fun arrayAlias(instanceInfo: AAInfo) = heapAlias(instanceInfo) { i -> HeapAlias(i, ArrayAlias) }

        fun fieldAlias(instanceInfo: AAInfo, fieldName: String) = heapAlias(instanceInfo) { i ->
            HeapAlias(i, FieldAlias(Field("Cls", fieldName, "I"), isImmutable = true))
        }

        private fun heapAlias(instance: AAInfo, body: (Int) -> HeapAlias): HeapAlias {
            val instanceId = infoId(instance)
            val instanceGroupId = state.aliasGroupId(instanceId)

            return create(body(instanceGroupId))
        }

        private fun <T : AAInfo> create(info: T): T {
            created[info] = Unit
            return info
        }

        fun merge(set: Set<AAInfo>) {
            val setIds = infoIds(set)
            state = state.mergeAliasSets(setIds)
        }

        fun remove(set: Set<AAInfo>) {
            val setIds = infoIds(set)
            state = state.removeUnsafe(setIds)
        }

        private fun infoId(info: AAInfo): Int {
            check(created.containsKey(info)) { "$info doesn't belongs to the current state" }
            return manager.getOrAdd(info)
        }

        private fun infoIds(set: Set<AAInfo>): IntOpenHashSet {
            val setIds = IntOpenHashSet()
            set.forEach { setIds.add(infoId(it)) }
            return setIds
        }

        fun build(): State = state

        fun mergeStates(vararg builders: StateBuilder) {
            val states = builders.map { it.state }
            this.state = State.merge(manager, strategy, states)

            builders.forEach {
                created.putAll(it.created)
            }
        }
    }

    private inline fun buildState(body: StateBuilder.() -> Unit): State =
        fillState(body).build()

    private inline fun fillState(body: StateBuilder.() -> Unit): StateBuilder {
        val builder = StateBuilder(manager, strategy)
        builder.body()
        return builder
    }

    @Test
    fun mergeAliasSetsOfTwoLocals() {
        val set1 = buildState {
            merge(setOf(local(0), local(1)))
            merge(setOf(local(0), local(1)))
        }

        val set2 = buildState {
            merge(setOf(local(0), local(1)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun mergeAliasSetsOfThreeElements() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            merge(setOf(a))
            merge(setOf(b))
            merge(setOf(c))
            merge(setOf(a, b, c))
        }

        val set2 = buildState {
            merge(setOf(local(0), local(1), local(2)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun mergeAliasSetsWithSingleElementIsNoop() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            merge(setOf(a))
            merge(setOf(b))
            merge(setOf(a))
        }

        val set2 = buildState {
            merge(setOf(local(0)))
            merge(setOf(local(1)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun mergeAliasSetsPreservesDisjointGroups() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            val d = local(3)
            merge(setOf(a))
            merge(setOf(b))
            merge(setOf(c))
            merge(setOf(d))
            merge(setOf(a, b))
        }

        val set2 = buildState {
            merge(setOf(local(0), local(1)))
            merge(setOf(local(2)))
            merge(setOf(local(3)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun removeUnsafeSingleElement() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            merge(setOf(a, b))
            remove(setOf(a))
        }

        val set2 = buildState {
            merge(setOf(local(1)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun removeUnsafeEmptySetIsNoop() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            merge(setOf(a, b))
            remove(emptySet())
        }

        val set2 = buildState {
            merge(setOf(local(0), local(1)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun removeUnsafeAllElements() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            merge(setOf(a, b))
            remove(setOf(a, b))
        }

        val set2 = buildState {}

        assertEquals(set2, set1)
    }

    @Test
    fun removeUnsafeFromMultipleGroups() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            val d = local(3)
            merge(setOf(a, b))
            merge(setOf(c, d))
            remove(setOf(a, c))
        }

        val set2 = buildState {
            merge(setOf(local(1)))
            merge(setOf(local(3)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun mergeTwoDisjointStates() {
        val set1 = buildState {
            mergeStates(
                fillState { merge(setOf(local(0), local(1))) },
                fillState { merge(setOf(local(2), local(3))) },
            )
        }

        val set2 = buildState {
            merge(setOf(local(0), local(1)))
            merge(setOf(local(2), local(3)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun mergeOverlappingStates() {
        val set1 = buildState {
            mergeStates(
                fillState { merge(setOf(local(0), local(1))) },
                fillState { merge(setOf(local(1), local(2))) },
            )
        }

        val set2 = buildState {
            merge(setOf(local(0), local(1), local(2)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun mergeSingleState() {
        val set1 = buildState {
            mergeStates(
                fillState { merge(setOf(local(0), local(1))) },
            )
        }

        val set2 = buildState {
            merge(setOf(local(0), local(1)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun mergeAliasSetsAfterRemoveUnsafe() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            val d = local(3)
            merge(setOf(a, b))
            merge(setOf(c, d))
            remove(setOf(b))
            merge(setOf(a, c))
        }

        val set2 = buildState {
            merge(setOf(local(0), local(2), local(3)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun removeUnsafeAfterMergeAliasSets() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            merge(setOf(a))
            merge(setOf(b))
            merge(setOf(c))
            merge(setOf(a, b, c))
            remove(setOf(b))
        }

        val set2 = buildState {
            merge(setOf(local(0), local(2)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun mergeStatesFollowedByMergeAliasSets() {
        val set1 = buildState {
            mergeStates(
                fillState { merge(setOf(local(0), local(1))) },
                fillState { merge(setOf(local(2), local(3))) },
            )
            merge(setOf(local(1), local(2)))
        }

        val set2 = buildState {
            merge(setOf(local(0), local(1), local(2), local(3)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun mergeStatesFollowedByRemoveUnsafe() {
        val set1 = buildState {
            mergeStates(
                fillState { merge(setOf(local(0), local(1))) },
                fillState { merge(setOf(local(2), local(3))) },
            )
            remove(setOf(local(0), local(3)))
        }

        val set2 = buildState {
            merge(setOf(local(1)))
            merge(setOf(local(2)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun mergeAliasSetsWithHeapAliases() {
        val set1 = buildState {
            val loc = local(0)
            val arr = arrayAlias(loc)
            val c = local(5)
            merge(setOf(loc, arr))
            merge(setOf(c))
            merge(setOf(loc, c))
        }

        val set2 = buildState {
            val loc = local(0)
            val arr = arrayAlias(loc)
            merge(setOf(loc, arr, local(5)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun removeUnsafeWithFieldAliases() {
        val set1 = buildState {
            val loc = local(0)
            val f = fieldAlias(loc, "x")
            val c = local(1)
            merge(setOf(loc, f, c))
            remove(setOf(f))
        }

        val set2 = buildState {
            merge(setOf(local(0), local(1)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun mergeStatesWithUnknownAliases() {
        val set1 = buildState {
            mergeStates(
                fillState { merge(setOf(local(0), unknown(0))) },
                fillState { merge(setOf(local(1), unknown(1))) },
            )
        }

        val set2 = buildState {
            merge(setOf(local(0), unknown(0)))
            merge(setOf(local(1), unknown(1)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun chainedMergeRemoveMerge() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            val d = local(3)
            val e = local(4)
            merge(setOf(a))
            merge(setOf(b))
            merge(setOf(c))
            merge(setOf(d))
            merge(setOf(e))
            merge(setOf(a, b))
            remove(setOf(c))
            merge(setOf(d, e))
        }

        val set2 = buildState {
            merge(setOf(local(0), local(1)))
            merge(setOf(local(3), local(4)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun mergeThreeOverlappingStates() {
        val set1 = buildState {
            mergeStates(
                fillState { merge(setOf(local(0), local(1))) },
                fillState { merge(setOf(local(1), local(2))) },
                fillState { merge(setOf(local(2), local(3))) },
            )
        }

        val set2 = buildState {
            merge(setOf(local(0), local(1), local(2), local(3)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun mergeStatesRemoveAndMergeAliasSets() {
        val set1 = buildState {
            mergeStates(
                fillState {
                    merge(setOf(local(0), local(1)))
                    merge(setOf(local(2)))
                },
                fillState { merge(setOf(local(3), local(4))) },
            )
            remove(setOf(local(2)))
            merge(setOf(local(0), local(3)))
        }

        val set2 = buildState {
            merge(setOf(local(0), local(1), local(3), local(4)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun mergeEmptyStates() {
        val set1 = buildState {
            mergeStates(
                fillState {},
                fillState {},
            )
        }

        val set2 = buildState {}

        assertEquals(set2, set1)
    }

    @Test
    fun mergeAliasSetsOnEmptySetIsNoop() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            merge(setOf(a))
            merge(setOf(b))
            merge(emptySet())
        }

        val set2 = buildState {
            merge(setOf(local(0)))
            merge(setOf(local(1)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun removeUnsafeThenMergeThenRemove() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            val d = local(3)
            val e = local(4)
            merge(setOf(a, b))
            merge(setOf(c))
            merge(setOf(d))
            merge(setOf(e))
            remove(setOf(a))
            merge(setOf(b, c))
            remove(setOf(d))
        }

        val set2 = buildState {
            merge(setOf(local(1), local(2)))
            merge(setOf(local(4)))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun deepHeapChainMerge() {
        val set1 = buildState {
            val loc = local(10)
            val d1 = arrayAlias(loc)
            val d2 = arrayAlias(d1)
            val d3 = arrayAlias(d2)
            merge(setOf(loc, d1))
            merge(setOf(d2, d3))
            merge(setOf(loc, d2))
        }

        val set2 = buildState {
            val loc = local(10)
            val d1 = arrayAlias(loc)
            val d2 = arrayAlias(d1)
            val d3 = arrayAlias(d2)
            merge(setOf(loc, d1, d2, d3))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun deepMixedHeapChainMergeAndRemove() {
        val set1 = buildState {
            val loc = local(30)
            val arr1 = arrayAlias(loc)
            val fld2 = fieldAlias(arr1, "f")
            val arr3 = arrayAlias(fld2)
            val e = local(99)
            merge(setOf(loc, arr1))
            merge(setOf(fld2, arr3))
            merge(setOf(e))
            merge(setOf(arr1, fld2))
            remove(setOf(e))
        }

        val set2 = buildState {
            val loc = local(30)
            val arr1 = arrayAlias(loc)
            val fld2 = fieldAlias(arr1, "f")
            val arr3 = arrayAlias(fld2)
            merge(setOf(loc, arr1, fld2, arr3))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun deepHeapChainStateMergeConnects() {
        val set1 = buildState {
            mergeStates(
                fillState {
                    val loc = local(40)
                    val h1 = arrayAlias(loc)
                    merge(setOf(loc, h1))
                },
                fillState {
                    val loc = local(40)
                    val h1 = arrayAlias(loc)
                    val h2 = arrayAlias(h1)
                    val h3 = arrayAlias(h2)
                    merge(setOf(h1, h2, h3))
                },
            )
        }

        val set2 = buildState {
            val loc = local(40)
            val h1 = arrayAlias(loc)
            val h2 = arrayAlias(h1)
            val h3 = arrayAlias(h2)
            merge(setOf(loc, h1, h2, h3))
        }

        assertEquals(set2, set1)
    }

    @Test
    fun equalityAfterDifferentMergeOrder() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            val d = local(3)
            merge(setOf(a))
            merge(setOf(b))
            merge(setOf(c))
            merge(setOf(d))
            merge(setOf(a, b))
            merge(setOf(c, d))
        }

        val set2 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            val d = local(3)
            merge(setOf(a))
            merge(setOf(b))
            merge(setOf(c))
            merge(setOf(d))
            merge(setOf(c, d))
            merge(setOf(a, b))
        }

        assertEquals(set1, set2)
    }

    @Test
    fun equalityMergeAllAtOnceVsIncrementally() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            merge(setOf(a))
            merge(setOf(b))
            merge(setOf(c))
            merge(setOf(a, b, c))
        }

        val set2 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            merge(setOf(a))
            merge(setOf(b))
            merge(setOf(c))
            merge(setOf(a, b))
            merge(setOf(b, c))
        }

        assertEquals(set1, set2)
    }

    @Test
    fun equalityRemoveThenMergeVsMergeFiltered() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            val d = local(3)
            merge(setOf(a, b))
            merge(setOf(c, d))
            remove(setOf(b, d))
            merge(setOf(a, c))
        }

        val set2 = buildState {
            merge(setOf(local(0), local(2)))
        }

        assertEquals(set1, set2)
    }

    @Test
    fun equalityStateMergeVsManualMergeAliasSets() {
        val set1 = buildState {
            mergeStates(
                fillState { merge(setOf(local(0), local(1))) },
                fillState { merge(setOf(local(1), local(2))) },
            )
        }

        val set2 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            merge(setOf(a))
            merge(setOf(b))
            merge(setOf(c))
            merge(setOf(a, b))
            merge(setOf(b, c))
        }

        assertEquals(set1, set2)
    }

    @Test
    fun equalityMergeTwoWaysWithDeepHeap() {
        val set1 = buildState {
            mergeStates(
                fillState {
                    val loc = local(90)
                    val h1 = arrayAlias(loc)
                    val h2 = fieldAlias(h1, "q")
                    val h3 = arrayAlias(h2)
                    merge(setOf(loc, h1, h2))
                    merge(setOf(h3))
                },
                fillState {
                    val loc = local(90)
                    val h1 = arrayAlias(loc)
                    val h2 = fieldAlias(h1, "q")
                    val h3 = arrayAlias(h2)
                    merge(setOf(h2, h3))
                    merge(setOf(local(91)))
                },
            )
        }

        val set2 = buildState {
            val loc = local(90)
            val h1 = arrayAlias(loc)
            val h2 = fieldAlias(h1, "q")
            val h3 = arrayAlias(h2)
            merge(setOf(loc))
            merge(setOf(h1))
            merge(setOf(h2))
            merge(setOf(h3))
            merge(setOf(local(91)))
            merge(setOf(loc, h1, h2, h3))
        }

        assertEquals(set1, set2)
    }

    @Test
    fun equalityChainedOpsVsDirectConstruction() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            val d = local(3)
            val e = local(4)
            val f = local(5)
            merge(setOf(a))
            merge(setOf(b))
            merge(setOf(c))
            merge(setOf(d))
            merge(setOf(e))
            merge(setOf(f))
            merge(setOf(a, b, c))
            remove(setOf(d))
            merge(setOf(e, f))
        }

        val set2 = buildState {
            merge(setOf(local(0), local(1), local(2)))
            merge(setOf(local(4), local(5)))
        }

        assertEquals(set1, set2)
    }

    @Test
    fun equalityMutableCopyChainVsOriginalChain() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            merge(setOf(a))
            merge(setOf(b))
            merge(setOf(c))
            merge(setOf(a, b))
            remove(setOf(c))
        }

        val set2 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            merge(setOf(a))
            merge(setOf(b))
            merge(setOf(c))
            merge(setOf(a, b))
            remove(setOf(c))
        }

        assertEquals(set1, set2)
    }

    @Test
    fun equalityRemoveOrderIndependence() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            val d = local(3)
            merge(setOf(a, b, c, d))
            remove(setOf(a))
            remove(setOf(c))
        }

        val set2 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            val d = local(3)
            merge(setOf(a, b, c, d))
            remove(setOf(c))
            remove(setOf(a))
        }

        assertEquals(set1, set2)
    }

    @Test
    fun equalityRemoveAtOnceVsOneByOne() {
        val set1 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            val d = local(3)
            merge(setOf(a, b, c, d))
            remove(setOf(a, c))
        }

        val set2 = buildState {
            val a = local(0)
            val b = local(1)
            val c = local(2)
            val d = local(3)
            merge(setOf(a, b, c, d))
            remove(setOf(a))
            remove(setOf(c))
        }

        assertEquals(set1, set2)
    }

    @Test
    fun equalityDeepChainMergeStateThenRemoveVsBuildDirect() {
        val set1 = buildState {
            mergeStates(
                fillState {
                    val loc = local(140)
                    val f1 = fieldAlias(loc, "a")
                    val f2 = fieldAlias(f1, "b")
                    val f3 = fieldAlias(f2, "c")
                    merge(setOf(loc, f1, f2, f3, local(141)))
                },
                fillState {
                    val loc = local(140)
                    val f1 = fieldAlias(loc, "a")
                    merge(setOf(loc, f1))
                },
            )
            remove(setOf(local(141)))
        }

        val set2 = buildState {
            val loc = local(140)
            val f1 = fieldAlias(loc, "a")
            val f2 = fieldAlias(f1, "b")
            val f3 = fieldAlias(f2, "c")
            merge(setOf(loc, f1, f2, f3))
        }

        assertEquals(set1, set2)
    }

    @Test
    fun equalityThreeStateMergeVsTwoStepMerge() {
        val set1 = buildState {
            mergeStates(
                fillState { merge(setOf(local(0), local(1))) },
                fillState { merge(setOf(local(1), local(2))) },
                fillState { merge(setOf(local(2), local(3))) },
            )
        }

        val set2 = buildState {
            val merged12 = fillState {
                mergeStates(
                    fillState { merge(setOf(local(0), local(1))) },
                    fillState { merge(setOf(local(1), local(2))) },
                )
            }
            mergeStates(
                merged12,
                fillState { merge(setOf(local(2), local(3))) },
            )
        }

        assertEquals(set1, set2)
    }

    @Test
    fun equalityMergeOrderDoesNotMatter() {
        val set1 = buildState {
            mergeStates(
                fillState { merge(setOf(local(0), local(1))) },
                fillState { merge(setOf(local(2), local(3))) },
                fillState { merge(setOf(local(1), local(2))) },
            )
        }

        val set2 = buildState {
            mergeStates(
                fillState { merge(setOf(local(1), local(2))) },
                fillState { merge(setOf(local(0), local(1))) },
                fillState { merge(setOf(local(2), local(3))) },
            )
        }

        assertEquals(set1, set2)
    }
}
