package org.opentaint.ir.impl.python.transforms.closure

import org.opentaint.ir.impl.python.flat.FlatModuleIR

/**
 * Per-capturing-function plan: synthetic adapter class name + impl-rename target.
 *
 * Keys are the *original* qualifiedName of the capturing impl function.
 */
internal data class CapturingEntry(
    val originalQn: String,
    val adapterClassName: String,           // bare name like "<closure_outer$inner>"
    val adapterClassQn: String,             // module.<closure_outer$inner>
    val implRenamedName: String,            // unique-name in module.functions
    val implRenamedQn: String,              // module.<closure_outer$inner_impl>
)

/**
 * First scan: pick adapter class names + impl renames for every capturing
 * function. Both decisions must be visible BEFORE we walk bind sites.
 */
internal fun buildCapturingPlan(
    module: FlatModuleIR,
    info: Map<String, ClosureInfo>,
): Map<String, CapturingEntry> {
    val taken = HashSet<String>()
    // Reserve every existing function name + every existing class name to avoid collisions.
    for (fn in module.functions) taken.add(fn.name)
    for (fn in module.functions) taken.add(fn.qualifiedName)
    for (cls in module.classes) {
        taken.add(cls.name)
        taken.add(cls.qualifiedName)
    }
    taken.add(module.moduleInit.name)
    taken.add(module.moduleInit.qualifiedName)

    val out = LinkedHashMap<String, CapturingEntry>()
    for (fn in module.functions) {
        val ci = info[fn.qualifiedName] ?: continue
        if (ci.closureVars.isEmpty()) continue
        val baseName = fn.name
        // Synthetic angle-bracketed names cannot collide with user identifiers.
        val adapterClassName = uniquifyAngleBracketed("<closure_$baseName>", taken)
        val implRenamedName = uniquifyAngleBracketed("<closure_${baseName}_impl>", taken)
        val adapterClassQn = "${module.moduleName}.$adapterClassName"
        val implRenamedQn = "${module.moduleName}.$implRenamedName"
        taken.add(adapterClassName)
        taken.add(implRenamedName)
        taken.add(adapterClassQn)
        taken.add(implRenamedQn)
        out[fn.qualifiedName] = CapturingEntry(
            originalQn = fn.qualifiedName,
            adapterClassName = adapterClassName,
            adapterClassQn = adapterClassQn,
            implRenamedName = implRenamedName,
            implRenamedQn = implRenamedQn,
        )
    }
    return out
}

/**
 * Make [candidate] unique against [taken] by appending `_2`, `_3`, … *before*
 * the trailing `>`. Assumes [candidate] ends with `>` (i.e. is in the
 * angle-bracketed synthetic-name shape used throughout this transform).
 */
private fun uniquifyAngleBracketed(candidate: String, taken: Set<String>): String {
    if (candidate !in taken) return candidate
    require(candidate.endsWith(">")) {
        "uniquifyAngleBracketed expects an angle-bracketed candidate, got: $candidate"
    }
    var i = 2
    while (true) {
        val attempt = candidate.removeSuffix(">") + "_$i>"
        if (attempt !in taken) return attempt
        i++
    }
}
