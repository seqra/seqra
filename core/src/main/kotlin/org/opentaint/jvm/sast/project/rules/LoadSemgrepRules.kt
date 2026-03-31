package org.opentaint.jvm.sast.project.rules

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KLogging
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta.Severity
import org.opentaint.jvm.sast.project.ProjectAnalysisOptions
import org.opentaint.semgrep.pattern.SemgrepLoadTrace
import org.opentaint.semgrep.pattern.SemgrepRuleLoader
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

private val logger = object : KLogging() {}.logger

fun ProjectAnalysisOptions.loadSemgrepRules(): SemgrepRuleLoader.RuleLoadResult {
    val trace = SemgrepLoadTrace()
    val semgrepRules = parseSemgrepRules(semgrepRuleSet, semgrepSeverity, semgrepRuleId, trace)

    val compressedTrace by lazy { trace.compressed() }
    semgrepRuleLoadTrace?.let { traceFile ->
        runCatching {
            val prettyJson = Json {
                prettyPrint = true
            }
            traceFile.outputStream().bufferedWriter().use { writer ->
                writer.write(prettyJson.encodeToString(compressedTrace))
            }
            logger.info { "Wrote semgrep load trace to $traceFile" }
        }.onFailure { ex ->
            logger.error(ex) { "Failed to write semgrep load trace to $traceFile: ${ex.message}" }
        }
    }

    return semgrepRules
}

private fun parseSemgrepRules(
    semgrepRulesPath: List<Path>,
    semgrepSeverity: List<Severity>,
    semgrepRuleId: List<String>,
    semgrepTrace: SemgrepLoadTrace
): SemgrepRuleLoader.RuleLoadResult {
    val loader = SemgrepRuleLoader()

    val ruleExtensions = arrayOf("yaml", "yml")
    for (rulesRoot in semgrepRulesPath) {
        rulesRoot.walk().filter { it.extension in ruleExtensions }.forEach { rulePath ->
            val relativePath = rulePath.relativeTo(rulesRoot)
            loader.registerRuleSet(rulePath.readText(), relativePath, rulesRoot, semgrepTrace)
        }
    }

    val loadedRules = loader.loadRules(semgrepSeverity, semgrepRuleId)

    logger.info { "Total loaded ${loadedRules.rulesWithMeta.sumOf { it.first.size }} rules" }

    return loadedRules
}
