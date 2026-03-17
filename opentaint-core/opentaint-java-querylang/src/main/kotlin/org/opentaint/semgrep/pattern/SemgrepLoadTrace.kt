package org.opentaint.semgrep.pattern

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import mu.KLogging
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

    val logMessagePrefix: String

    fun info(message: String) {
        addEntry(SemgrepInfoEntry(message))
        logger.info { "$logMessagePrefix | $message" }
    }

    fun error(
        step: Step,
        message: SemgrepRuleLoadErrorMessage,
    ) {
        val location = logMessagePrefix
        val enrichedMessage = SemgrepLoadIssueMessages.format(
            category = message.category,
            severity = message.severity,
            message = message.message,
        )

        addEntry(SemgrepErrorEntry(step, enrichedMessage, message.category, message.severity))

        when {
            message.category == SemgrepErrorEntry.Category.INTERNAL_WARNING -> logger.warn { "$location | $enrichedMessage" }
            message.severity == SemgrepErrorEntry.Severity.BLOCKING -> logger.error { "$location | $enrichedMessage" }
            else -> logger.warn { "$location | $enrichedMessage" }
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
    override val logMessagePrefix: String
        get() = path.substringAfterLast('/')

    override fun addEntry(entry: SemgrepTraceEntry) {
        entries += entry
    }

    fun ruleTrace(ruleId: String, ruleIdInFile: String): SemgrepRuleLoadTrace =
        SemgrepRuleLoadTrace(ruleId, ruleIdInFile, logMessagePrefix).also { ruleTraces.add(it) }

    fun compressed(): SemgrepFileLoadTrace {
        val compressedRuleTraces = ruleTraces.mapTo(mutableListOf()) { it.compressed() }
        return copy(ruleTraces = compressedRuleTraces)
    }
}

@Serializable
data class SemgrepRuleLoadTrace(
    val ruleId: String,
    val ruleIdInFile: String,
    @Transient val parentLogMessagePrefix: String = "",
    val steps: MutableList<SemgrepRuleLoadStepTrace> = mutableListOf(),
    val entries: MutableList<SemgrepTraceEntry> = mutableListOf(),
) : SemgrepTraceLogger {
    override val logMessagePrefix: String
        get() = "$parentLogMessagePrefix/$ruleIdInFile"

    override fun addEntry(entry: SemgrepTraceEntry) {
        entries += entry
    }

    fun stepTrace(step: Step): SemgrepRuleLoadStepTrace =
        SemgrepRuleLoadStepTrace(step, logMessagePrefix).also { steps.add(it) }

    fun compressed(): SemgrepRuleLoadTrace {
        val compressedSteps = steps.mapNotNullTo(mutableListOf()) { it.compressed() }
        return copy(steps = compressedSteps)
    }
}

@Serializable
data class SemgrepRuleLoadStepTrace(
    val step: Step,
    @Transient val parentLogMessagePrefix: String = "",
    val entries: MutableList<SemgrepTraceEntry> = mutableListOf(),
) : SemgrepTraceLogger {
    override val logMessagePrefix: String
        get() = "$parentLogMessagePrefix/${step.name.lowercase().replace('_', '-')}"

    override fun addEntry(entry: SemgrepTraceEntry) {
        entries += entry
    }

    fun error(message: SemgrepRuleLoadErrorMessage) = super.error(step, message)

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
        BUILD_TAINT_AUTOMATA,
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
    val category: Category,
    val severity: Severity,
) : SemgrepTraceEntry() {
    enum class Category {
        RULE_ISSUE,
        UNSUPPORTED_FEATURE,
        INTERNAL_WARNING,
    }

    enum class Severity {
        BLOCKING,
        NON_BLOCKING,
    }
}
