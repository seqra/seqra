package org.opentaint.ir.impl.python.protoToFlat.cfg

import org.opentaint.ir.impl.python.flat.FlatAssign
import org.opentaint.ir.impl.python.flat.FlatCFG
import org.opentaint.ir.impl.python.flat.FlatLocal
import org.opentaint.ir.impl.python.flat.FlatParameter
import org.opentaint.ir.impl.python.flat.FlatParameterRef
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

    /**
     * Result of building a function-scope CFG. Carries the CFG plus any
     * `nonlocal` / `global` declarations collected while walking the body —
     * those are needed by closure analysis and aren't reconstructible from
     * the lowered instruction stream.
     */
    data class CfgBuildResult(
        val cfg: FlatCFG,
        val nonlocalNames: Set<String>,
        val globalNames: Set<String>,
    ) {
        companion object {
            val EMPTY = CfgBuildResult(FlatCFG.EMPTY, emptySet(), emptySet())
        }
    }

    /**
     * Build the CFG for a regular function/method body.
     *
     * [parameters] is the function's parameter list in declaration order;
     * one [FlatAssign] copy from `FlatParameterRef(name)` into
     * `FlatLocal(name)` is prepended to the entry block per parameter, in
     * order. After this prologue the body is free to read/write each
     * parameter slot as a regular [FlatLocal] — downstream passes don't need
     * to special-case parameter names.
     *
     * This is the only emission point in `protoToFlat` for [FlatParameterRef].
     * The closure rewriter (`RewriteCtx`) emits additional [FlatParameterRef]s
     * later — for the synthetic `<self>` env parameter prepended to capturing
     * children — so consumers should not assume the RHS-of-entry-prologue
     * shape is the only context where [FlatParameterRef] appears.
     */
    fun buildFunctionCfg(
        module: ModuleContext,
        qualifiedName: String,
        functionName: String,
        body: MypyBlockProto,
        parameters: List<FlatParameter>,
        sourceLabel: String = qualifiedName,
        errorPrefix: String = "Failed to build CFG for $qualifiedName",
    ): CfgBuildResult {
        val session = CfgSession(
            module = module,
            currentFunctionQualifiedName = qualifiedName,
            currentFunctionName = functionName,
        )
        return runOrEmpty(module, sourceLabel, errorPrefix) {
            for (param in parameters) {
                session.emit(
                    FlatAssign(
                        target = FlatLocal(param.name, param.type),
                        source = FlatParameterRef(param.name, param.type),
                    ),
                )
            }
            session.visitBlock(body)
            if (!session.currentBlockTerminated()) session.emitReturn(null)
            CfgBuildResult(session.finalizeCfg(), session.nonlocalNames, session.globalNames)
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
        // `nonlocal` / `global` declarations collected by the session are
        // intentionally dropped here: module init has no enclosing scope, so
        // they have no effect.
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
            CfgBuildResult(session.finalizeCfg(), session.nonlocalNames, session.globalNames)
        }.cfg
    }

    private inline fun runOrEmpty(
        module: ModuleContext,
        sourceLabel: String,
        errorPrefix: String,
        block: () -> CfgBuildResult,
    ): CfgBuildResult = try {
        block()
    } catch (e: Exception) {
        module.reportException(errorPrefix, sourceLabel, e)
        CfgBuildResult.EMPTY
    }
}
