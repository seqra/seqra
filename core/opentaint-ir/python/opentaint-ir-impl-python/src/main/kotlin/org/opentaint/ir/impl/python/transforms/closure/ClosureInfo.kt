package org.opentaint.ir.impl.python.transforms.closure

/**
 * Per-function closure facts produced by [ClosureAnalyzer.analyze] and
 * consumed by the rewriter. Pure data — purely structural facts about the
 * function's name flow, no rewriter scheduling hints.
 *
 * - [ownedNames] — names this function defines: parameters plus locals
 *   it actually owns (writes that aren't `nonlocal` / `global`).
 * - [cellVars] — owned names that must be cell-allocated because some
 *   descendant captures them.
 * - [closureVars] — names this function must receive from its parent's
 *   closure environment. Always empty when [isClosureRoot] is true.
 * - [isClosureRoot] — `true` iff the function has no closure parent
 *   (top-level free function, module init, method of a top-level/module-
 *   level class). A closure root cannot receive a `<self>` env at call
 *   time, so its [closureVars] is forced to `∅`. Note that this is not
 *   the same as `closureVars.isEmpty()` — a non-capturing inner function
 *   has empty `closureVars` but is *not* a closure root.
 */
data class ClosureInfo(
    val ownedNames: Set<String>,
    val cellVars: Set<String>,
    val closureVars: Set<String>,
    val isClosureRoot: Boolean,
)
