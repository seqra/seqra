package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.Publication
import org.opentaint.dataflow.bidi.engine.isSpecialStaticRoot

/** Local rule/work tests observe computed rows. The separate ordinary-default
 * driver controls check the precomputed identity, both layers and notifications.
 * No field, concrete, generated or nonidentity static result is removed here. */
private fun isPrecomputedStaticDefault(premise: PremiseKey, facts: Facts): Boolean =
    premise is InitialAp && isSpecialStaticRoot(premise) && facts is FlowTree &&
        facts.base == STATIC && facts.tail == Tail.STAR && facts.exclusion == ExclusionSet.Empty &&
        facts.markExclusion.isEmpty && facts.root.accessorData == null && !facts.root.leaf.isEmpty

internal fun Sequence<Pair<PremiseKey, Facts>>.withoutPrecomputedStaticDefault(): Sequence<Pair<PremiseKey, Facts>> =
    filterNot { (premise, facts) -> isPrecomputedStaticDefault(premise, facts) }

internal fun Iterable<Publication>.withoutPrecomputedStaticDefault(): List<Publication> =
    filterNot { isPrecomputedStaticDefault(it.premise, it.conclusion) }
