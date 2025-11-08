package org.opentaint.dataflow.jvm.ap.ifds.trace

import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRThrowInst
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodSequentPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodSequentPrecondition.SequentPrecondition
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils

class JIRMethodSequentPrecondition(private val currentInst: JIRInst) : MethodSequentPrecondition {
    override fun factPrecondition(fact: InitialFactAp): SequentPrecondition {
        when (currentInst) {
            is JIRAssignInst -> {
                return sequentAssignPrecondition(currentInst.rhv, currentInst.lhv, fact)
            }

            is JIRReturnInst -> {
                if (fact.base !is AccessPathBase.Return) {
                    return SequentPrecondition.Unchanged
                }

                val base = currentInst.returnValue
                    ?.let { MethodFlowFunctionUtils.accessPathBase(it) }
                    ?: return SequentPrecondition.Unchanged

                return SequentPrecondition.Facts(listOf(fact.rebase(base)))
            }

            is JIRThrowInst -> {
                if (fact.base !is AccessPathBase.Exception) {
                    return SequentPrecondition.Unchanged
                }

                val base = currentInst.throwable
                    .let { MethodFlowFunctionUtils.accessPathBase(it) }
                    ?: return SequentPrecondition.Unchanged

                return SequentPrecondition.Facts(listOf(fact.rebase(base)))
            }

            else -> return SequentPrecondition.Unchanged
        }
    }

    private fun sequentAssignPrecondition(
        assignFrom: JIRExpr,
        assignTo: JIRValue,
        fact: InitialFactAp,
    ): SequentPrecondition {
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
    ): SequentPrecondition {
        if (assignTo == assignFrom || assignTo != fact.base) {
            return SequentPrecondition.Unchanged
        }

        if (assignFrom != null) {
            return SequentPrecondition.Facts(listOf(fact.rebase(assignFrom)))
        }

        // kill fact
        return SequentPrecondition.Facts(emptyList())
    }

    private fun fieldRead(
        assignTo: AccessPathBase?,
        instance: AccessPathBase,
        accessor: Accessor,
        fact: InitialFactAp,
    ): SequentPrecondition {
        if (fact.base != assignTo) {
            return SequentPrecondition.Unchanged
        }

        return SequentPrecondition.Facts(listOf(fact.prependAccessor(accessor).rebase(instance)))
    }

    private fun fieldWrite(
        instance: AccessPathBase,
        accessor: Accessor,
        assignFrom: AccessPathBase?,
        fact: InitialFactAp,
    ): SequentPrecondition {
        if (fact.base != instance || !fact.startsWithAccessor(accessor)) {
            return SequentPrecondition.Unchanged
        }

        val facts = buildList {
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

        return SequentPrecondition.Facts(facts)
    }
}
