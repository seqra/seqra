package org.opentaint.ir.impl.python.transforms.closure

import org.opentaint.ir.impl.python.flat.FlatModuleIR

/**
 * Pure `FlatModuleIR -> FlatModuleIR` closure-lowering pass.
 *
 * Runs [ClosureAnalyzer] over the module to compute per-function
 * [ClosureInfo], then [ClosureRewriter] to materialise cells, env extraction,
 * and bind-site env attaching. The output module is what the
 * `flatToPir` stage consumes.
 */
object FlatClosureTransformer {
    fun transform(module: FlatModuleIR): FlatModuleIR {
        val info = ClosureAnalyzer.analyze(module)
        return ClosureRewriter.rewrite(module, info)
    }
}
