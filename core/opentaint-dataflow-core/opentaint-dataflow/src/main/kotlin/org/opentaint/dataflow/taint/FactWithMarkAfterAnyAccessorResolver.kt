package org.opentaint.dataflow.taint

import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.SideEffectKind
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

interface FactWithMarkAfterAnyAccessorResolver {
    fun resolve(mark: TaintMarkAccessor)
}

data class TaintMarkFieldUnfoldRequest(
    val method: MethodEntryPoint,
    val fact: InitialFactAp,
    val mark: TaintMarkAccessor,
    val suffix: Accessor?
) : SideEffectKind

data class DefaultFactWithMarkAfterAnyFieldResolver(
    private val method: MethodEntryPoint,
    private val initialFact: InitialFactAp,
    private val addSideEffect: (InitialFactAp, SideEffectKind) -> Unit
): FactWithMarkAfterAnyAccessorResolver {
    override fun resolve(mark: TaintMarkAccessor) {
        addSideEffect(initialFact, TaintMarkFieldUnfoldRequest(method, initialFact, mark, suffix = null))
    }

    companion object {
        fun createMarkAfterAccessorResolver(
            method: MethodEntryPoint,
            initialFacts: Set<InitialFactAp>,
            addSideEffect: (InitialFactAp, SideEffectKind) -> Unit
        ): DefaultFactWithMarkAfterAnyFieldResolver? {
            // 0 or 2+ facts implies that we have no abstraction
            if (initialFacts.size != 1) return null
            return DefaultFactWithMarkAfterAnyFieldResolver(method, initialFacts.first(), addSideEffect)
        }
    }
}
