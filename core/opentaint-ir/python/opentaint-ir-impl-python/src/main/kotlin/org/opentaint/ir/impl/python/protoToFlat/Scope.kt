package org.opentaint.ir.impl.python.protoToFlat

/**
 * Per-function temp allocator. Each [org.opentaint.ir.impl.python.protoToFlat.cfg.CfgSession]
 * owns one; it does not need to be shared across functions because nested defs
 * / lambdas are lowered as independent top-level functions, each with its own
 * counter starting at `$t0`.
 *
 * Local-name resolution is currently the identity (Python locals keep their
 * source names in Flat IR), so it lives here as a single function rather than
 * a map.
 */
internal class Scope {
    private var tempCounter = 0

    fun newTemp(): String = "\$t${tempCounter++}"

    fun resolveLocal(name: String): String = name
}
