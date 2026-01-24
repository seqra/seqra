package org.opentaint.semgrep.pattern

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mu.KLogging
import org.opentaint.semgrep.pattern.SemgrepErrorEntry.Reason
import org.opentaint.semgrep.pattern.SemgrepTraceEntry.Step

@Serializable
data class SemgrepLoadTrace(
    val fileTraces: MutableList<SemgrepFileLoadTrace> = mutableListOf(),
) {
    fun fileTrace(path: String): SemgrepFileLoadTrace =
        SemgrepFileLoadTrace(path).also { fileTraces.add(it) }

    fun compressed(): SemgrepLoadTrace {
        val compressedFileTraces = fileTraces.mapTo(mutableListOf()) { it.compressed() }
        return SemgrepLoadTrace(compressedFileTraces)
    }
}

sealed interface SemgrepTraceLogger {
    fun addEntry(entry: SemgrepTraceEntry)

    fun info(message: String) {
        addEntry(SemgrepInfoEntry(message))
        logger.info { message }
    }

    fun error(step: Step, message: String, reason: Reason) {
        addEntry(SemgrepErrorEntry(step, message, reason))

        when (reason) {
            Reason.WARNING -> logger.warn { message }
            Reason.ERROR,
            Reason.NOT_IMPLEMENTED -> logger.error { message }
        }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}

@Serializable
data class SemgrepFileLoadTrace(
    val path: String,
    val ruleTraces: MutableList<SemgrepRuleLoadTrace> = mutableListOf(),
    val entries: MutableList<SemgrepTraceEntry> = mutableListOf(),
) : SemgrepTraceLogger {
    override fun addEntry(entry: SemgrepTraceEntry) {
        entries += entry
    }

    fun ruleTrace(ruleId: String, ruleIdInFile: String): SemgrepRuleLoadTrace =
        SemgrepRuleLoadTrace(ruleId, ruleIdInFile).also { ruleTraces.add(it) }

    fun compressed(): SemgrepFileLoadTrace {
        val compressedRuleTraces = ruleTraces.mapTo(mutableListOf()) { it.compressed() }
        return copy(ruleTraces = compressedRuleTraces)
    }
}

@Serializable
data class SemgrepRuleLoadTrace(
    val ruleId: String,
    val ruleIdInFile: String,
    val steps: MutableList<SemgrepRuleLoadStepTrace> = mutableListOf(),
    val entries: MutableList<SemgrepTraceEntry> = mutableListOf(),
) : SemgrepTraceLogger {
    override fun addEntry(entry: SemgrepTraceEntry) {
        entries += entry
    }

    fun stepTrace(step: Step): SemgrepRuleLoadStepTrace =
        SemgrepRuleLoadStepTrace(step).also { steps.add(it) }

    fun compressed(): SemgrepRuleLoadTrace {
        val compressedSteps = steps.mapNotNullTo(mutableListOf()) { it.compressed() }
        return copy(steps = compressedSteps)
    }
}

@Serializable
data class SemgrepRuleLoadStepTrace(
    val step: Step,
    val entries: MutableList<SemgrepTraceEntry> = mutableListOf(),
) : SemgrepTraceLogger {
    override fun addEntry(entry: SemgrepTraceEntry) {
        entries += entry
    }

    fun error(message: String, reason: Reason) = super.error(step, message, reason)

    fun compressed(): SemgrepRuleLoadStepTrace? = this.takeIf { entries.isNotEmpty() }
}

@Serializable
@Polymorphic
sealed class SemgrepTraceEntry {
    enum class Step {
        LOAD_RULESET,
        BUILD,
        BUILD_CONVERT_TO_RAW_RULE,
        BUILD_PARSE_SEMGREP_RULE,
        BUILD_META_VAR_RESOLVING,
        BUILD_ACTION_LIST_CONVERSION,
        BUILD_TRANSFORM_TO_AUTOMATA,
        AUTOMATA_TO_TAINT_RULE,
    }
}

@Serializable
@SerialName("Info")
data class SemgrepInfoEntry(
    val message: String,
) : SemgrepTraceEntry()

@Serializable
@SerialName("Error")
data class SemgrepErrorEntry(
    val step: Step,
    val message: String,
    val reason: Reason,
) : SemgrepTraceEntry() {
    enum class Reason {
        ERROR, WARNING, NOT_IMPLEMENTED
    }
}
