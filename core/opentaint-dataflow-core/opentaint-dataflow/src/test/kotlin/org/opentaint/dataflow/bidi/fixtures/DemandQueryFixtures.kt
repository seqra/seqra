package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.ZERO_FACT

/** A FLOW probe observes explicit index payloads without selecting a concrete static permission.
 * Static permission controls supply their actual FULL mark explicitly. */
fun demandProbe(base: AccessPathBase, path: List<Int> = emptyList(), mark: MarkSlot = MarkSlot.STAR): Pattern =
    if (base == AccessPathBase.Zero) Pattern(ZERO_FACT.copy(path = path), ExclusionSet.Empty)
    else Pattern(PathFact(base, path, Tail.ANY, mark), ExclusionSet.Empty)
