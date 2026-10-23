package org.opentaint.common.sast

import org.opentaint.common.sast.dataflow.DebugOptions
import org.opentaint.common.sast.dataflow.TaintAnalyzerOptions
import org.opentaint.common.sast.sarif.SarifGenerationOptions
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import java.nio.file.Path
import kotlin.time.Duration

data class CommonAnalysisOptions(
    val customApproximationConfig: List<Path> = emptyList(),
    val semgrepRuleSet: List<Path> = emptyList(),
    val semgrepRuleLoadTrace: Path? = null,
    val semgrepSeverity: List<CommonTaintConfigurationSinkMeta.Severity> = emptyList(),
    val semgrepRuleId: List<String> = emptyList(),
    val semgrepRuleIdExclude: List<String> = emptyList(),
    val trackExternalMethods: Boolean = false,
    val ifdsAnalysisTimeout: Duration = Duration.ZERO,
    val ifdsApMode: ApMode = ApMode.Tree,
    val debugOptions: DebugOptions? = null,
    val sarifGenerationOptions: SarifGenerationOptions = SarifGenerationOptions(),
    val cwe: List<Int> = emptyList(),
    val useSymbolicExecution: Boolean = false,
    val symbolicExecutionTimeout: Duration = Duration.ZERO,
    val storeSummaries: Boolean = false,
    val experimentalAAInterProcCallDepth: Int = 1,
    val iterationLimit: Int? = null,
) {
    val summariesApMode get() = ifdsApMode.takeIf { storeSummaries }

    fun taintAnalyzerOptions() = TaintAnalyzerOptions(
        ifdsTimeout = ifdsAnalysisTimeout,
        ifdsApMode = ifdsApMode,
        symbolicExecutionEnabled = useSymbolicExecution,
        analysisCwe = cwe.takeIf { it.isNotEmpty() }?.toSet(),
        storeSummaries = storeSummaries,
        experimentalAAInterProcCallDepth = experimentalAAInterProcCallDepth,
        debugOptions = debugOptions,
        tracePathLimit = sarifGenerationOptions.sarifCodeFlowLimit,
        iterationLimit = iterationLimit,
    )
}
