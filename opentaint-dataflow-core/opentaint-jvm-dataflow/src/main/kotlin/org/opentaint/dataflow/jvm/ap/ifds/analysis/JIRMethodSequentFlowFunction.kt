package org.opentaint.dataflow.jvm.ap.ifds.analysis

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
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.opentaint.dataflow.configuration.jvm.ConstantTrue
import org.opentaint.dataflow.jvm.ap.ifds.CalleePositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.accessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.clearField
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.excludeField
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.mayReadField
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.mayRemoveAfterWrite
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.readFieldTo
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.writeToField
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.applyRuleWithAssumptions
import org.opentaint.dataflow.jvm.ap.ifds.taint.FinalFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintSourceActionEvaluator
import org.opentaint.util.onSome

class JIRMethodSequentFlowFunction(
    private val apManager: ApManager,
    private val analysisContext: JIRMethodAnalysisContext,
    private val currentInst: JIRInst,
): MethodSequentFlowFunction {
    private val factTypeChecker get() = analysisContext.factTypeChecker

    override fun propagateZeroToZero(): Set<Sequent> = buildSet {
        add(Sequent.ZeroToZero)

        applyUnconditionalSources()
    }

    override fun propagateZeroToFact(currentFactAp: FinalFactAp) = buildSet {
        propagate(
            factAp = currentFactAp,
            unchanged = { add(Sequent.Unchanged) },
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
    ) = buildSet {
        propagate(
            factAp = currentFactAp,
            unchanged = { add(Sequent.Unchanged) },
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
        unchanged: () -> Unit,
        propagateFact: (FinalFactAp) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor) -> Unit
    ) {
        when (currentInst) {
            is JIRAssignInst -> {
                sequentFlowAssign(
                    currentInst.rhv, currentInst.lhv, factAp,
                    unchanged, propagateFact, propagateFactWithAccessorExclude
                )
            }

            is JIRReturnInst -> {
                unchanged()

                val access = currentInst.returnValue?.let { accessPathBase(it) }
                if (access == factAp.base) {
                    val resultFact = factAp.rebase(AccessPathBase.Return)
                    propagateFact(resultFact)

                    applyMethodExitSinkRules(AccessPathBase.Return, resultFact)
                } else {
                    applyMethodExitSinkRules(AccessPathBase.Return, factAp)
                }
            }

            is JIRThrowInst -> {
                unchanged()

                val access = accessPathBase(currentInst.throwable)
                if (access == factAp.base) {
                    val resultFact = factAp.rebase(AccessPathBase.Exception)
                    propagateFact(resultFact)

                    applyMethodExitSinkRules(AccessPathBase.Exception, resultFact)
                } else {
                    applyMethodExitSinkRules(AccessPathBase.Exception, factAp)
                }
            }

            else -> {
                unchanged()
            }
        }
    }

    private fun sequentFlowAssign(
        assignFrom: JIRExpr,
        assignTo: JIRValue,
        currentFactAp: FinalFactAp,
        unchanged: () -> Unit,
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

        val factModified = fact != currentFactAp
        val onUnchanged: (FinalFactAp) -> Unit = if (factModified) propagateFact else { _ -> unchanged() }

        when {
            assignFromAccess?.accessor != null -> {
                check(assignToAccess.accessor == null) { "Complex assignment: $assignTo = $assignFrom" }
                fieldRead(
                    assignToAccess.base, assignFromAccess.base, assignFromAccess.accessor, fact,
                    onUnchanged, propagateFact, propagateFactWithAccessorExclude
                )
            }

            assignToAccess.accessor != null -> {
                fieldWrite(
                    assignToAccess.base, assignToAccess.accessor, assignFromAccess?.base, fact,
                    onUnchanged, propagateFact, propagateFactWithAccessorExclude
                )
            }

            else -> simpleAssign(assignToAccess.base, assignFromAccess?.base, fact, onUnchanged, propagateFact)
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
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp) -> Unit,
    ) {
        if (assignTo == assignFrom) {
            unchanged(factAp)
            return
        }

        // Assign can't overwrite fact
        if (assignTo != factAp.base) {
            unchanged(factAp)
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
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor) -> Unit
    ) {
        if (!factAp.mayReadField(instance, accessor)) {
            // Fact is irrelevant to current reading
            unchanged(factAp)
            return
        }

        if (factAp.isAbstract() && accessor !in factAp.exclusions) {
            val nonAbstractAp = factAp.removeAbstraction()
            if (nonAbstractAp != null) {
                fieldRead(
                    assignTo, instance, accessor, nonAbstractAp,
                    unchanged, propagateFact, propagateFactWithAccessorExclude
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
            unchanged(factAp)
        }
    }

    private fun fieldWrite(
        instance: AccessPathBase,
        accessor: Accessor,
        assignFrom: AccessPathBase?,
        factAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor) -> Unit
    ) {
        if (assignFrom == instance) {
            if (factAp.base != instance) {
                // Fact is irrelevant to current writing
                unchanged(factAp)
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
                    unchanged = {
                        if (it.base != auxiliaryBase) {
                            unchanged(it)
                        }
                    },
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
                    unchanged = {
                        if (it.base != auxiliaryBase) {
                            unchanged(it)
                        }
                    },
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
            unchanged(factAp)

            // New lhs fact
            val newAp = factAp.writeToField(newBase = instance, field = accessor)
            propagateFact(newAp)

            analysisContext.aliasAnalysis?.forEachAliasAtStatement(currentInst, newAp) { aliased ->
                propagateFact(aliased)
            }

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
            unchanged(factAp)
            return
        }

        if (factAp.isAbstract() && accessor !in factAp.exclusions) {
            val nonAbstractAp = factAp.removeAbstraction()
            if (nonAbstractAp != null) {
                fieldWrite(
                    instance, accessor, assignFrom, nonAbstractAp,
                    unchanged, propagateFact, propagateFactWithAccessorExclude
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

        analysisContext.aliasAnalysis?.forEachAliasAtStatement(currentInst, abstractAp) { aliased ->
            propagateFactWithAccessorExclude(aliased, accessor)
        }
    }

    private fun applyMethodExitSinkRules(
        methodResult: AccessPathBase, fact: FinalFactAp
    ): Unit = with(analysisContext.taint) {
        val config = taintConfig as TaintRulesProvider
        val sinkRules = config.sinkRulesForMethodExit(currentInst.method, currentInst).toList()
        if (sinkRules.isEmpty()) return

        val resultFact = if (fact.base == methodResult) fact.rebase(AccessPathBase.Return) else fact
        val conditionFactReader = FinalFactReader(resultFact, apManager)

        val valueResolver = CalleePositionToJIRValueResolver(currentInst.method)

        sinkRules.applyRuleWithAssumptions(
            apManager,
            valueResolver,
            listOf(conditionFactReader),
            analysisContext.factTypeChecker,
            condition = { condition },
            storeAssumptions = { rule, facts ->
                taintSinkTracker.addSinkRuleAssumptions(rule, currentInst, facts)
            },
            currentAssumptions = { rule ->
                taintSinkTracker.currentSinkRuleAssumptions(rule, currentInst)
            }
        ) { rule, evaluatedFacts ->
            taintSinkTracker.addVulnerability(
                analysisContext.methodEntryPoint, evaluatedFacts.toHashSet(), currentInst, rule
            )
        }
    }

    private fun MutableSet<Sequent>.applyUnconditionalSources() {
        if (currentInst !is JIRAssignInst) return

        val rhvFieldRef = currentInst.rhv as? JIRFieldRef ?: return
        val field = rhvFieldRef.field.field
        if (!field.isStatic) return

        val config = analysisContext.taint.taintConfig as TaintRulesProvider
        val sourceRules = config.sourceRulesForStaticField(field, currentInst).toList()
        if (sourceRules.isEmpty()) return

        val lhv = accessPathBase(currentInst.lhv) ?: return

        val sourceEvaluator = TaintSourceActionEvaluator(
            apManager, ExclusionSet.Universe, factTypeChecker, returnValueType = null
        )

        for (sourceRule in sourceRules) {
            if (sourceRule.condition !is ConstantTrue) {
                TODO("Field source with complex condition")
            }

            for (action in sourceRule.actionsAfter) {
                sourceEvaluator.evaluate(sourceRule, action).onSome { evaluatedFacts ->
                    evaluatedFacts.mapTo(this) {
                        if (it.base !is AccessPathBase.Return) {
                            TODO("Field source with non-result assign")
                        }

                        Sequent.ZeroToFact(it.rebase(lhv))
                    }
                }
            }
        }
    }
}
