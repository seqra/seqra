package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRType
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
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.MethodSequentFlowFunction
import org.opentaint.dataflow.ap.ifds.Sequent
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.clearField
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.excludeField
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.mayReadField
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.mayRemoveAfterWrite
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.readFieldTo
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.writeToField
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

class JIRMethodSequentFlowFunction(
    private val apManager: ApManager,
    private val currentInst: JIRInst,
    private val factTypeChecker: FactTypeChecker
): MethodSequentFlowFunction {
    override fun propagateZeroToZero() = setOf(Sequent.ZeroToZero)

    override fun propagateZeroToFact(currentFactAp: FinalFactAp) = buildSet<Sequent.ZeroToFact> {
        propagate(
            factAp = currentFactAp,
            propagateFact = { fact ->
                add(Sequent.ZeroToFact(fact))
            },
            propagateFactWithAccessorExclude = { _, _ ->
                error("Zero to Fact edge can't be refined: $currentFactAp")
            }
        )
    }

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp
    ) = buildSet<Sequent.FactToFact> {
        propagate(
            factAp = currentFactAp,
            propagateFact = { fact ->
                add(Sequent.FactToFact(initialFactAp, fact))
            },
            propagateFactWithAccessorExclude = { fact, accessor ->
                val refinedInitial = initialFactAp.excludeField(accessor)
                val refinedFact = fact.excludeField(accessor)
                add(Sequent.FactToFact(refinedInitial, refinedFact))
            }
        )
    }

    private fun propagate(
        factAp: FinalFactAp,
        propagateFact: (FinalFactAp) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor) -> Unit
    ) {
        when (currentInst) {
            is JIRAssignInst -> {
                sequentFlowAssign(
                    currentInst.rhv, currentInst.lhv, factAp,
                    propagateFact, propagateFactWithAccessorExclude
                )
            }

            is JIRReturnInst -> {
                propagateFact(factAp)

                val access = currentInst.returnValue?.let { MethodFlowFunctionUtils.accessPathBase(it) }
                if (access == factAp.base) {
                    propagateFact(factAp.rebase(AccessPathBase.Return))
                }
            }

            is JIRThrowInst -> {
                propagateFact(factAp)

                val access = currentInst.throwable.let { MethodFlowFunctionUtils.accessPathBase(it) }
                if (access == factAp.base) {
                    propagateFact(factAp.rebase(AccessPathBase.Exception))
                }
            }

            else -> {
                propagateFact(factAp)
            }
        }
    }

    private fun sequentFlowAssign(
        assignFrom: JIRExpr,
        assignTo: JIRValue,
        currentFactAp: FinalFactAp,
        propagateFact: (FinalFactAp) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor) -> Unit
    ) {
        var fact = currentFactAp

        val assignFromAccess = when (assignFrom) {
            is JIRCastExpr -> MethodFlowFunctionUtils.mkAccess(assignFrom.operand)
                ?.apply { fact = filterFactBaseType(assignFrom.type, fact) ?: return }
                ?: return

            is JIRImmediate -> MethodFlowFunctionUtils.mkAccess(assignFrom)
                ?.apply { fact = filterFactBaseType(assignFrom.type, fact) ?: return }
                ?: return

            is JIRArrayAccess -> MethodFlowFunctionUtils.mkAccess(assignFrom)
                ?.apply { fact = filterFactBaseType(assignFrom.array.type, fact) ?: return }
                ?: return

            is JIRFieldRef -> MethodFlowFunctionUtils.mkAccess(assignFrom)
                ?.apply { fact = filterFactBaseType(assignFrom.instance?.type, fact) ?: return }
                ?.apply { fact = filterFactBaseType(assignFrom.field.enclosingType, fact) ?: return }
                ?: return

            else -> null
        }

        val assignToAccess = when (assignTo) {
            is JIRImmediate -> MethodFlowFunctionUtils.mkAccess(assignTo)
                ?.apply { fact = filterFactBaseType(assignTo.type, fact) ?: return }
                ?: return

            is JIRArrayAccess -> MethodFlowFunctionUtils.mkAccess(assignTo)
                ?.apply { fact = filterFactBaseType(assignTo.array.type, fact) ?: return }
                ?: return

            is JIRFieldRef -> MethodFlowFunctionUtils.mkAccess(assignTo)
                ?.apply { fact = filterFactBaseType(assignTo.instance?.type, fact) ?: return }
                ?.apply { fact = filterFactBaseType(assignTo.field.enclosingType, fact) ?: return }
                ?: return

            else -> error("Assign to complex value: $assignTo")
        }

        when {
            assignFromAccess?.accessor != null -> {
                check(assignToAccess.accessor == null) { "Complex assignment: $assignTo = $assignFrom" }
                fieldRead(
                    assignToAccess.base, assignFromAccess.base, assignFromAccess.accessor, fact,
                    propagateFact, propagateFactWithAccessorExclude
                )
            }

            assignToAccess.accessor != null -> {
                fieldWrite(
                    assignToAccess.base, assignToAccess.accessor, assignFromAccess?.base, fact,
                    propagateFact, propagateFactWithAccessorExclude
                )
            }

            else -> simpleAssign(assignToAccess.base, assignFromAccess?.base, fact, propagateFact)
        }
    }

    private fun MethodFlowFunctionUtils.Access.filterFactBaseType(
        expectedType: JIRType?,
        factAp: FinalFactAp
    ): FinalFactAp? {
        if (factAp.base != this.base || expectedType == null) return factAp
        return factTypeChecker.filterFactByLocalType(expectedType, factAp)
    }

    private fun simpleAssign(
        assignTo: AccessPathBase,
        assignFrom: AccessPathBase?,
        factAp: FinalFactAp,
        propagateFact: (FinalFactAp) -> Unit,
    ) {
        if (assignTo == assignFrom) {
            propagateFact(factAp)
            return
        }

        // Assign can't overwrite fact
        if (assignTo != factAp.base) {
            propagateFact(factAp)
        }

        if (assignFrom == factAp.base) {
            propagateFact(factAp.rebase(assignTo))
        }
    }

    private fun fieldRead(
        assignTo: AccessPathBase,
        instance: AccessPathBase,
        accessor: Accessor,
        factAp: FinalFactAp,
        propagateFact: (FinalFactAp) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor) -> Unit
    ) {
        if (!factAp.mayReadField(instance, accessor)) {
            // Fact is irrelevant to current reading
            propagateFact(factAp)
            return
        }

        if (factAp.isAbstract() && accessor !in factAp.exclusions) {
            val nonAbstractAp = factAp.removeAbstraction()
            if (nonAbstractAp != null) {
                fieldRead(
                    assignTo, instance, accessor, nonAbstractAp,
                    propagateFact, propagateFactWithAccessorExclude
                )
            }

            propagateAbstractFactWithFieldExcluded(factAp, accessor, propagateFactWithAccessorExclude)

            return
        }

        check(factAp.startsWithAccessor(accessor))

        val newAp = factAp.readFieldTo(newBase = assignTo, field = accessor)
        propagateFact(newAp)

        // Assign can't overwrite fact
        if (assignTo != factAp.base) {
            propagateFact(factAp)
        }
    }

    private fun fieldWrite(
        instance: AccessPathBase,
        accessor: Accessor,
        assignFrom: AccessPathBase?,
        factAp: FinalFactAp,
        propagateFact: (FinalFactAp) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor) -> Unit
    ) {
        if (assignFrom == instance) {
            if (factAp.base != instance) {
                // Fact is irrelevant to current writing
                propagateFact(factAp)
                return
            } else {
                /**
                 * a.x = a | f(a)
                 * -------------------
                 * b = a | f(a), f(b)
                 * a.x = b | f(b), f(b -> a.x), f(a -> a / {x})
                 */

                val auxiliaryBase = AccessPathBase.LocalVar(-1) // b
                check(auxiliaryBase != instance)

                fieldWrite(
                    instance = instance,
                    accessor = accessor,
                    assignFrom = auxiliaryBase,
                    factAp = factAp.rebase(auxiliaryBase), // f(b)
                    propagateFact = {
                        if (it.base != auxiliaryBase) {
                            propagateFact(it)
                        }
                    },
                    propagateFactWithAccessorExclude = { f, a ->
                        if (f.base != auxiliaryBase) {
                            propagateFactWithAccessorExclude(f, a)
                        }
                    }
                )

                fieldWrite(
                    instance = instance,
                    accessor = accessor,
                    assignFrom = auxiliaryBase,
                    factAp = factAp, // f(a)
                    propagateFact = {
                        if (it.base != auxiliaryBase) {
                            propagateFact(it)
                        }
                    },
                    propagateFactWithAccessorExclude = { f, a ->
                        if (f.base != auxiliaryBase) {
                            propagateFactWithAccessorExclude(f, a)
                        }
                    }
                )

                return
            }
        }

        if (factAp.base == assignFrom) {
            // Original rhs fact
            propagateFact(factAp)

            // New lhs fact
            val newAp = factAp.writeToField(newBase = instance, field = accessor)
            propagateFact(newAp)

            return
        }

        // We have fact on lhs and NO fact on the rhs -> remove fact from lhs

        // todo hack: keep fact on the array elements
        if (factAp.base == instance && accessor is ElementAccessor) {
            propagateFact(factAp)
            return
        }

        if (!factAp.mayRemoveAfterWrite(instance, accessor)) {
            // Fact is irrelevant to current writing
            propagateFact(factAp)
            return
        }

        if (factAp.isAbstract() && accessor !in factAp.exclusions) {
            val nonAbstractAp = factAp.removeAbstraction()
            if (nonAbstractAp != null) {
                fieldWrite(
                    instance, accessor, assignFrom, nonAbstractAp,
                    propagateFact, propagateFactWithAccessorExclude
                )
            }

            propagateAbstractFactWithFieldExcluded(factAp, accessor, propagateFactWithAccessorExclude)

            return
        }

        check(factAp.startsWithAccessor(accessor))

        val newAp = factAp.clearField(accessor) ?: return
        propagateFact(newAp)
    }

    private fun propagateAbstractFactWithFieldExcluded(
        factAp: FinalFactAp,
        accessor: Accessor,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor) -> Unit
    ) {
        val abstractAp = apManager.createAbstractAp(factAp.base, factAp.exclusions)
        propagateFactWithAccessorExclude(abstractAp, accessor)
    }
}
