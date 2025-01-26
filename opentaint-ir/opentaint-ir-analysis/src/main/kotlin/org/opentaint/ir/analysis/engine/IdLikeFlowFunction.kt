package org.opentaint.ir.analysis.engine

/**
 * Flow function which is equal to id for all elements from [domain] except those in [nonId], for which the result is stored in the map
 * For now, this class is not used, but it may be helpful for some analysis
 */
abstract class IdLikeFlowFunction(
    private val domain: Set<DomainFact>,
    private val nonId: Map<DomainFact, Collection<DomainFact>>
): FlowFunctionInstance {

    override fun compute(fact: DomainFact): Collection<DomainFact> {
        nonId[fact]?.let {
            return it
        }
        return if (domain.contains(fact)) listOf(fact) else emptyList()
    }
}