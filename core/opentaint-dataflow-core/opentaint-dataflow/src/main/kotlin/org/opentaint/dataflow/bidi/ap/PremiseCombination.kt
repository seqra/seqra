package org.opentaint.dataflow.bidi.ap


/** Combine original inputs only after the existing complete condition has been admitted. */
fun interface PremiseCombination {
    fun combine(inputs: Iterable<PremiseKey>): PremiseKey
}

/** Later runs keep the complete canonical conjunction. The shared AP manager has no run mode. */
class ExactPremises(private val manager: ApManager) : PremiseCombination {
    override fun combine(inputs: Iterable<PremiseKey>): PremiseKey {
        var result: PremiseKey = manager.zero
        var present = false
        for (input in inputs) {
            manager.cancellation.checkpoint()
            result = manager.union(result, input)
            present = true
        }
        check(present) { "A complete condition needs at least one input" }
        return result
    }
}
