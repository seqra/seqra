package org.opentaint.ir.impl.python.transforms.closure

import org.opentaint.ir.api.python.PIRDiagnostic
import org.opentaint.ir.api.python.PIRDiagnosticSeverity
import org.opentaint.ir.impl.python.flat.FlatClass
import org.opentaint.ir.impl.python.flat.FlatFunctionIR
import org.opentaint.ir.impl.python.flat.FlatModuleIR

/**
 * Rewrites a [FlatModuleIR] using per-function [ClosureInfo] from
 * [ClosureAnalyzer]. Pure: input module is not modified.
 *
 * For every function with non-empty `cellVars ∪ closureVars`:
 *   - prepends `<self>` to `parameters` if `closureVars` is non-empty;
 *   - prepends a prologue allocating own cells (`__pir_cell__()`),
 *     seeding parameter cells, and extracting received cells from
 *     `<self>._closure_env_`;
 *   - rewires reads of cell-managed locals through `FlatLoadAttr` and
 *     writes through `FlatStoreAttr` against `$cell$name`;
 *
 * **Callable-shim shape** (per `.agents/callable-shim/plan.md`): for every
 * capturing function (`closureVars` non-empty) the rewriter additionally
 * synthesizes an adapter `FlatClass` whose `__init__` stores `_closure_env_`
 * on `self` and whose `__call__` forwards to the renamed impl function. At
 * each `FlatBindFunction(target, child)` whose child captures, the bind is
 * replaced by a `FlatBuildDict + FlatCall` constructing the adapter class
 * with the env dict as its only argument.
 *
 * `FlatFunctionIR.closureVars` is populated from [ClosureInfo.closureVars]
 * so the PIR converter sees it.
 *
 * Implementation is split across:
 *  - [CapturingPlan] — synthetic name allocation.
 *  - [AdapterClassBuilder] — pure adapter-class synthesis.
 *  - [RewriteCtx] — per-function rewrite state and body walk.
 */
internal object ClosureRewriter {

    fun rewrite(module: FlatModuleIR, info: Map<String, ClosureInfo>): FlatModuleIR {
        val diagnostics = ArrayList<PIRDiagnostic>()

        // First scan: pick adapter class names + impl renames for every capturing
        // function. Both decisions must be visible BEFORE we walk bind sites.
        val capturingPlan = buildCapturingPlan(module, info)

        val adapterClasses = ArrayList<FlatClass>()
        val runner = RewriteRunner(info, capturingPlan, diagnostics)

        val newFunctions = module.functions.map {
            val out = runner.rewriteFunction(it)
            out.adapterClass?.let(adapterClasses::add)
            out.impl
        }
        val newModuleInit = runner.rewriteFunction(module.moduleInit).also {
            // Module init is never capturing — its kind is MODULE_INIT, a closure root.
            it.adapterClass?.let(adapterClasses::add)
        }.impl
        val newClasses = module.classes.map { runner.rewriteClass(it, adapterClasses) }

        return module.copy(
            functions = newFunctions,
            moduleInit = newModuleInit,
            classes = newClasses + adapterClasses,
            diagnostics = module.diagnostics + diagnostics,
        )
    }
}

/**
 * Per-module rewrite driver. Holds the lookups every per-function
 * [RewriteCtx] needs and isolates rewrite failures into diagnostics.
 */
private class RewriteRunner(
    private val info: Map<String, ClosureInfo>,
    private val capturingPlan: Map<String, CapturingEntry>,
    private val diagnostics: MutableList<PIRDiagnostic>,
) {

    fun rewriteClass(
        cls: FlatClass,
        adapterAccumulator: MutableList<FlatClass>,
    ): FlatClass = cls.copy(
        methods = cls.methods.map {
            val out = rewriteFunction(it)
            out.adapterClass?.let(adapterAccumulator::add)
            out.impl
        },
        nestedClasses = cls.nestedClasses.map { rewriteClass(it, adapterAccumulator) },
    )

    /**
     * Per-function rewrite. Returns a [RewriteOutput] holding the new impl
     * function and an optional adapter class.
     *
     * Only [ClosureRewriteLimitation] (documented unsupported shapes) is
     * caught and downgraded to a diagnostic. Any other exception — including
     * [IllegalStateException] from `error(...)` invariant checks and
     * [IllegalArgumentException] from contract checks like `withTarget` —
     * indicates a programmer error and propagates: silently downgrading
     * those produces an inconsistent module (e.g. parents committed to an
     * adapter-class shape whose impl never got renamed because the impl
     * rewrite was bailed).
     */
    fun rewriteFunction(fn: FlatFunctionIR): RewriteOutput {
        val ci = info[fn.qualifiedName] ?: return RewriteOutput(fn)

        if (ci.cellVars.isEmpty() && ci.closureVars.isEmpty() && !ci.hasCapturingChildBind) {
            return RewriteOutput(fn)
        }

        return try {
            RewriteCtx(fn, ci, info, capturingPlan).run()
        } catch (e: ClosureRewriteLimitation) {
            diagnostics.add(
                PIRDiagnostic(
                    severity = PIRDiagnosticSeverity.ERROR,
                    message = "Closure rewrite failed for ${fn.qualifiedName}: ${e.message}",
                    functionName = fn.qualifiedName,
                    exceptionType = e::class.simpleName ?: "ClosureRewriteLimitation",
                ),
            )
            RewriteOutput(fn)
        }
    }
}
