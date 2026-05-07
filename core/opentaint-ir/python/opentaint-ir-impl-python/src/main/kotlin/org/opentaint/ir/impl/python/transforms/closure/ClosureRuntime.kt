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

    /**
     * Qualified name of the synthetic adapter class for a capturing impl
     * function. The bare class name is angle-bracketed (so it cannot collide
     * with any Python identifier) and embeds the impl's already-unique
     * [fnName] (set by `freshNestedName` / `freshLambdaName` during
     * proto→Flat lifting), guaranteeing module-level uniqueness.
     */
    fun adapterClassQn(moduleName: String, fnName: String): String =
        "$moduleName.<closure_$fnName>"

    /**
     * Qualified name the capturing impl function is renamed to. Same
     * uniqueness story as [adapterClassQn]: angle brackets keep it disjoint
     * from user identifiers; the embedded [fnName] keeps it disjoint from
     * any other capturing function in the module.
     */
    fun implFunctionQn(moduleName: String, fnName: String): String =
        "$moduleName.<closure_${fnName}_impl>"
}
