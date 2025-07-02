package org.opentaint.dataflow.jvm.ap.ifds

object MethodSummaryEdgeApplicationUtils {

    sealed interface SummaryEdgeTreeApplication {
        data class SummaryApRefinement(val access: AccessTree.AccessNode) : SummaryEdgeTreeApplication
        data class SummaryExclusionRefinement(val exclusion: ExclusionSet) : SummaryEdgeTreeApplication
    }

    fun tryApplySummaryEdge(
        methodInitialFact: Fact.TaintedTree,
        methodSummaryInitialFact: Fact.TaintedPath,
    ): List<SummaryEdgeTreeApplication> {
        if (methodInitialFact.ap.base != methodSummaryInitialFact.ap.base) return emptyList()

        var node = methodInitialFact.ap.access
        val access = methodSummaryInitialFact.ap.access
        if (access != null) {
            for (accessor in access) {
                if (accessor is FinalAccessor) {
                    if (!node.isFinal) return emptyList()

                    val refinement = SummaryEdgeTreeApplication.SummaryExclusionRefinement(
                        methodInitialFact.ap.exclusions.union(methodSummaryInitialFact.ap.exclusions)
                    )
                    return listOf(refinement)
                }

                node = node.getChild(accessor) ?: return emptyList()
            }
        }

        val filteredNode = when (val exclusion = methodSummaryInitialFact.ap.exclusions) {
            ExclusionSet.Empty -> node
            is ExclusionSet.Concrete -> node.filter(exclusion)
            ExclusionSet.Universe -> error("Unexpected universe exclusion in initial fact")
        }

        if (filteredNode.isEmpty) return emptyList()

        if (!filteredNode.isAbstract) return listOf(SummaryEdgeTreeApplication.SummaryApRefinement(filteredNode))

        val apRefinement = filteredNode
            .removeAbstraction()
            .takeIf { !it.isEmpty }
            ?.let { SummaryEdgeTreeApplication.SummaryApRefinement(it) }

        val exclusionRefinement = SummaryEdgeTreeApplication.SummaryExclusionRefinement(
            methodInitialFact.ap.exclusions.union(methodSummaryInitialFact.ap.exclusions)
        )

        return listOfNotNull(apRefinement, exclusionRefinement)
    }
}
