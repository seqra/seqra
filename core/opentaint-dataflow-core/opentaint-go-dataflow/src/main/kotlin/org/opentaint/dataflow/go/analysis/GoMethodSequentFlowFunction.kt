package org.opentaint.dataflow.go.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.opentaint.dataflow.go.GoFlowFunctionUtils
import org.opentaint.dataflow.go.GoFlowFunctionUtils.Access
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.expr.GoIRBinOpExpr
import org.opentaint.ir.go.inst.*
import org.opentaint.ir.go.type.GoIRBinaryOp

/**
 * Handles intraprocedural taint propagation: assignments, stores, returns, phi nodes, map updates.
 * This is the most complex flow function.
 */
class GoMethodSequentFlowFunction(
    private val apManager: ApManager,
    private val context: GoMethodAnalysisContext,
    private val currentInst: GoIRInst,
    private val generateTrace: Boolean,
) : MethodSequentFlowFunction {

    private val method: GoIRFunction get() = context.method

    override fun propagateZeroToZero(): Set<Sequent> {
        return setOf(Sequent.ZeroToZero)
    }

    override fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<Sequent> {
        return propagate(null, currentFactAp)
    }

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> {
        return propagate(initialFactAp, currentFactAp)
    }

    override fun propagateNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> {
        return setOf(Sequent.Unchanged)
    }

    private fun propagate(initialFact: InitialFactAp?, currentFact: FinalFactAp): Set<Sequent> {
        return when (currentInst) {
            is GoIRAssignInst -> handleAssign(initialFact, currentFact, currentInst)
            is GoIRStore -> handleStore(initialFact, currentFact, currentInst)
            is GoIRReturn -> handleReturn(initialFact, currentFact, currentInst)
            is GoIRPhi -> handlePhi(initialFact, currentFact, currentInst)
            is GoIRMapUpdate -> handleMapUpdate(initialFact, currentFact, currentInst)
            else              -> setOf(Sequent.Unchanged)
        }
    }

    // ── Assignment ───────────────────────────────────────────────────

    private fun handleAssign(
        initialFact: InitialFactAp?,
        currentFact: FinalFactAp,
        inst: GoIRAssignInst,
    ): Set<Sequent> {
        val registerBase = AccessPathBase.LocalVar(inst.register.index)
        val expr = inst.expr

        // String concatenation — multiple operands
        if (expr is GoIRBinOpExpr && expr.op == GoIRBinaryOp.ADD
            && GoFlowFunctionUtils.isStringType(expr.type)
        ) {
            return handleStringConcat(initialFact, currentFact, registerBase, expr)
        }

        val rhsAccess = GoFlowFunctionUtils.exprToAccess(expr, method)
            ?: return handleNonPropagatingExpr(currentFact, registerBase)

        return when (rhsAccess) {
            is Access.Simple -> handleSimpleAssign(initialFact, currentFact, registerBase, rhsAccess.base)
            is Access.RefAccess -> handleRefAssign(initialFact, currentFact, registerBase, rhsAccess)
        }
    }

    private fun handleSimpleAssign(
        initialFact: InitialFactAp?,
        currentFact: FinalFactAp,
        toBase: AccessPathBase,
        fromBase: AccessPathBase,
    ): Set<Sequent> {
        val result = mutableSetOf<Sequent>()

        // Kill: if fact is about the destination, the assignment overwrites it
        if (currentFact.base == toBase) {
            if (fromBase == toBase) {
                result.add(Sequent.Unchanged)
                return result
            }
            // Don't add Unchanged — fact is killed
        } else {
            result.add(Sequent.Unchanged)
        }

        // Gen: if fact is about the source, generate taint on destination
        if (currentFact.base == fromBase) {
            val newFact = currentFact.rebase(toBase)
            result.add(makeEdge(initialFact, newFact))
        }

        return result
    }

    private fun handleRefAssign(
        initialFact: InitialFactAp?,
        currentFact: FinalFactAp,
        toBase: AccessPathBase,
        rhsAccess: Access.RefAccess,
    ): Set<Sequent> {
        val result = mutableSetOf<Sequent>()

        // Kill: assignment to register overwrites previous value
        if (currentFact.base == toBase) {
            // Don't add Unchanged — register gets new value
        } else {
            result.add(Sequent.Unchanged)
        }

        // Gen: if fact is about the source object AND the accessor matches
        if (currentFact.base == rhsAccess.base) {
            if (currentFact.startsWithAccessor(rhsAccess.accessor)) {
                // Concrete: strip accessor and rebase
                val readFact = currentFact.readAccessor(rhsAccess.accessor)
                if (readFact != null) {
                    result.add(makeEdge(initialFact, readFact.rebase(toBase)))
                }
            } else if (currentFact.isAbstract()
                && !currentFact.exclusions.contains(rhsAccess.accessor)
            ) {
                // Abstract: trigger refinement by adding accessor to exclusion set
                val refinedFact = currentFact.exclude(rhsAccess.accessor)
                result.add(makeEdge(initialFact, refinedFact))
            }
        }

        return result
    }

    // ── Store ────────────────────────────────────────────────────────

    private fun handleStore(
        initialFact: InitialFactAp?,
        currentFact: FinalFactAp,
        inst: GoIRStore,
    ): Set<Sequent> {
        val valueBase = GoFlowFunctionUtils.accessPathBase(inst.value, method)
            ?: return setOf(Sequent.Unchanged)
        val addrAccess = GoFlowFunctionUtils.accessForAddr(inst.addr, method)
            ?: return setOf(Sequent.Unchanged)

        val result = mutableSetOf<Sequent>()

        when (addrAccess) {
            is Access.RefAccess -> {
                val destBase = addrAccess.base
                val accessor = addrAccess.accessor

                // Kill/preserve
                if (currentFact.base == destBase) {
                    if (currentFact.startsWithAccessor(accessor)) {
                        if (accessor is ElementAccessor) {
                            result.add(Sequent.Unchanged) // Weak update for elements
                        }
                        // FieldAccessor: strong update — don't preserve
                    } else if (currentFact.isAbstract()
                        && !currentFact.exclusions.contains(accessor)
                    ) {
                        val refinedFact = currentFact.exclude(accessor)
                        result.add(makeEdge(initialFact, refinedFact))
                    } else {
                        result.add(Sequent.Unchanged)
                    }
                } else {
                    result.add(Sequent.Unchanged)
                }

                // Gen: if value is tainted, write taint into dest.accessor
                if (currentFact.base == valueBase) {
                    val newFact = currentFact.rebase(destBase).prependAccessor(accessor)
                    result.add(makeEdge(initialFact, newFact))
                }
            }

            is Access.Simple -> {
                val destBase = addrAccess.base

                if (currentFact.base == destBase) {
                    // Pointer store: overwritten
                } else {
                    result.add(Sequent.Unchanged)
                }

                if (currentFact.base == valueBase) {
                    val newFact = currentFact.rebase(destBase)
                    result.add(makeEdge(initialFact, newFact))
                }
            }
        }

        return result
    }

    // ── Return ───────────────────────────────────────────────────────

    private fun handleReturn(
        initialFact: InitialFactAp?,
        currentFact: FinalFactAp,
        inst: GoIRReturn,
    ): Set<Sequent> {
        val result = mutableSetOf<Sequent>(Sequent.Unchanged)

        for (retVal in inst.results) {
            val retBase = GoFlowFunctionUtils.accessPathBase(retVal, method) ?: continue
            if (currentFact.base == retBase) {
                val exitFact = currentFact.rebase(AccessPathBase.Return)
                result.add(makeEdge(initialFact, exitFact))
            }
        }

        return result
    }

    // ── Phi ──────────────────────────────────────────────────────────

    private fun handlePhi(
        initialFact: InitialFactAp?,
        currentFact: FinalFactAp,
        inst: GoIRPhi,
    ): Set<Sequent> {
        val result = mutableSetOf<Sequent>()
        val registerBase = AccessPathBase.LocalVar(inst.register.index)

        if (currentFact.base == registerBase) {
            // Don't add Unchanged — overwritten by phi
        } else {
            result.add(Sequent.Unchanged)
        }

        for (edge in inst.edges) {
            val edgeBase = GoFlowFunctionUtils.accessPathBase(edge, method) ?: continue
            if (currentFact.base == edgeBase) {
                val newFact = currentFact.rebase(registerBase)
                result.add(makeEdge(initialFact, newFact))
                break
            }
        }

        return result
    }

    // ── Map Update ───────────────────────────────────────────────────

    private fun handleMapUpdate(
        initialFact: InitialFactAp?,
        currentFact: FinalFactAp,
        inst: GoIRMapUpdate,
    ): Set<Sequent> {
        val result = mutableSetOf<Sequent>(Sequent.Unchanged) // weak update
        val mapBase = GoFlowFunctionUtils.accessPathBase(inst.map, method)
            ?: return setOf(Sequent.Unchanged)
        val valueBase = GoFlowFunctionUtils.accessPathBase(inst.value, method)
            ?: return setOf(Sequent.Unchanged)

        if (currentFact.base == valueBase) {
            val newFact = currentFact.rebase(mapBase).prependAccessor(ElementAccessor)
            result.add(makeEdge(initialFact, newFact))
        }

        return result
    }

    // ── String Concat ────────────────────────────────────────────────

    private fun handleStringConcat(
        initialFact: InitialFactAp?,
        currentFact: FinalFactAp,
        registerBase: AccessPathBase,
        expr: GoIRBinOpExpr,
    ): Set<Sequent> {
        val result = mutableSetOf<Sequent>()

        if (currentFact.base == registerBase) {
            // Don't add unchanged — overwritten
        } else {
            result.add(Sequent.Unchanged)
        }

        val leftBase = GoFlowFunctionUtils.accessPathBase(expr.x, method)
        val rightBase = GoFlowFunctionUtils.accessPathBase(expr.y, method)

        if (leftBase != null && currentFact.base == leftBase) {
            result.add(makeEdge(initialFact, currentFact.rebase(registerBase)))
        }
        if (rightBase != null && currentFact.base == rightBase) {
            result.add(makeEdge(initialFact, currentFact.rebase(registerBase)))
        }

        return result
    }

    // ── Non-propagating expression ───────────────────────────────────

    private fun handleNonPropagatingExpr(
        currentFact: FinalFactAp,
        registerBase: AccessPathBase,
    ): Set<Sequent> {
        return if (currentFact.base == registerBase) {
            emptySet() // register overwritten with clean value
        } else {
            setOf(Sequent.Unchanged)
        }
    }

    // ── Edge creation helper ─────────────────────────────────────────

    private fun makeEdge(initialFact: InitialFactAp?, newFact: FinalFactAp): Sequent {
        val traceInfo = if (generateTrace) MethodSequentFlowFunction.TraceInfo.Flow else null
        return if (initialFact != null) {
            Sequent.FactToFact(initialFact, newFact, traceInfo)
        } else {
            Sequent.ZeroToFact(newFact, traceInfo)
        }
    }
}
