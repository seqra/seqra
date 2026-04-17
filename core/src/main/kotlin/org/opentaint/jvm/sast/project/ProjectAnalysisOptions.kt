package org.opentaint.jvm.sast.project

import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta.Severity
import org.opentaint.jvm.sast.dataflow.DataFlowApproximationLoader
import org.opentaint.jvm.sast.dataflow.DebugOptions
import org.opentaint.jvm.sast.dataflow.TaintAnalyzerOptions
import java.nio.file.Path
import kotlin.time.Duration

data class ProjectAnalysisOptions(
    val customApproximationConfig: List<Path> = emptyList(),
    val semgrepRuleSet: List<Path> = emptyList(),
    val semgrepRuleLoadTrace: Path? = null,
    val semgrepSeverity: List<Severity> = emptyList(),
    val semgrepRuleId: List<String> = emptyList(),
    val trackExternalMethods: Boolean = false,
    val cwe: List<Int> = emptyList(),
    val useSymbolicExecution: Boolean = false,
    val symbolicExecutionTimeout: Duration = Duration.ZERO,
    val ifdsAnalysisTimeout: Duration = Duration.ZERO,
    val ifdsApMode: ApMode = ApMode.Tree,
    val projectKind: ProjectKind = ProjectKind.UNKNOWN,
    val storeSummaries: Boolean = false,
    val debugOptions: DebugOptions? = null,
    val experimentalAAInterProcCallDepth: Int = 1,
    val sarifGenerationOptions: SarifGenerationOptions = SarifGenerationOptions(),
    val approximationOptions: DataFlowApproximationLoader.Options = DataFlowApproximationLoader.Options(),
) {
    val summariesApMode get() = ifdsApMode.takeIf { storeSummaries }

    fun taintAnalyzerOptions() = TaintAnalyzerOptions(
        ifdsTimeout = ifdsAnalysisTimeout,
        ifdsApMode = ifdsApMode,
        symbolicExecutionEnabled = useSymbolicExecution,
        analysisCwe = cwe.takeIf { it.isNotEmpty() }?.toSet(),
        storeSummaries = storeSummaries,
        experimentalAAInterProcCallDepth = experimentalAAInterProcCallDepth,
        debugOptions = debugOptions
    )
}
