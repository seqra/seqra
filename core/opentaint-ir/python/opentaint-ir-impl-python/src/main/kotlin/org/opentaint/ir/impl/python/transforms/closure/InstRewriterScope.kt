package org.opentaint.ir.impl.python.transforms.closure

import org.opentaint.ir.impl.python.flat.FlatInst

/**
 * Per-instruction emit buffer. Owns the `pre` (instructions emitted before
 * the rewritten core) and `post` (after) lists for one instruction's
 * rewrite. Generic over what the rewrite does — the scope itself has no
 * knowledge of cells, operands, or any other transform-specific concept.
 *
 * Lifetime: one [InstRewriterScope] per instruction; never reused. State
 * does not leak across instructions.
 *
 * Typical use:
 * ```
 * val scope = InstRewriterScope()
 * val withOps = inst.mapOperand { v -> /* transform; may scope.emitBefore(...) */ }
 * val target  = inst.target?.let { /* transform; may scope.emitAfter(...) */ } ?: ...
 * return scope.finish(withOps.withTarget(target))
 * ```
 */
internal class InstRewriterScope {
    private val pre = ArrayList<FlatInst>()
    private val post = ArrayList<FlatInst>()

    fun emitBefore(inst: FlatInst) { pre += inst }
    fun emitBefore(insts: List<FlatInst>) { pre += insts }
    fun emitAfter(inst: FlatInst) { post += inst }
    fun emitAfter(insts: List<FlatInst>) { post += insts }

    fun finish(rewritten: FlatInst): List<FlatInst> = pre + rewritten + post
    fun finish(rewritten: List<FlatInst>): List<FlatInst> = pre + rewritten + post
}
