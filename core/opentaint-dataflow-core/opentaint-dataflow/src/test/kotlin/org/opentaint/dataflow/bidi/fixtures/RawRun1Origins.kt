package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.interp.ZERO_PATTERN

/** Scalar oracle applied only after a complete compatible binding is accepted.
 * Each returned set is an ordinary atomic premise. No production store, premise policy,
 * InitialAp factory, interner, or executable support-set object supplies the answer.
 */
internal class RawRun1Origins {
    fun split(inputs: Iterable<Set<Pattern>>): List<Set<Pattern>> {
        val bindings = inputs.toList()
        check(bindings.isNotEmpty())
        check(bindings.all { it.size == 1 }) { "An executable run-1 input is atomic" }
        val inputsByOrigin = bindings.map { it.single() }
        check(inputsByOrigin.all { it.exclusion == ExclusionSet.Empty })
        val nonzero = inputsByOrigin.filterTo(LinkedHashSet()) { it.fact.base != AccessPathBase.Zero }
        // A mixed ZERO/nonzero binding produces no ZERO arm. This complete binding
        // permits ZERO only when every selected leaf contribution has a ZERO premise.
        return if (nonzero.isEmpty()) listOf(setOf(ZERO_PATTERN)) else nonzero.map { setOf(it) }
    }
}
