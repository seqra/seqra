package org.opentaint.dataflow.jvm.ap.ifds.alias

import org.opentaint.dataflow.jvm.ap.ifds.alias.DSUAliasAnalysis.State
import org.opentaint.dataflow.jvm.ap.ifds.alias.JIRIntraProcAliasAnalysis.JIRInstGraph
import org.opentaint.dataflow.jvm.ap.ifds.alias.LocalAlias.SimpleLoc
import org.opentaint.ir.api.jvm.JIRMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration

class DSUAliasAnalysisInvalidateOuterHeapAliasesTest {

    private val analysis = DSUAliasAnalysis(
        methodCallResolver = object : CallResolver {
            override fun resolveMethodCall(callStmt: Stmt.Call, level: Int): List<JIRMethod>? = null
            override fun buildMethodGraph(method: JIRMethod): JIRInstGraph? = null
        },
        rootMethodReachabilityInfo = null,
        cancellation = AnalysisCancellation(timeLimit = Duration.INFINITE, parentCancellation = null),
    )
    private val manager = analysis.aliasManager
    private val strategy = analysis.dsuMergeStrategy

    private inline fun buildState(body: StateBuilder.() -> Unit): State =
        fillState(body).build()

    private inline fun fillState(body: StateBuilder.() -> Unit): StateBuilder {
        val builder = StateBuilder(manager, strategy)
        builder.body()
        return builder
    }

    private fun State.invalidate(builder: StateBuilder, start: Set<AAInfo>): State =
        with(analysis) { invalidateOuterHeapAliases(builder.infoIds(start)) }

    @Test
    fun invalidateEmptyStartSetIsNoop() {
        val builder = fillState {
            val loc = local(0)
            val arr = arrayAlias(loc)
            merge(setOf(loc, arr))
        }
        val original = builder.build()

        val result = original.invalidate(builder, emptySet())

        val expected = buildState {
            val loc = local(0)
            val arr = arrayAlias(loc)
            merge(setOf(loc, arr))
        }

        assertEquals(expected, result)
        assertEquals(original, result)
    }

    @Test
    fun invalidateInvalidatesMutableHeapAliasOfInvalidatedInstance() {
        val builder = fillState {
            val loc = local(0)
            val arr = arrayAlias(loc)
            merge(setOf(loc, arr))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.Local(0, ContextInfo.rootContext))))

        val expected = buildState {
            merge(setOf(local(0)))
        }

        assertEquals(expected, result)
        assertNotEquals(state, result)
    }

    @Test
    fun invalidateKeepsImmutableHeapAlias() {
        val builder = fillState {
            val loc = local(0)
            val fld = fieldAlias(loc, "x")
            merge(setOf(loc, fld))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.Local(0, ContextInfo.rootContext))))

        val expected = buildState {
            val l = local(0)
            val f = fieldAlias(l, "x")
            merge(setOf(l, f))
        }

        assertEquals(expected, result)
        assertEquals(state, result)
    }

    @Test
    fun invalidateTransitivelyPropagatesThroughGroupContainingOuter() {
        val builder = fillState {
            val a = local(0)
            val b = local(1)
            val outer = outerThis()
            val c = local(2)
            val arr = arrayAlias(outer)

            merge(setOf(a, b))
            merge(setOf(outer, c, arr))
            // connect the two groups
            merge(setOf(b, c))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.Local(0, ContextInfo.rootContext))))

        val expected = buildState {
            val a2 = local(0)
            val b2 = local(1)
            val outer2 = outerThis()
            val c2 = local(2)
            merge(setOf(a2, b2, outer2, c2))
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateTransitivelyPropagatesThroughUnknown() {
        val builder = fillState {
            val a = local(0)
            val b = local(1)
            val u = unknown(0)
            val arr = arrayAlias(b)

            merge(setOf(u, arr, b))
            // connect a's singleton group with the unknown-tainted group
            merge(setOf(a, b))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.Local(0, ContextInfo.rootContext))))

        val expected = buildState {
            val a2 = local(0)
            val b2 = local(1)
            val u2 = unknown(0)
            merge(setOf(a2, b2, u2))
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateLeavesUnrelatedGroupsUntouched() {
        val builder = fillState {
            val a = local(0)
            val arr1 = arrayAlias(a)
            val b = local(5)
            val arr2 = arrayAlias(b)

            merge(setOf(a, arr1))
            merge(setOf(b, arr2))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.Local(0, ContextInfo.rootContext))))

        val expected = buildState {
            val a2 = local(0)
            val b2 = local(5)
            val arr2 = arrayAlias(b2)
            merge(setOf(a2))
            merge(setOf(b2, arr2))
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateInvalidatesHeapAliasWhenInstanceGroupContainsOuter() {
        val builder = fillState {
            val outer = outerThis()
            val x = local(0)
            val arr = arrayAlias(outer)
            merge(setOf(outer, x, arr))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.This(isOuter = true))))

        val expected = buildState {
            val outer2 = outerThis()
            val x2 = local(0)
            merge(setOf(outer2, x2))
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateRemovesEntireSharedAliasSetWhenInvalidatedThroughHeapInstance() {
        val builder = fillState {
            val x = local(0)
            val y = local(7)
            val hx = arrayAlias(x)
            val hy = arrayAlias(y)
            merge(setOf(hx, hy))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.Local(0, ContextInfo.rootContext))))

        val expected = buildState {
            // both hx and hy are removed; x and y were never merged into the DSU on their own.
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateKeepsValidHeapAliasNotInGroupOfInvalidatedHeap() {
        val builder = fillState {
            val x = local(0)
            val y = local(7)
            val hx = arrayAlias(x)
            val hy = arrayAlias(y)
            merge(setOf(x, hx))
            merge(setOf(y, hy))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.Local(0, ContextInfo.rootContext))))

        val expected = buildState {
            val x2 = local(0)
            val y2 = local(7)
            val hy2 = arrayAlias(y2)
            merge(setOf(x2))
            merge(setOf(y2, hy2))
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateCascadesThroughHeapOfHeapChain() {
        val builder = fillState {
            val l = local(0)
            val h1 = arrayAlias(l)
            merge(setOf(l, h1))
            val h2 = arrayAlias(h1)
            merge(setOf(h1, h2))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.Local(0, ContextInfo.rootContext))))

        val expected = buildState {
            val l2 = local(0)
            merge(setOf(l2))
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateCascadeStopsAtImmutableBoundary() {
        // immutable accessor alone is not enough: the entire instance alias group
        // must be deep-immutable, and the mutable h2 in the same group taints it.
        val builder = fillState {
            val l = local(0)
            val imm = fieldAlias(l, "f", isImmutable = true)
            merge(setOf(l, imm))
            val h2 = arrayAlias(imm)
            merge(setOf(imm, h2))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.Local(0, ContextInfo.rootContext))))

        val expected = buildState {
            val l2 = local(0)
            merge(setOf(l2))
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateRemovesMutableFieldAlias() {
        val builder = fillState {
            val l = local(0)
            val f = fieldAlias(l, "x", isImmutable = false)
            merge(setOf(l, f))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.Local(0, ContextInfo.rootContext))))

        val expected = buildState {
            val l2 = local(0)
            merge(setOf(l2))
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateRemovesImmutableFieldOnOuterInstance() {
        val builder = fillState {
            val outer = outerThis()
            val f = fieldAlias(outer, "x", isImmutable = true)
            merge(setOf(outer, f))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.This(isOuter = true))))

        val expected = buildState {
            val outer2 = outerThis()
            merge(setOf(outer2))
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateEmptyStartRemovesHeapWithUnknownInGroup() {
        val builder = fillState {
            val l = local(0)
            val u = unknown(99)
            val h = arrayAlias(l)
            merge(setOf(u, h, l))
        }
        val state = builder.build()

        val result = state.invalidate(builder, emptySet())

        val expected = buildState {
            val l2 = local(0)
            val u2 = unknown(99)
            merge(setOf(u2, l2))
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateEmptyStartRemovesHeapWithOuterInGroup() {
        val builder = fillState {
            val outer = outerThis()
            val h = arrayAlias(outer)
            merge(setOf(outer, h))
        }
        val state = builder.build()

        val result = state.invalidate(builder, emptySet())

        val expected = buildState {
            val outer2 = outerThis()
            merge(setOf(outer2))
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateEmptyStartIsNoopWhenStateHasNoOuterOrHeap() {
        val builder = fillState {
            val a = local(0)
            val b = local(1)
            merge(setOf(a, b))
        }
        val state = builder.build()

        val result = state.invalidate(builder, emptySet())

        val expected = buildState {
            val a2 = local(0)
            val b2 = local(1)
            merge(setOf(a2, b2))
        }

        assertEquals(expected, result)
        assertEquals(state, result)
    }

    @Test
    fun invalidateRemovesMutableHeapButKeepsImmutableHeapInSameGroup() {
        // The presence of a mutable heap in the instance group breaks deep-immutability,
        // so the immutable heap is removed as well.
        val builder = fillState {
            val l = local(0)
            val mh = arrayAlias(l)
            val ih = fieldAlias(l, "k", isImmutable = true)
            merge(setOf(l, mh, ih))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.Local(0, ContextInfo.rootContext))))

        val expected = buildState {
            val l2 = local(0)
            merge(setOf(l2))
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateRemovesAllMutableHeapsInChainWhenStartIsHeapItself() {
        val builder = fillState {
            val l = local(0)
            val h1 = arrayAlias(l)
            merge(setOf(l, h1))
            val h2 = arrayAlias(h1)
            merge(setOf(h1, h2))
        }
        val state = builder.build()

        // Seed with h1 itself (a heap alias). The first pass doesn't taint the group through h1,
        // but `removeUnsafe({h1})` cascades to h2 because h2's instance group equals h1's group.
        val h1Seed = HeapAlias(state.aliasGroupId(builder.infoId(SimpleLoc(RefValue.Local(0, ContextInfo.rootContext)))), ArrayAlias)
        val result = state.invalidate(builder, setOf(h1Seed))

        val expected = buildState {
            val l2 = local(0)
            merge(setOf(l2))
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateLeavesUnrelatedTopLevelAliasGroupsIntact() {
        val builder = fillState {
            merge(setOf(local(0), local(1)))
            merge(setOf(local(2), local(3)))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.Local(0, ContextInfo.rootContext))))

        val expected = buildState {
            merge(setOf(local(0), local(1)))
            merge(setOf(local(2), local(3)))
        }

        assertEquals(expected, result)
        assertEquals(state, result)
    }

    @Test
    fun invalidateRemovesMutableHeapEvenWhenAliasedWithImmutableHeapOnDifferentInstance() {
        // hx (mutable) and iy (immutable accessor) share an alias group.
        // The group's deep-immutability check fails because of hx, so iy is removed too.
        val builder = fillState {
            val x = local(0)
            val y = local(7)
            val hx = arrayAlias(x)
            val iy = fieldAlias(y, "k", isImmutable = true)
            merge(setOf(y, iy))
            merge(setOf(hx, iy))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.Local(0, ContextInfo.rootContext))))

        val expected = buildState {
            // hx and iy removed, y left as singleton (no longer in the DSU).
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateCascadeAcrossTwoMergedHeapInstancesBothInvalid() {
        val builder = fillState {
            val x = local(0)
            val y = local(7)
            merge(setOf(x, y))
            val hx = arrayAlias(x)
            val hy = arrayAlias(y)
            merge(setOf(hx, hy))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.Local(0, ContextInfo.rootContext))))

        val expected = buildState {
            val x2 = local(0)
            val y2 = local(7)
            merge(setOf(x2, y2))
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateChainedFieldAccessImmutableThenMutable() {
        // Although `imm` accessor is immutable, the alias group also contains `mut` whose
        // accessor is mutable, so the group fails the deep-immutability check and `imm` is removed.
        val builder = fillState {
            val l = local(0)
            val imm = fieldAlias(l, "imm", isImmutable = true)
            merge(setOf(l, imm))
            val mut = fieldAlias(imm, "mut", isImmutable = false)
            merge(setOf(imm, mut))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.Local(0, ContextInfo.rootContext))))

        val expected = buildState {
            val l2 = local(0)
            merge(setOf(l2))
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateDeepImmutableChainOnSafeLocalRemainsIntact() {
        val builder = fillState {
            val l = local(0)
            val f1 = fieldAlias(l, "a", isImmutable = true)
            merge(setOf(l, f1))
            val f2 = fieldAlias(f1, "b", isImmutable = true)
            merge(setOf(f1, f2))
            val f3 = fieldAlias(f2, "c", isImmutable = true)
            merge(setOf(f2, f3))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.Local(0, ContextInfo.rootContext))))

        val expected = buildState {
            val l2 = local(0)
            val f1b = fieldAlias(l2, "a", isImmutable = true)
            merge(setOf(l2, f1b))
            val f2b = fieldAlias(f1b, "b", isImmutable = true)
            merge(setOf(f1b, f2b))
            val f3b = fieldAlias(f2b, "c", isImmutable = true)
            merge(setOf(f2b, f3b))
        }

        assertEquals(expected, result)
        assertEquals(state, result)
    }

    @Test
    fun invalidateDeepImmutableChainAnchoredOnOuterRemoved() {
        val builder = fillState {
            val outer = outerThis()
            val f1 = fieldAlias(outer, "a", isImmutable = true)
            merge(setOf(outer, f1))
            val f2 = fieldAlias(f1, "b", isImmutable = true)
            merge(setOf(f1, f2))
        }
        val state = builder.build()

        val result = state.invalidate(builder, emptySet())

        val expected = buildState {
            val outer2 = outerThis()
            merge(setOf(outer2))
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateMixedSafeAndOuterInstanceChain() {
        val builder = fillState {
            val l = local(0)
            val outer = outerThis()
            merge(setOf(l, outer))
            val f = fieldAlias(l, "x", isImmutable = true)
            merge(setOf(l, f))
        }
        val state = builder.build()

        val result = state.invalidate(builder, emptySet())

        val expected = buildState {
            val l2 = local(0)
            val outer2 = outerThis()
            merge(setOf(l2, outer2))
        }

        assertEquals(expected, result)
    }

    @Test
    fun invalidateNoopWhenStartHasOnlyLocalNotInState() {
        val builder = fillState {
            merge(setOf(local(0), local(1)))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.Local(42, ContextInfo.rootContext))))

        val expected = buildState {
            merge(setOf(local(0), local(1)))
        }

        assertEquals(expected, result)
        assertEquals(state, result)
    }

    @Test
    fun invalidateRemovesMultipleHeapAliasesInDifferentGroupsWhenInstancesInvalidated() {
        val builder = fillState {
            val x = local(0)
            val y = local(1)
            merge(setOf(x, y))
            val hx = arrayAlias(x)
            merge(setOf(x, hx))
            val hy = arrayAlias(y)
            merge(setOf(y, hy))
        }
        val state = builder.build()

        val result = state.invalidate(builder, setOf(SimpleLoc(RefValue.Local(0, ContextInfo.rootContext))))

        val expected = buildState {
            val x2 = local(0)
            val y2 = local(1)
            merge(setOf(x2, y2))
        }

        assertEquals(expected, result)
    }
}
