package org.opentaint.ir.analysis

import kotlinx.serialization.Serializable
import org.opentaint.ir.analysis.engine.VulnerabilityInstance

/**
 * Simplified version of [VulnerabilityInstance] that contains only serializable data.
 */
@Serializable
data class DumpableVulnerabilityInstance(
    val vulnerabilityType: String,
    val sources: List<String>,
    val sink: String,
    val traces: List<List<String>>
)

@Serializable
data class DumpableAnalysisResult(val foundVulnerabilities: List<DumpableVulnerabilityInstance>)

fun List<VulnerabilityInstance>.toDumpable(maxPathsCount: Int = 3): DumpableAnalysisResult {
    return DumpableAnalysisResult(map { it.toDumpable(maxPathsCount) })
}