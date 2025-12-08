package org.opentaint.semgrep.pattern

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mu.KLogging
import org.slf4j.event.Level

@Serializable
@Polymorphic
sealed class AbstractSemgrepError {
    abstract val errors: MutableList<AbstractSemgrepError>

    protected open fun log() {}

    operator fun plusAssign(semgrepError: AbstractSemgrepError) {
        errors.add(semgrepError)
        semgrepError.log()
    }
}

@Serializable
@SerialName("SemgrepError")
data class SemgrepError(
    val step: Step,
    val message: String,
    val level: Level,
    val reason: Reason,
    override var errors: MutableList<AbstractSemgrepError> = arrayListOf()
) : AbstractSemgrepError() {
    override fun log() {
        logger.atLevel(level).log(message)
    }

    enum class Reason {
        ERROR, WARNING, NOT_IMPLEMENTED
    }

    enum class Step {
        LOAD_RULESET,
        BUILD_CONVERT_TO_RAW_RULE,
        BUILD_PARSE_SEMGREP_RULE,
        BUILD_META_VAR_RESOLVING,
        BUILD_ACTION_LIST_CONVERSION,
        BUILD_TRANSFORM_TO_AUTOMATA,
        AUTOMATA_TO_TAINT_RULE,
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}

@Serializable
@SerialName("SemgrepRule")
data class SemgrepRuleErrors(
    val ruleId: String,
    val ruleIdInFile: String,
    override val errors: MutableList<AbstractSemgrepError> = arrayListOf()
) : AbstractSemgrepError() {
    constructor(
        ruleIdInFile: String,
        errors: MutableList<AbstractSemgrepError> = arrayListOf(),
        ruleSetName: String
    ) : this(SemgrepRuleUtils.getRuleId(ruleSetName, ruleIdInFile), ruleSetName, errors)

    fun handlePhase(
        failureCount: Int,
        semgrepError: SemgrepError,
    ) {
        if (failureCount > 0 || semgrepError.errors.isNotEmpty() == true) {
            val newMessage = semgrepError.message + if (failureCount > 0) ": $failureCount times" else ""
            val uniqueErrors = semgrepError.errors.distinct() as MutableList<AbstractSemgrepError>

            val semgrepError = SemgrepError(
                semgrepError.step,
                newMessage,
                Level.WARN,
                SemgrepError.Reason.WARNING,
                uniqueErrors
            )
            this += semgrepError
        }
    }
}

@Serializable
@SerialName("SemgrepFile")
data class SemgrepFileErrors(
    val path: String,
    override val errors: MutableList<AbstractSemgrepError> = arrayListOf(),
) : AbstractSemgrepError()
