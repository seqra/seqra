package org.opentaint.ir.impl.python.transforms.closure

/**
 * Per-function closure facts produced by [ClosureAnalyzer.analyze] and
 * consumed by the rewriter (step 5). Pure data.
 *
 * - [ownedNames] — names this function defines: parameters plus locals
 *   it actually owns (writes that aren't `nonlocal` / `global`).
 * - [cellVars] — owned names that must be cell-allocated because some
 *   descendant captures them.
 * - [closureVars] — names this function must receive from its parent's
 *   closure environment. Empty for closure roots
 *   (TOP_LEVEL / METHOD / MODULE_INIT).
 */
data class ClosureInfo(
    val ownedNames: Set<String>,
    val cellVars: Set<String>,
    val closureVars: Set<String>,
)
