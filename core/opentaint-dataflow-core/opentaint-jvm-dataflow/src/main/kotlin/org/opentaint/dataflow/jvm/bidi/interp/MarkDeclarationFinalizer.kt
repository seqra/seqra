package org.opentaint.dataflow.jvm.bidi.interp

import org.opentaint.dataflow.bidi.ap.FrozenMarkDeclarations
import org.opentaint.dataflow.bidi.ap.FullKey
import org.opentaint.dataflow.bidi.ap.MarkDeclarations
import org.opentaint.dataflow.bidi.ap.MarkGroupKey
import org.opentaint.dataflow.bidi.ap.RawMarkDeclarations
import org.opentaint.dataflow.bidi.ap.encodeGroup
import org.opentaint.dataflow.jvm.ap.ifds.taint.PrimitiveTaintExt
import org.opentaint.dataflow.util.Cancellation
import java.util.Collections

/** Merge declarations, not executable rules. Fallback ownership is assigned only at finalization. */
fun mergeRawMarkDeclarations(parts: Iterable<RawMarkDeclarations>): RawMarkDeclarations {
    val names = linkedSetOf<String>()
    val owners = linkedMapOf<String, MarkGroupKey>()
    val flags = linkedMapOf<MarkGroupKey, Boolean>()
    for (part in parts) {
        names += part.names
        names += part.explicitOwners.keys
        part.explicitOwners.forEach { (name, owner) -> claim(owners, name, owner) }
        part.primitiveByOwner.forEach { (owner, primitive) -> claim(flags, owner, primitive) }
    }
    return RawMarkDeclarations(
        Collections.unmodifiableSet(LinkedHashSet(names)),
        Collections.unmodifiableMap(LinkedHashMap(owners)),
        Collections.unmodifiableMap(LinkedHashMap(flags)),
    )
}

/** Build one immutable index after the complete provider chain has supplied its declarations. */
fun finalizeMarkDeclarations(raw: RawMarkDeclarations, cancellation: Cancellation): MarkDeclarations {
    val flags = LinkedHashMap(raw.primitiveByOwner)
    val keys = linkedMapOf<String, FullKey>()
    for (name in raw.names + raw.explicitOwners.keys) {
        cancellation.checkpoint()
        val owner = raw.explicitOwners[name] ?: MarkGroupKey.SharedModel
        val primitive = name.endsWith(PrimitiveTaintExt.PRIMITIVE_TRACKING_ENABLED_MODE)
        claim(flags, owner, primitive)
        keys[name] = FullKey(encodeGroup(owner, primitive), name)
    }
    return FrozenMarkDeclarations(keys)
}

private fun <K, V : Any> claim(target: MutableMap<K, V>, key: K, value: V) {
    val previous = target.putIfAbsent(key, value)
    require(previous == null || previous == value) { "Conflicting mark declaration: $key" }
}
