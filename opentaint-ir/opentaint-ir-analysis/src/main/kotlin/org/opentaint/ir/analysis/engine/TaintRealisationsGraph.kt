package org.opentaint.ir.analysis.engine

import org.opentaint.ir.analysis.VulnerabilityInstance

data class TaintRealisationsGraph(
    val sink: IFDSVertex<DomainFact>,
    val sources: Set<IFDSVertex<DomainFact>>,
    val edges: Map<IFDSVertex<DomainFact>, Set<IFDSVertex<DomainFact>>>,
) {
    private fun getAllPaths(curPath: MutableList<IFDSVertex<DomainFact>>): Sequence<List<IFDSVertex<DomainFact>>> = sequence {
        val v = curPath.last()

        if (v == sink) {
            yield(curPath.toList())
            return@sequence
        }

        for (u in edges[v].orEmpty()) {
            if (u !in curPath) {
                curPath.add(u)
                yieldAll(getAllPaths(curPath))
                curPath.removeLast()
            }
        }
    }

    private fun getAllPaths(): Sequence<List<IFDSVertex<DomainFact>>> = sequence {
        sources.forEach {
            yieldAll(getAllPaths(mutableListOf(it)))
        }
    }

    fun toVulnerability(vulnerabilityType: String, maxPathsCount: Int = 100): VulnerabilityInstance {
        return VulnerabilityInstance(
            vulnerabilityType,
            sources.map { it.statement.toString() },
            sink.statement.toString(),
            getAllPaths().take(maxPathsCount).map { intermediatePoints ->
                intermediatePoints.map { it.statement.toString() }
            }.toList()
        )
    }
}