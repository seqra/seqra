package org.opentaint.ir.impl.python.protoToFlat.cfg

import org.opentaint.ir.impl.python.flat.FlatAssign
import org.opentaint.ir.impl.python.flat.FlatCFG
import org.opentaint.ir.impl.python.flat.FlatLocal
import org.opentaint.ir.impl.python.flat.FlatParameter
import org.opentaint.ir.impl.python.flat.FlatParameterRef
import org.opentaint.ir.impl.python.protoToFlat.ImportManager
import org.opentaint.ir.impl.python.protoToFlat.ModuleContext
import org.opentaint.ir.impl.python.protoToFlat.recordImports
import org.opentaint.ir.impl.python.protoToFlat.recordImportsFrom
import org.opentaint.ir.impl.python.protoToFlat.toPhysicalLocation
import org.opentaint.ir.impl.python.proto.MypyBlockProto
import org.opentaint.ir.impl.python.proto.MypyStmtProto

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
        imports: ImportManager = module.imports.nestedChild(),
    ): CfgBuildResult {
        val session = CfgSession(
            module = module,
            currentFunctionQualifiedName = qualifiedName,
            currentFunctionName = functionName,
            imports = imports,
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
     * Build the synthetic module-init CFG from the top-level statements pulled
     * out of the module's def list (assignments + module-level `Import` /
     * `ImportFrom`). Function/class defs are extracted separately and not
     * included here.
     *
     * Each [MypyStmtProto] wraps its inner statement and carries the source
     * span on the outer wrapper, so module-level statements get the same
     * physical-location attribution as in-function statements.
     *
     * Two-pass shape:
     *  1. **Import-recording pass** — walk every stmt and record `import` /
     *     `from … import …` bindings into [ModuleContext.imports]. This is
     *     done up-front so that a lambda RHS in a module-level assignment
     *     (lowered during pass 2) referencing a textually-later import
     *     still sees the canonical binding.
     *  2. **Emission pass** — walk again, this time dispatching assignments
     *     into the CFG. Import statements are no-ops at this point (pass 1
     *     already updated the import scope; they emit no `FlatInst`).
     *
     * Module-init's [CfgSession] uses [ModuleContext.imports] directly (not a
     * nested child), so writes in pass 1 are visible to every subsequent
     * lowering — including top-level functions and classes, which are lowered
     * AFTER module-init in [ModuleLowering.lower].
     */
    fun buildModuleInitCfg(
        module: ModuleContext,
        statements: List<MypyStmtProto>,
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
            // Pass 1: record imports into module.imports so any lambda RHSs
            // emitted in pass 2 see the full module-level import map.
            for (stmt in statements) {
                when {
                    stmt.hasImportStmt() -> recordImports(module.imports, stmt.importStmt)
                    stmt.hasImportFromStmt() -> recordImportsFrom(module.imports, stmt.importFromStmt)
                }
            }
            // Pass 2: emit FlatInst for assignments. Import statements are
            // already recorded — skip them. Any other stmt kind in this slot
            // is a programming error; surface it.
            for (stmt in statements) {
                if (session.currentBlockTerminated()) break
                val location = stmt.toPhysicalLocation()
                when {
                    stmt.hasAssignment() -> session.visitAssignment(stmt.assignment, location)
                    stmt.hasImportStmt() || stmt.hasImportFromStmt() -> Unit
                    else -> module.reportError(
                        message = "buildModuleInitCfg: unexpected stmt kind ${stmt.kindCase} " +
                            "in MypyDefinitionProto.assignment slot",
                        source = "__module_init__",
                        code = "ModuleInitUnexpectedStmt",
                    )
                }
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
