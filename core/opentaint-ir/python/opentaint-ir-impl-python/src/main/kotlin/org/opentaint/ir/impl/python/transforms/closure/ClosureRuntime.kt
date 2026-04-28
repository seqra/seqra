package org.opentaint.ir.impl.python.transforms.closure

/**
 * Names used by the closure-lowering transform. Kept here (not in proto→Flat
 * lowering) so the first pass stays closure-agnostic.
 *
 * Synthetic names use characters invalid in Python identifiers so the
 * closure transform (and later passes) can distinguish them from
 * user-written names.
 */
object ClosureRuntime {
    const val SELF_PARAM_NAME = "<self>"
    const val CLOSURE_ATTR_NAME = "_closure_env_"
    const val CELL_CTOR_NAME = "__pir_cell__"
    const val CELL_VALUE_ATTR = "value"
}
