package org.opentaint.dataflow.jvm.ap.ifds.trace

import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodSequentPrecondition
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils

class JIRMethodSequentPrecondition(private val currentInst: JIRInst) : MethodSequentPrecondition {
    override fun factPrecondition(fact: InitialFactAp): List<InitialFactAp>? {
        if (currentInst !is JIRAssignInst) {
            return null
        }

        return sequentAssignPrecondition(currentInst.rhv, currentInst.lhv, fact)
    }

    private fun sequentAssignPrecondition(
        assignFrom: JIRExpr,
        assignTo: JIRValue,
        fact: InitialFactAp,
    ): List<InitialFactAp>? {
        val assignFromAccess = when (assignFrom) {
            is JIRCastExpr -> MethodFlowFunctionUtils.mkAccess(assignFrom.operand)
            is JIRImmediate -> MethodFlowFunctionUtils.mkAccess(assignFrom)
            is JIRArrayAccess -> MethodFlowFunctionUtils.mkAccess(assignFrom)
            is JIRFieldRef -> MethodFlowFunctionUtils.mkAccess(assignFrom)
            else -> null
        }

        val assignToAccess = when (assignTo) {
            is JIRImmediate -> MethodFlowFunctionUtils.mkAccess(assignTo)
            is JIRArrayAccess -> MethodFlowFunctionUtils.mkAccess(assignTo)
            is JIRFieldRef -> MethodFlowFunctionUtils.mkAccess(assignTo)
            else -> null
        }

        return when {
            assignFromAccess?.accessor != null -> {
                check(assignToAccess?.accessor == null) { "Complex assignment: $assignTo = $assignFrom" }
                fieldRead(
                    assignToAccess?.base, assignFromAccess.base, assignFromAccess.accessor, fact
                )
            }

            assignToAccess?.accessor != null -> {
                fieldWrite(
                    assignToAccess.base, assignToAccess.accessor, assignFromAccess?.base, fact
                )
            }

            else -> simpleAssign(assignToAccess?.base, assignFromAccess?.base, fact)
        }
    }

    private fun simpleAssign(
        assignTo: AccessPathBase?,
        assignFrom: AccessPathBase?,
        fact: InitialFactAp,
    ): List<InitialFactAp>? {
        if (assignTo == assignFrom || assignTo != fact.base) {
            return null
        }

        if (assignFrom != null) {
            return listOf(fact.rebase(assignFrom))
        }

        return emptyList()
    }

    private fun fieldRead(
        assignTo: AccessPathBase?,
        instance: AccessPathBase,
        accessor: Accessor,
        fact: InitialFactAp,
    ): List<InitialFactAp>? {
        if (fact.base != assignTo) {
            return null
        }

        return listOf(fact.prependAccessor(accessor).rebase(instance))
    }

    private fun fieldWrite(
        instance: AccessPathBase,
        accessor: Accessor,
        assignFrom: AccessPathBase?,
        fact: InitialFactAp,
    ): List<InitialFactAp>? {
        if (fact.base != instance || !fact.startsWithAccessor(accessor)) {
            return null
        }

        return buildList {
            val factAtAccessor = fact.readAccessor(accessor) ?: error("No fact")
            if (assignFrom != null) {
                this += factAtAccessor.rebase(assignFrom)
            }

            val otherFact = fact.clearAccessor(accessor)
            if (otherFact != null) {
                this += otherFact
            }

            if (accessor is ElementAccessor) {
                this += factAtAccessor.prependAccessor(ElementAccessor)
            }
        }
    }
}
