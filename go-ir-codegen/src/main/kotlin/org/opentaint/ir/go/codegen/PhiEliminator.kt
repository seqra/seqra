package org.opentaint.ir.go.codegen

import org.opentaint.ir.go.api.GoIRBody
import org.opentaint.ir.go.cfg.GoIRBasicBlock
import org.opentaint.ir.go.inst.GoIRPhi
import org.opentaint.ir.go.value.GoIRValue

/**
 * Eliminates SSA phi nodes by computing variable assignments to insert at the
 * end of predecessor blocks (before the terminator's branch).
 *
 * For each phi node `t5 = phi [t1, t3]` in block B with predecessors [P0, P1]:
 * - At end of P0, insert: `_phi_t5 = t1`
 * - At end of P1, insert: `_phi_t5 = t3`
 * - At start of B, insert: `t5 = _phi_t5`
 *
 * When multiple phis in the same block create parallel-assignment conflicts
 * (e.g., swap pattern where phi edges reference other phis' values),
 * a temporary variable is used to break the cycle.
 */
object PhiEliminator {

    /**
     * A phi assignment to insert at the end of a predecessor block.
     */
    data class PhiAssignment(
        val phiVarName: String,     // the phi's SSA name (e.g., "t5")
        val phiTempName: String,    // the temporary name (e.g., "_phi_t5")
        val sourceValue: GoIRValue, // the value to assign from this predecessor
    )

    /**
     * Result of phi elimination for a function body.
     *
     * @property predecessorAssignments Map from block index to list of phi assignments
     *           that must be emitted at the END of that block (before the terminator goto).
     * @property blockPhiReads Map from block index to list of (varName, tempName) pairs
     *           that must be emitted at the START of the block (reading the temp into the real var).
     */
    data class PhiEliminationResult(
        val predecessorAssignments: Map<Int, List<PhiAssignment>>,
        val blockPhiReads: Map<Int, List<Pair<String, String>>>,
    )

    /**
     * Analyzes all phi nodes in the function body and computes the necessary
     * assignments for phi elimination.
     */
    fun eliminate(body: GoIRBody): PhiEliminationResult {
        val predAssignments = mutableMapOf<Int, MutableList<PhiAssignment>>()
        val blockPhiReads = mutableMapOf<Int, MutableList<Pair<String, String>>>()

        for (block in body.blocks) {
            val phis = block.phis
            if (phis.isEmpty()) continue

            // Collect phi reads for this block
            val reads = mutableListOf<Pair<String, String>>()
            for (phi in phis) {
                val tempName = "_phi_${phi.name}"
                reads.add(phi.name to tempName)
            }
            blockPhiReads[block.index] = reads

            // For each predecessor, compute assignments
            val preds = block.predecessors
            for ((predIdx, pred) in preds.withIndex()) {
                val assignments = predAssignments.getOrPut(pred.index) { mutableListOf() }
                for (phi in phis) {
                    if (predIdx < phi.edges.size) {
                        val tempName = "_phi_${phi.name}"
                        assignments.add(
                            PhiAssignment(
                                phiVarName = phi.name,
                                phiTempName = tempName,
                                sourceValue = phi.edges[predIdx],
                            )
                        )
                    }
                }
            }
        }

        return PhiEliminationResult(predAssignments, blockPhiReads)
    }
}
