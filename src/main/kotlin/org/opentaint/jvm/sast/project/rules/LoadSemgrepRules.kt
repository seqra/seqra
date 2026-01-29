package org.opentaint.jvm.sast.project.rules

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KLogging
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta.Severity
import org.opentaint.jvm.sast.project.ProjectAnalysisOptions
import org.opentaint.org.opentaint.semgrep.pattern.convertToOldErrorsFormat
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
    val semgrepRules = parseSemgrepRules(semgrepRuleSet, semgrepMinSeverity, trace)

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

    // todo: remove after opentaint-cli update
    semgrepRuleLoadErrors?.let { traceFile ->
        runCatching {
            val oldErrorsFormat = compressedTrace.convertToOldErrorsFormat()
            val prettyJson = Json {
                prettyPrint = true
            }
            traceFile.outputStream().bufferedWriter().use { writer ->
                writer.write(prettyJson.encodeToString(oldErrorsFormat))
            }
            logger.info { "Wrote semgrep load errors to $traceFile" }
        }.onFailure { ex ->
            logger.error(ex) { "Failed to write semgrep load errors to $traceFile: ${ex.message}" }
        }
    }

    return semgrepRules
}

private fun parseSemgrepRules(
    semgrepRulesPath: List<Path>,
    semgrepMinSeverity: Severity,
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

    val loadedRules = loader.loadRules(semgrepMinSeverity)

    logger.info { "Total loaded ${loadedRules.rulesWithMeta.sumOf { it.first.size }} rules" }

    return loadedRules
}
