package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.opentaint.dataflow.python.util.PIRFlowFunctionUtils
import org.opentaint.ir.api.python.*

class PIRMethodSequentFlowFunction(
    private val instruction: PIRInstruction,
    private val method: PIRFunction,
    private val ctx: PIRMethodAnalysisContext,
    private val apManager: ApManager,
) : MethodSequentFlowFunction {

    override fun propagateZeroToZero(): Set<Sequent> = setOf(Sequent.ZeroToZero)

    override fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<Sequent> {
        return when (instruction) {
            is PIRAssign -> handleAssignShared(instruction, currentFactAp) { Sequent.ZeroToFact(it, null) }
            is PIRReturn -> handleReturnShared(instruction, currentFactAp) { Sequent.ZeroToFact(it, null) }
            else -> setOf(Sequent.Unchanged)
        }
    }

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> = when (instruction) {
        is PIRAssign -> handleAssignShared(instruction, currentFactAp) { Sequent.FactToFact(initialFactAp, it, null) }
        is PIRReturn -> handleReturnShared(instruction, currentFactAp) { Sequent.FactToFact(initialFactAp, it, null) }
        is PIRStoreAttr -> handleStoreAttr(instruction, initialFactAp, currentFactAp)
        is PIRStoreSubscript -> handleStoreSubscript(instruction, initialFactAp, currentFactAp)
        else -> setOf(Sequent.Unchanged)
    }

    override fun propagateNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> = setOf(Sequent.Unchanged)

    // --- Shared logic for assignment: target = expr ---

    /**
     * Handles assignment for both zero-initial and fact-initial edges.
     * [mkCopy] creates the appropriate result type: ZeroToFact or FactToFact.
     */
    private inline fun handleAssignShared(
        assign: PIRAssign,
        currentFactAp: FinalFactAp,
        mkCopy: (FinalFactAp) -> Sequent,
    ): Set<Sequent> {
        val assignTo = PIRFlowFunctionUtils.accessPathBase(assign.target, method, ctx)
            ?: return setOf(Sequent.Unchanged)
        val assignFrom = PIRFlowFunctionUtils.exprToBase(assign.expr, method, ctx)

        if (assignFrom != null) {
            val results = mutableSetOf<Sequent>()
            if (currentFactAp.base == assignFrom) {
                results.add(mkCopy(currentFactAp.rebase(assignTo)))
                results.add(Sequent.Unchanged) // keep on source too
            } else if (currentFactAp.base == assignTo) {
                // Strong update: kill
            } else {
                results.add(Sequent.Unchanged)
            }
            return results
        }

        // Compound expression — kill if overwriting target, else pass through
        return if (currentFactAp.base == assignTo) {
            emptySet()  // Strong update
        } else {
            setOf(Sequent.Unchanged)
        }
    }

    // --- Shared logic for return ---

    /**
     * Handles return for both zero-initial and fact-initial edges.
     * [mkCopy] creates the appropriate result type: ZeroToFact or FactToFact.
     */
    private inline fun handleReturnShared(
        ret: PIRReturn,
        currentFactAp: FinalFactAp,
        mkCopy: (FinalFactAp) -> Sequent,
    ): Set<Sequent> {
        val results = mutableSetOf<Sequent>(Sequent.Unchanged)
        val retVal = ret.value ?: return results
        val retBase = PIRFlowFunctionUtils.accessPathBase(retVal, method, ctx) ?: return results
        if (currentFactAp.base == retBase) {
            results.add(mkCopy(currentFactAp.rebase(AccessPathBase.Return)))
        }
        return results
    }

    // --- StoreAttr / StoreSubscript (fact-initial only, no zero-initial equivalent needed yet) ---

    /**
     * obj.attr = value: propagate taint from value to obj's field.
     */
    private fun handleStoreAttr(
        store: PIRStoreAttr,
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> {
        val valueBase = PIRFlowFunctionUtils.accessPathBase(store.value, method, ctx)
            ?: return setOf(Sequent.Unchanged)
        val objBase = PIRFlowFunctionUtils.accessPathBase(store.obj, method, ctx)
            ?: return setOf(Sequent.Unchanged)

        val results = mutableSetOf<Sequent>(Sequent.Unchanged)

        if (currentFactAp.base == valueBase) {
            val accessor = org.opentaint.dataflow.ap.ifds.FieldAccessor(
                store.obj.type.typeName,
                store.attribute,
                store.value.type.typeName,
            )
            val newFact = currentFactAp.rebase(objBase).prependAccessor(accessor)
            results.add(Sequent.FactToFact(initialFactAp, newFact, null))
        }

        return results
    }

    /**
     * obj[index] = value: propagate taint from value to obj's element.
     */
    private fun handleStoreSubscript(
        store: PIRStoreSubscript,
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> {
        val valueBase = PIRFlowFunctionUtils.accessPathBase(store.value, method, ctx)
            ?: return setOf(Sequent.Unchanged)
        val objBase = PIRFlowFunctionUtils.accessPathBase(store.obj, method, ctx)
            ?: return setOf(Sequent.Unchanged)

        val results = mutableSetOf<Sequent>(Sequent.Unchanged)

        if (currentFactAp.base == valueBase) {
            val newFact = currentFactAp.rebase(objBase)
                .prependAccessor(org.opentaint.dataflow.ap.ifds.ElementAccessor)
            results.add(Sequent.FactToFact(initialFactAp, newFact, null))
        }

        return results
    }
}
