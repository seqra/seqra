package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isFieldAccessor
import org.opentaint.dataflow.bidi.ap.*

/** Demote before evaluation, without changing AP geometry or its annotations. */
internal fun normalizeRun1StaticFlow(premise: PremiseKey, facts: Facts, input: Facts? = null): Facts {
    if (facts.layer == Layer.DEMAND) return facts
    var staticOrigin = false
    for (index in 0 until premise.size) if (premise.member(index).base == STATIC) { staticOrigin = true; break }
    val demandSupport = input != null && (input.layer == Layer.DEMAND || input.base == STATIC)
    return if (staticOrigin || facts.base == STATIC || demandSupport) facts.withLayer(Layer.DEMAND) else facts
}

/** Remove only the canonical identity LEAF; descendant/sibling effects remain.
 * The owner's unconditional root default covers this transfer. Operations and
 * their requests have already run before this summary-retention boundary. */
internal fun compactRun1StaticIdentity(ops: ApOps, premise: PremiseKey, facts: Facts): Facts? {
    // Exact concrete identity is covered by the correlated root default.
    // STAR -> ANY is an effect: it loses suffix correlation and must remain.
    if (premise is InitialAp && premise.base == STATIC && premise.tail == Tail.EXACT &&
        premise.exclusion == ExclusionSet.Empty && premise.mark is MarkSlot.Concrete &&
        premise.pathData.size == 2 && premise.pathData[0].isClass() && premise.pathData[1].isFieldAccessor() &&
        facts is TaintTree && facts.base == STATIC && facts.layer == Layer.DEMAND &&
        facts.exclusion == ExclusionSet.Empty) {
        return ops.without(facts, ops.startFact(premise).withLayer(Layer.DEMAND))
    }
    if (premise !is InitialAp || premise.base != STATIC || premise.tail != Tail.STAR ||
        premise.exclusion != ExclusionSet.Empty || premise.mark != MarkSlot.STAR ||
        premise.pathData.size != 2 || !premise.pathData[0].isClass() || !premise.pathData[1].isFieldAccessor() ||
        facts !is FlowTree || facts.base != STATIC || facts.layer != Layer.DEMAND ||
        facts.tail != Tail.STAR || facts.exclusion != ExclusionSet.Empty || !facts.markExclusion.isEmpty) return facts
    val identity = ops.startFact(premise).withLayer(Layer.DEMAND)
    return ops.without(facts, identity)
}
