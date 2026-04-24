package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.SideEffectKind
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

data class TaintMarkFieldUnfoldRequest(
    val method: MethodEntryPoint,
    val fact: InitialFactAp,
    val mark: TaintMarkAccessor
) : SideEffectKind

data class JIRFactWithMarkAfterAnyFieldResolver(
    private val method: MethodEntryPoint,
    val initialFact: InitialFactAp?,
    private val addSideEffect: (InitialFactAp, SideEffectKind) -> Unit
) {
    fun resolve(mark: TaintMarkAccessor) {
        check(initialFact != null)
        addSideEffect(initialFact, TaintMarkFieldUnfoldRequest(method, initialFact, mark))
    }

    fun resolve(fact: InitialFactAp, mark: TaintMarkAccessor) {
        addSideEffect(fact, TaintMarkFieldUnfoldRequest(method, fact, mark))
    }

    companion object {
        fun createMarkAfterFieldsResolver(
            method: MethodEntryPoint,
            initialFacts: Set<InitialFactAp>,
            addSideEffect: (InitialFactAp, SideEffectKind) -> Unit
        ): JIRFactWithMarkAfterAnyFieldResolver {
            // 0 or 2+ facts implies that we have no abstraction
            val initialFact =
                if (initialFacts.size != 1) null
                else initialFacts.first()
            return JIRFactWithMarkAfterAnyFieldResolver(method, initialFact, addSideEffect)
        }
    }
}
