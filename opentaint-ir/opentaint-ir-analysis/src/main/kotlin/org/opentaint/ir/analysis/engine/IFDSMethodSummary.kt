package org.opentaint.ir.analysis.engine

import org.opentaint.ir.analysis.AnalysisResult

data class CalleeInfo(
    val factsAtCalleeStart: Set<IFDSVertex>,
    val callsiteRealisationsGraph: TaintRealisationsGraph
)

data class IFDSMethodSummary(
    val factsAtExits: Map<IFDSVertex, Set<IFDSVertex>>,
    val crossUnitCallees: Map<IFDSVertex, CalleeInfo>,
    val foundVulnerabilities: AnalysisResult
)