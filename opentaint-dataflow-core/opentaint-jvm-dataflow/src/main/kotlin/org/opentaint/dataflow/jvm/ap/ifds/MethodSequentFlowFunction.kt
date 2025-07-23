package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.dataflow.jvm.ap.ifds.AccessTree.AccessNode
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.clearField
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.excludeField
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.mayReadField
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.mayRemoveAfterWrite
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.readFieldTo
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.rebase
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.writeToField

class MethodSequentFlowFunction(private val currentInst: JIRInst, private val factTypeChecker: FactTypeChecker) {

    sealed interface Sequent {
        object ZeroToZero : Sequent
        data class ZeroToFact(val fact: Fact.TaintedTree) : Sequent
        data class FactToFact(val initialFact: Fact.TaintedPath, val fact: Fact.TaintedTree) : Sequent
    }

    fun propagateZeroToZero() = setOf(Sequent.ZeroToZero)

    fun propagateZeroToFact(currentFact: Fact.TaintedTree) = buildSet<Sequent.ZeroToFact> {
        propagate(
            fact = currentFact,
            propagateFact = { fact ->
                add(Sequent.ZeroToFact(fact))
            },
            propagateFactWithAccessorExclude = { _, _ ->
                error("Zero to Fact edge can't be refined: $currentFact")
            }
        )
    }

    fun propagateFactToFact(
        initialFact: Fact.TaintedPath,
        currentFact: Fact.TaintedTree
    ) = buildSet<Sequent.FactToFact> {
        propagate(
            fact = currentFact,
            propagateFact = { fact ->
                add(Sequent.FactToFact(initialFact, fact))
            },
            propagateFactWithAccessorExclude = { fact, accessor ->
                val refinedInitial = initialFact.changeAP(initialFact.ap.excludeField(accessor))
                val refinedFact = fact.changeAP(fact.ap.excludeField(accessor))
                add(Sequent.FactToFact(refinedInitial, refinedFact))
            }
        )
    }

    private fun propagate(
        fact: Fact.TaintedTree,
        propagateFact: (Fact.TaintedTree) -> Unit,
        propagateFactWithAccessorExclude: (Fact.TaintedTree, Accessor) -> Unit
    ) {
        if (currentInst !is JIRAssignInst) {
            propagateFact(fact)
        } else {
            sequentFlowAssign(
                currentInst.rhv, currentInst.lhv, fact,
                propagateFact, propagateFactWithAccessorExclude
            )
        }
    }

    private fun sequentFlowAssign(
        assignFrom: JIRExpr,
        assignTo: JIRValue,
        currentFact: Fact.TaintedTree,
        propagateFact: (Fact.TaintedTree) -> Unit,
        propagateFactWithAccessorExclude: (Fact.TaintedTree, Accessor) -> Unit
    ) {
        var fact = currentFact

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
        fact: Fact.TaintedTree
    ): Fact.TaintedTree? {
        if (fact.ap.base != this.base || expectedType == null) return fact
        return factTypeChecker.filterFactByLocalType(expectedType, fact)
    }

    private fun simpleAssign(
        assignTo: AccessPathBase,
        assignFrom: AccessPathBase?,
        fact: Fact.TaintedTree,
        propagateFact: (Fact.TaintedTree) -> Unit,
    ) {
        if (assignTo == assignFrom) {
            propagateFact(fact)
            return
        }

        // Assign can't overwrite fact
        if (assignTo != fact.ap.base) {
            propagateFact(fact)
        }

        if (assignFrom == fact.ap.base) {
            propagateFact(fact.rebase(assignTo))
        }
    }

    private fun fieldRead(
        assignTo: AccessPathBase,
        instance: AccessPathBase,
        accessor: Accessor,
        fact: Fact.TaintedTree,
        propagateFact: (Fact.TaintedTree) -> Unit,
        propagateFactWithAccessorExclude: (Fact.TaintedTree, Accessor) -> Unit
    ) {
        if (!fact.ap.mayReadField(instance, accessor)) {
            // Fact is irrelevant to current reading
            propagateFact(fact)
            return
        }

        if (fact.ap.access.isAbstract && accessor !in fact.ap.exclusions) {
            val nonAbstractAccess = fact.ap.access.removeAbstraction()
            if (!nonAbstractAccess.isEmpty) {
                val nonAbstractAp = AccessTree(fact.ap.base, nonAbstractAccess, fact.ap.exclusions)
                val nonAbstractFact = fact.changeAP(nonAbstractAp)
                fieldRead(
                    assignTo, instance, accessor, nonAbstractFact,
                    propagateFact, propagateFactWithAccessorExclude
                )
            }

            propagateAbstractFactWithFieldExcluded(fact, accessor, propagateFactWithAccessorExclude)

            return
        }

        check(fact.ap.access.contains(accessor))

        val newAp = fact.ap.readFieldTo(newBase = assignTo, field = accessor)
        propagateFact(fact.changeAP(newAp))

        // Assign can't overwrite fact
        if (assignTo != fact.ap.base) {
            propagateFact(fact)
        }
    }

    private fun fieldWrite(
        instance: AccessPathBase,
        accessor: Accessor,
        assignFrom: AccessPathBase?,
        fact: Fact.TaintedTree,
        propagateFact: (Fact.TaintedTree) -> Unit,
        propagateFactWithAccessorExclude: (Fact.TaintedTree, Accessor) -> Unit
    ) {
        if (assignFrom == instance) {
            if (fact.ap.base != instance) {
                // Fact is irrelevant to current writing
                propagateFact(fact)
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
                    fact = fact.rebase(auxiliaryBase), // f(b)
                    propagateFact = {
                        if (it.ap.base != auxiliaryBase) {
                            propagateFact(it)
                        }
                    },
                    propagateFactWithAccessorExclude = { f, a ->
                        if (f.ap.base != auxiliaryBase) {
                            propagateFactWithAccessorExclude(f, a)
                        }
                    }
                )

                fieldWrite(
                    instance = instance,
                    accessor = accessor,
                    assignFrom = auxiliaryBase,
                    fact = fact, // f(a)
                    propagateFact = {
                        if (it.ap.base != auxiliaryBase) {
                            propagateFact(it)
                        }
                    },
                    propagateFactWithAccessorExclude = { f, a ->
                        if (f.ap.base != auxiliaryBase) {
                            propagateFactWithAccessorExclude(f, a)
                        }
                    }
                )

                return
            }
        }

        if (fact.ap.base == assignFrom) {
            // Original rhs fact
            propagateFact(fact)

            // New lhs fact
            val newAp = fact.ap.writeToField(newBase = instance, field = accessor)
            propagateFact(fact.changeAP(newAp))

            return
        }

        // We have fact on lhs and NO fact on the rhs -> remove fact from lhs

        // todo hack: keep fact on the array elements
        if (fact.ap.base == instance && accessor is ElementAccessor) {
            propagateFact(fact)
            return
        }

        if (!fact.ap.mayRemoveAfterWrite(instance, accessor)) {
            // Fact is irrelevant to current writing
            propagateFact(fact)
            return
        }

        if (fact.ap.access.isAbstract && accessor !in fact.ap.exclusions) {
            val nonAbstractAccess = fact.ap.access.removeAbstraction()
            if (!nonAbstractAccess.isEmpty) {
                val nonAbstractAp = AccessTree(fact.ap.base, nonAbstractAccess, fact.ap.exclusions)
                val nonAbstractFact = fact.changeAP(nonAbstractAp)
                fieldWrite(
                    instance, accessor, assignFrom, nonAbstractFact,
                    propagateFact, propagateFactWithAccessorExclude
                )
            }

            propagateAbstractFactWithFieldExcluded(fact, accessor, propagateFactWithAccessorExclude)

            return
        }

        check(fact.ap.access.contains(accessor))

        val newAp = fact.ap.clearField(accessor) ?: return
        propagateFact(fact.changeAP(newAp))
    }

    private fun propagateAbstractFactWithFieldExcluded(
        fact: Fact.TaintedTree,
        accessor: Accessor,
        propagateFactWithAccessorExclude: (Fact.TaintedTree, Accessor) -> Unit
    ) {
        val abstractAp = AccessTree(
            fact.ap.base,
            AccessNode.abstractNode(),
            fact.ap.exclusions
        )

        val abstractFact = fact.changeAP(abstractAp)
        propagateFactWithAccessorExclude(abstractFact, accessor)
    }
}
