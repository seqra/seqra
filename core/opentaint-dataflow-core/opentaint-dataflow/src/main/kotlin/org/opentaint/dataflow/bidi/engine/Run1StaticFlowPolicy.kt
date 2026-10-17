package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isFieldAccessor
import org.opentaint.dataflow.bidi.ap.*

/** Run-1 layers are independent of AP geometry. Concrete TAINT has its own path. */
internal fun normalizeRun1StaticFlow(premise: PremiseKey, facts: Facts, input: Facts? = null): Facts {
    if (facts !is FlowTree || facts.layer == Layer.DEMAND) return facts
    val staticOrigin = premise is InitialAp && premise.isFlow && premise.base == STATIC
    val demandSupport = input is FlowTree && (input.layer == Layer.DEMAND || input.base == STATIC)
    return if (staticOrigin || facts.base == STATIC || demandSupport) facts.withLayer(Layer.DEMAND) else facts
}

/** Remove only the canonical identity LEAF; descendant/sibling effects remain.
 * The owner's unconditional root default covers this transfer. Operations and
 * their requests have already run before this summary-retention boundary. */
internal fun compactRun1StaticIdentity(ops: ApOps, premise: PremiseKey, facts: Facts): Facts? {
    if (premise !is InitialAp || premise.base != STATIC || premise.tail != Tail.STAR ||
        premise.exclusion != ExclusionSet.Empty || premise.mark != MarkSlot.STAR ||
        premise.pathData.size != 2 || !premise.pathData[0].isClass() || !premise.pathData[1].isFieldAccessor() ||
        facts !is FlowTree || facts.base != STATIC || facts.layer != Layer.DEMAND ||
        facts.tail != Tail.STAR || facts.exclusion != ExclusionSet.Empty || !facts.markExclusion.isEmpty) return facts
    val identity = ops.startFact(premise).withLayer(Layer.DEMAND)
    return ops.without(facts, identity)
}
