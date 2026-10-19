package org.opentaint.dataflow.jvm.bidi.fixtures

import org.opentaint.dataflow.bidi.ap.*

/** Complete finite inventory for the shared JVM bytecode fixtures. No runtime registration. */
internal val jvmFixtureMarkNames = setOf(
    "<zero>", "A", "B", "N", "ACTIVE", "BOUNDARY", "END", "END_ONLY", "ENTRY_P", "IN", "NEVER",
    "OUT", "Out", "P", "Q", "R", "READ", "S", "SOURCE", "T", "T%%primitive%%", "U", "V", "W",
    "output T", "real entry T", "%%primitive%%", "many%%primitive%%%%primitive%%",
    "T%%primitive%%suffix", "T%%PRIMITIVE%%", "primitive",
) + listOf("SOURCE", "SINK").flatMap { role ->
    listOf("POS", "NEG", "CONTRADICTION", "FALSE").map { "${role}_$it" }
}

internal fun jvmTestDeclarations(
    names: Set<String> = jvmFixtureMarkNames,
    owners: Map<String, MarkGroupKey> = emptyMap(),
): MarkDeclarations {
    val flags = linkedMapOf<MarkGroupKey, Boolean>()
    return FrozenMarkDeclarations(names.associateWith { name ->
        val owner = owners[name] ?: MarkGroupKey.Model(name)
        val flag = name.endsWith("%%primitive%%")
        val previous = flags.putIfAbsent(owner, flag)
        require(previous == null || previous == flag)
        FullKey(encodeGroup(owner, flag), name)
    })
}
