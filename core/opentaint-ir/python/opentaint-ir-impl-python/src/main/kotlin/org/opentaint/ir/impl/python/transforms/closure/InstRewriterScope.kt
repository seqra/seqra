package org.opentaint.ir.impl.python.transforms.closure

import org.opentaint.ir.impl.python.flat.FlatInst

/**
 * Per-instruction emit buffer. Owns the (possibly-rewritten) core
 * instruction plus the `pre` and `post` lists wrapping it. Generic — the
 * scope itself has no knowledge of cells, operands, or any other
 * transform-specific concept.
 *
 * Constructed with the original instruction as the core, so a handler
 * that doesn't actually rewrite anything can leave the scope alone and
 * `finish()` returns `[original]`. Handlers that *do* rewrite call
 * [replaceWith] to swap in the new core.
 *
 * Lifetime: one [InstRewriterScope] per instruction; never reused. State
 * does not leak across instructions.
 */
internal class InstRewriterScope(original: FlatInst) {
    private val pre = ArrayList<FlatInst>()
    private var core: FlatInst = original
    private val post = ArrayList<FlatInst>()

    fun emitBefore(inst: FlatInst) { pre += inst }
    fun emitAfter(inst: FlatInst) { post += inst }

    /** Replace the core instruction. */
    fun replaceWith(inst: FlatInst) { core = inst }

    fun finish(): List<FlatInst> = pre + core + post
}
