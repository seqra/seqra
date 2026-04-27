package org.opentaint.ir.impl.python.protoToFlat.cfg

import org.opentaint.ir.impl.python.flat.FlatCFG
import org.opentaint.ir.impl.python.protoToFlat.ModuleContext
import org.opentaint.ir.impl.python.proto.MypyAssignmentStmtProto
import org.opentaint.ir.impl.python.proto.MypyBlockProto

/**
 * High-level entry points for building a single function-scope CFG. Each call
 * spins up a fresh [CfgSession], runs the lowering, and returns the finalized
 * [FlatCFG] (or [FlatCFG.EMPTY] on failure, with the exception reported through
 * [ModuleContext.reportException]).
 */
internal object CfgBuild {

    /** Build the CFG for a regular function/method body. */
    fun buildFunctionCfg(
        module: ModuleContext,
        qualifiedName: String,
        body: MypyBlockProto,
        sourceLabel: String = qualifiedName,
        errorPrefix: String = "Failed to build CFG for $qualifiedName",
    ): FlatCFG {
        val session = CfgSession(module = module, currentFunctionQualifiedName = qualifiedName)
        return runOrEmpty(module, sourceLabel, errorPrefix) {
            session.visitBlock(body)
            if (!session.currentBlockTerminated()) session.emitReturn(null)
            session.finalizeCfg()
        }
    }

    /**
     * Build the synthetic module-init CFG from the top-level assignment
     * statements pulled out of the module's def list. Function/class defs
     * are extracted separately and not included here.
     *
     * The mypy proto's per-statement `line` lives on the outer `MypyStmtProto`
     * wrapper, not on the inner `MypyAssignmentStmtProto`. Module-level
     * assignments come to us already unwrapped, so the line is unknown — we
     * pass `-1`, the standard "unknown line" sentinel used elsewhere in Flat IR.
     */
    fun buildModuleInitCfg(
        module: ModuleContext,
        assignments: List<MypyAssignmentStmtProto>,
    ): FlatCFG {
        val session = CfgSession(module = module)
        return runOrEmpty(
            module,
            sourceLabel = "__module_init__",
            errorPrefix = "Failed to build module_init CFG for ${module.moduleName}",
        ) {
            for (assignment in assignments) {
                if (session.currentBlockTerminated()) break
                session.visitAssignment(assignment, line = -1)
            }
            if (!session.currentBlockTerminated()) session.emitReturn(null)
            session.finalizeCfg()
        }
    }

    private inline fun runOrEmpty(
        module: ModuleContext,
        sourceLabel: String,
        errorPrefix: String,
        block: () -> FlatCFG,
    ): FlatCFG = try {
        block()
    } catch (e: Exception) {
        module.reportException(errorPrefix, sourceLabel, e)
        FlatCFG.EMPTY
    }
}
