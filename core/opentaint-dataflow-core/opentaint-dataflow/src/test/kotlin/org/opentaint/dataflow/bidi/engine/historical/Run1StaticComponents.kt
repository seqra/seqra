package org.opentaint.dataflow.bidi.engine.historical

// Historical pure reference model. This code is never part of the runtime artifact.
import org.opentaint.dataflow.bidi.engine.*

import org.opentaint.dataflow.bidi.ap.*

internal fun isSpecialStaticRoot(initial: InitialAp): Boolean = initial.base == STATIC &&
    initial.path == null && initial.tail == Tail.STAR && initial.mark == MarkSlot.STAR &&
    initial.exclusion == ExclusionSet.Empty

internal fun isSpecialStaticRoot(pattern: Pattern): Boolean = pattern.fact.base == STATIC &&
    pattern.fact.path.isEmpty() && pattern.fact.tail == Tail.STAR && pattern.fact.mark == MarkSlot.STAR &&
    pattern.exclusion == ExclusionSet.Empty

/** Split only the root component; packed field branches keep ordinary rules. */
internal fun staticRootFact(ap: ApManager, input: Facts): Facts? {
    if (input.base != STATIC) return null
    return when (input) {
        is FlowTree -> if (input.root.leaf.isEmpty) null else ap.flowTree(STATIC, input.layer,
            input.exclusion, input.markExclusion, FlowAlgebra.leafNode(input.root.leaf), input.tail)
        is TaintTree -> if (input.root.leaf.isEmpty) null else ap.taintTree(STATIC, input.layer,
            ap.taintAlg.leafNode(input.root.leaf), input.exclusion, input.anyTail)
        is Reach -> null
    }
}

internal fun withoutStaticRootFact(ap: ApManager, input: Facts): Facts? {
    if (input.base != STATIC) return input
    return when (input) {
        is FlowTree -> if (input.root.leaf.isEmpty) input else
            FlowAlgebra.withLeaf(input.root, FlowLeaf.NONE)?.let { input.withRoot(it) }
        is TaintTree -> if (input.root.leaf.isEmpty) input else
            ap.taintAlg.withLeaf(input.root, TaintLeaves.EMPTY)?.let { input.withRoot(it) }
        is Reach -> input
    }
}
