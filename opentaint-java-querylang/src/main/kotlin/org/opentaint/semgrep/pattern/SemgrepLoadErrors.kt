package org.opentaint.org.opentaint.semgrep.pattern

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opentaint.semgrep.pattern.SemgrepErrorEntry
import org.opentaint.semgrep.pattern.SemgrepFileLoadTrace
import org.opentaint.semgrep.pattern.SemgrepInfoEntry
import org.opentaint.semgrep.pattern.SemgrepLoadTrace
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace
import org.opentaint.semgrep.pattern.SemgrepRuleLoadTrace
import org.opentaint.semgrep.pattern.SemgrepTraceEntry
import org.slf4j.event.Level

fun SemgrepLoadTrace.convertToOldErrorsFormat(): List<SemgrepFileErrors> =
    fileTraces.map { it.convert() }

private fun SemgrepFileLoadTrace.convert(): SemgrepFileErrors {
    val errors = entries.mapNotNullTo(mutableListOf()) { it.errorOrNull() }
    ruleTraces.mapTo(errors) { it.convert() }
    return SemgrepFileErrors(path, errors)
}

private fun SemgrepRuleLoadTrace.convert(): SemgrepRuleErrors {
    val errors = entries.mapNotNullTo(mutableListOf()) { it.errorOrNull() }
    steps.mapTo(errors) { it.convert() }
    return SemgrepRuleErrors(ruleId, ruleIdInFile, errors)
}

private fun SemgrepRuleLoadStepTrace.convert(): SemgrepError {
    val errorEntries = entries.mapNotNullTo(mutableListOf()) { it.errorOrNull() }
    return SemgrepError(step, "Step errors", Level.WARN, SemgrepErrorEntry.Reason.WARNING, errorEntries)
}

private fun SemgrepTraceEntry.errorOrNull(): AbstractSemgrepError? = when (this) {
    is SemgrepInfoEntry -> null
    is SemgrepErrorEntry -> {
        val level = when (reason) {
            SemgrepErrorEntry.Reason.WARNING -> Level.WARN
            SemgrepErrorEntry.Reason.ERROR,
            SemgrepErrorEntry.Reason.NOT_IMPLEMENTED,
                -> Level.ERROR
        }
        SemgrepError(step, message, level, reason)
    }
}

@Serializable
@Polymorphic
sealed class AbstractSemgrepError {
    abstract val errors: MutableList<AbstractSemgrepError>

    operator fun plusAssign(semgrepError: AbstractSemgrepError) {
        errors.add(semgrepError)
    }
}

@Serializable
@SerialName("SemgrepError")
private data class SemgrepError(
    val step: SemgrepTraceEntry.Step,
    val message: String,
    val level: Level,
    val reason: SemgrepErrorEntry.Reason,
    override var errors: MutableList<AbstractSemgrepError> = arrayListOf(),
) : AbstractSemgrepError()

@Serializable
@SerialName("SemgrepRule")
private data class SemgrepRuleErrors(
    val ruleId: String,
    val ruleIdInFile: String,
    override val errors: MutableList<AbstractSemgrepError> = arrayListOf(),
) : AbstractSemgrepError()

@Serializable
@SerialName("SemgrepFile")
data class SemgrepFileErrors(
    val path: String,
    override val errors: MutableList<AbstractSemgrepError> = arrayListOf(),
) : AbstractSemgrepError()
