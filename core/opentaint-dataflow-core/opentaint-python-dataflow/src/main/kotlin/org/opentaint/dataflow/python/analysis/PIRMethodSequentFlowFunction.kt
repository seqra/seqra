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
            is PIRAssign -> handleAssignZero(instruction, currentFactAp)
            is PIRReturn -> handleReturnShared(instruction, currentFactAp) { Sequent.ZeroToFact(it, null) }
            is PIRStoreAttr -> handleStoreAttrShared(instruction, currentFactAp) { Sequent.ZeroToFact(it, null) }
            is PIRStoreSubscript -> handleStoreSubscriptShared(instruction, currentFactAp) { Sequent.ZeroToFact(it, null) }
            else -> setOf(Sequent.Unchanged)
        }
    }

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> = when (instruction) {
        is PIRAssign -> handleAssignFact(instruction, initialFactAp, currentFactAp)
        is PIRReturn -> handleReturnShared(instruction, currentFactAp) { Sequent.FactToFact(initialFactAp, it, null) }
        is PIRStoreAttr -> handleStoreAttrFact(instruction, initialFactAp, currentFactAp)
        is PIRStoreSubscript -> handleStoreSubscriptFact(instruction, initialFactAp, currentFactAp)
        else -> setOf(Sequent.Unchanged)
    }

    override fun propagateNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> = setOf(Sequent.Unchanged)

    // ==========================================================================
    // Assignment: target = expr
    // ==========================================================================

    /**
     * ZeroToFact propagation through assignment.
     * Handles simple copy and field/subscript reads from compound expressions.
     */
    private fun handleAssignZero(
        assign: PIRAssign,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> {
        val assignTo = PIRFlowFunctionUtils.accessPathBase(assign.target, method, ctx)
            ?: return setOf(Sequent.Unchanged)

        return handleAssignExpr(assign.expr, assignTo, currentFactAp) { Sequent.ZeroToFact(it, null) }
    }

    /**
     * FactToFact propagation through assignment.
     * Handles simple copy, field reads, subscript reads, and strong updates.
     */
    private fun handleAssignFact(
        assign: PIRAssign,
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> {
        val assignTo = PIRFlowFunctionUtils.accessPathBase(assign.target, method, ctx)
            ?: return setOf(Sequent.Unchanged)

        return handleAssignExpr(assign.expr, assignTo, currentFactAp) {
            Sequent.FactToFact(initialFactAp, it, null)
        }
    }

    /**
     * Core assignment handling for both ZeroToFact and FactToFact.
     * Dispatches based on expression type:
     * - Simple value (PIRValue): variable-to-variable copy
     * - PIRAttrExpr: field read (x = obj.attr)
     * - PIRSubscriptExpr: subscript read (x = obj[i])
     * - Other compound: strong update (kill) on target
     */
    private inline fun handleAssignExpr(
        expr: PIRExpr,
        assignTo: AccessPathBase,
        currentFactAp: FinalFactAp,
        mkCopy: (FinalFactAp) -> Sequent,
    ): Set<Sequent> {
        // Case 1: Simple value copy (x = y)
        if (expr is PIRValue) {
            val assignFrom = PIRFlowFunctionUtils.accessPathBase(expr, method, ctx)
            if (assignFrom != null) {
                val results = mutableSetOf<Sequent>()
                if (currentFactAp.base == assignFrom) {
                    results.add(mkCopy(currentFactAp.rebase(assignTo)))
                    results.add(Sequent.Unchanged) // keep on source too
                } else if (currentFactAp.base == assignTo) {
                    // Strong update: kill fact on overwritten target
                } else {
                    results.add(Sequent.Unchanged)
                }
                return results
            }
            // Constant or unresolvable value — kill if overwriting target, else pass
            return if (currentFactAp.base == assignTo) emptySet() else setOf(Sequent.Unchanged)
        }

        // Case 2: Field read (x = obj.attr)
        if (expr is PIRAttrExpr) {
            return handleAttrRead(expr, assignTo, currentFactAp, mkCopy)
        }

        // Case 3: Subscript read (x = obj[index])
        if (expr is PIRSubscriptExpr) {
            return handleSubscriptRead(expr, assignTo, currentFactAp, mkCopy)
        }

        // Case 4: Container literal (dict, list, tuple, set) — taint flows from values to target
        if (expr is PIRDictExpr || expr is PIRListExpr || expr is PIRTupleExpr || expr is PIRSetExpr) {
            return handleContainerLiteral(expr, assignTo, currentFactAp, mkCopy)
        }

        // Case 5: Other compound expression — strong update on target, pass through otherwise
        return if (currentFactAp.base == assignTo) {
            emptySet()  // Strong update
        } else {
            setOf(Sequent.Unchanged)
        }
    }

    /**
     * Field read: target = obj.attr
     *
     * If fact is on obj with matching field accessor (e.g., obj.data.![taint].*),
     * read the field accessor to produce target.![taint].* and rebase.
     *
     * If fact is abstract on obj (obj.*) and field is not excluded,
     * propagate abstract fact with field excluded + materialize the concrete read.
     */
    private inline fun handleAttrRead(
        expr: PIRAttrExpr,
        assignTo: AccessPathBase,
        currentFactAp: FinalFactAp,
        mkCopy: (FinalFactAp) -> Sequent,
    ): Set<Sequent> {
        val objBase = PIRFlowFunctionUtils.accessPathBase(expr.obj, method, ctx)
            ?: return if (currentFactAp.base == assignTo) emptySet() else setOf(Sequent.Unchanged)
        val accessor = org.opentaint.dataflow.ap.ifds.FieldAccessor(
            expr.obj.type.typeName,
            expr.attribute,
            expr.resultType.typeName,
        )

        val results = mutableSetOf<Sequent>()

        if (currentFactAp.base == objBase) {
            if (currentFactAp.startsWithAccessor(accessor)) {
                // Concrete: strip the field accessor and rebase
                val readFact = currentFactAp.readAccessor(accessor)?.rebase(assignTo)
                if (readFact != null) {
                    results.add(mkCopy(readFact))
                }
                // Original fact on obj survives (field read is non-destructive)
                if (assignTo != objBase) {
                    results.add(Sequent.Unchanged)
                }
            } else if (currentFactAp.isAbstract() && accessor !in currentFactAp.exclusions) {
                // Abstract: field might be behind *.
                // Materialize: remove abstraction and try concrete read
                val nonAbstract = currentFactAp.removeAbstraction()
                if (nonAbstract != null && nonAbstract.startsWithAccessor(accessor)) {
                    val readFact = nonAbstract.readAccessor(accessor)?.rebase(assignTo)
                    if (readFact != null) {
                        results.add(mkCopy(readFact))
                    }
                }
                // Propagate abstract fact with field excluded (refinement)
                val excludedFact = currentFactAp.exclude(accessor)
                results.add(mkCopy(excludedFact))
                // Also keep the original abstract fact
                results.add(Sequent.Unchanged)
            } else {
                // Fact on obj but field doesn't match — pass through
                results.add(Sequent.Unchanged)
            }
        } else if (currentFactAp.base == assignTo) {
            // Strong update: kill taint on overwritten target
            // (return empty set to kill the fact)
        } else {
            results.add(Sequent.Unchanged)
        }

        return if (results.isEmpty()) emptySet() else results
    }

    /**
     * Subscript read: target = obj[index]
     *
     * Similar to field read but uses ElementAccessor instead of FieldAccessor.
     */
    private inline fun handleSubscriptRead(
        expr: PIRSubscriptExpr,
        assignTo: AccessPathBase,
        currentFactAp: FinalFactAp,
        mkCopy: (FinalFactAp) -> Sequent,
    ): Set<Sequent> {
        val objBase = PIRFlowFunctionUtils.accessPathBase(expr.obj, method, ctx)
            ?: return if (currentFactAp.base == assignTo) emptySet() else setOf(Sequent.Unchanged)
        val accessor = org.opentaint.dataflow.ap.ifds.ElementAccessor

        val results = mutableSetOf<Sequent>()

        if (currentFactAp.base == objBase) {
            if (currentFactAp.startsWithAccessor(accessor)) {
                // Concrete: strip element accessor and rebase
                val readFact = currentFactAp.readAccessor(accessor)?.rebase(assignTo)
                if (readFact != null) {
                    results.add(mkCopy(readFact))
                }
                if (assignTo != objBase) {
                    results.add(Sequent.Unchanged)
                }
            } else if (currentFactAp.isAbstract() && accessor !in currentFactAp.exclusions) {
                // Abstract: element might be behind *
                val nonAbstract = currentFactAp.removeAbstraction()
                if (nonAbstract != null && nonAbstract.startsWithAccessor(accessor)) {
                    val readFact = nonAbstract.readAccessor(accessor)?.rebase(assignTo)
                    if (readFact != null) {
                        results.add(mkCopy(readFact))
                    }
                }
                val excludedFact = currentFactAp.exclude(accessor)
                results.add(mkCopy(excludedFact))
                results.add(Sequent.Unchanged)
            } else {
                results.add(Sequent.Unchanged)
            }
        } else if (currentFactAp.base == assignTo) {
            // Strong update: kill taint on overwritten target
        } else {
            results.add(Sequent.Unchanged)
        }

        return if (results.isEmpty()) emptySet() else results
    }

    /**
     * Container literal: target = {k: v, ...} / [v, ...] / (v, ...) / {v, ...}
     *
     * If any value in the container matches the current fact's base, propagate taint
     * to target with ElementAccessor prepended. Dict keys are not tracked.
     */
    private inline fun handleContainerLiteral(
        expr: PIRExpr,
        assignTo: AccessPathBase,
        currentFactAp: FinalFactAp,
        mkCopy: (FinalFactAp) -> Sequent,
    ): Set<Sequent> {
        val valueExpressions: List<PIRValue> = when (expr) {
            is PIRDictExpr -> expr.values
            is PIRListExpr -> expr.elements
            is PIRTupleExpr -> expr.elements
            is PIRSetExpr -> expr.elements
            else -> return setOf(Sequent.Unchanged)
        }

        val results = mutableSetOf<Sequent>()

        for (valueExpr in valueExpressions) {
            val valueBase = PIRFlowFunctionUtils.accessPathBase(valueExpr, method, ctx) ?: continue
            if (currentFactAp.base == valueBase) {
                val newFact = currentFactAp.rebase(assignTo)
                    .prependAccessor(org.opentaint.dataflow.ap.ifds.ElementAccessor)
                results.add(mkCopy(newFact))
                results.add(Sequent.Unchanged)  // value keeps its taint
                return results
            }
        }

        // No value matched — strong update if overwriting target, else pass through
        return if (currentFactAp.base == assignTo) {
            emptySet()
        } else {
            setOf(Sequent.Unchanged)
        }
    }

    // ==========================================================================
    // Return
    // ==========================================================================

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

    // ==========================================================================
    // StoreAttr: obj.attr = value
    // ==========================================================================

    /**
     * Shared logic for StoreAttr (both ZeroToFact and FactToFact for adding taint).
     * obj.attr = value: if fact is on value, propagate taint to obj.attr.
     * Also applies strong update when the current fact is on obj.attr.
     */
    private inline fun handleStoreAttrShared(
        store: PIRStoreAttr,
        currentFactAp: FinalFactAp,
        mkCopy: (FinalFactAp) -> Sequent,
    ): Set<Sequent> {
        val objBase = PIRFlowFunctionUtils.accessPathBase(store.obj, method, ctx)
            ?: return setOf(Sequent.Unchanged)
        val valueBase = PIRFlowFunctionUtils.accessPathBase(store.value, method, ctx)

        if (valueBase != null && currentFactAp.base == valueBase) {
            val accessor = org.opentaint.dataflow.ap.ifds.FieldAccessor(
                store.obj.type.typeName,
                store.attribute,
                store.value.type.typeName,
            )
            val newFact = currentFactAp.rebase(objBase).prependAccessor(accessor)
            return setOf(mkCopy(newFact), Sequent.Unchanged)
        }

        if (currentFactAp.base == objBase && factStartsWithField(currentFactAp, store.attribute)) {
            // Strong update: obj.attr is being overwritten — kill the tainted field fact
            return emptySet()
        }

        return setOf(Sequent.Unchanged)
    }

    /**
     * FactToFact StoreAttr: delegates to shared handler.
     */
    private fun handleStoreAttrFact(
        store: PIRStoreAttr,
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> = handleStoreAttrShared(store, currentFactAp) {
        Sequent.FactToFact(initialFactAp, it, null)
    }

    /**
     * Checks if a fact's access path starts with a FieldAccessor matching the given field name.
     * Uses name-only matching, ignoring className and fieldType (which may vary between instructions).
     */
    private fun factStartsWithField(factAp: FinalFactAp, fieldName: String): Boolean {
        val startAccessors = factAp.getStartAccessors()
        return startAccessors.any { it is org.opentaint.dataflow.ap.ifds.FieldAccessor && it.fieldName == fieldName }
    }

    // ==========================================================================
    // StoreSubscript: obj[index] = value
    // ==========================================================================

    /**
     * Shared logic for StoreSubscript (both ZeroToFact and FactToFact for adding taint).
     * obj[index] = value: if fact is on value, propagate taint to obj's element.
     * Also applies strong update when the current fact is on obj's element.
     */
    private inline fun handleStoreSubscriptShared(
        store: PIRStoreSubscript,
        currentFactAp: FinalFactAp,
        mkCopy: (FinalFactAp) -> Sequent,
    ): Set<Sequent> {
        val objBase = PIRFlowFunctionUtils.accessPathBase(store.obj, method, ctx)
            ?: return setOf(Sequent.Unchanged)
        val valueBase = PIRFlowFunctionUtils.accessPathBase(store.value, method, ctx)

        val accessor = org.opentaint.dataflow.ap.ifds.ElementAccessor

        if (valueBase != null && currentFactAp.base == valueBase) {
            val newFact = currentFactAp.rebase(objBase).prependAccessor(accessor)
            return setOf(mkCopy(newFact), Sequent.Unchanged)
        }

        if (currentFactAp.base == objBase && currentFactAp.startsWithAccessor(accessor)) {
            // Strong update: element is being overwritten — kill
            return emptySet()
        }

        return setOf(Sequent.Unchanged)
    }

    /**
     * FactToFact StoreSubscript: delegates to shared handler.
     */
    private fun handleStoreSubscriptFact(
        store: PIRStoreSubscript,
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> = handleStoreSubscriptShared(store, currentFactAp) {
        Sequent.FactToFact(initialFactAp, it, null)
    }
}
