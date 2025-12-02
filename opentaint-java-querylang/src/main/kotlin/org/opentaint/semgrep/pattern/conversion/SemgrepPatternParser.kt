package org.opentaint.semgrep.pattern.conversion

import mu.KotlinLogging
import org.slf4j.event.Level
import org.opentaint.semgrep.pattern.AbstractSemgrepError
import org.opentaint.semgrep.pattern.SemgrepError
import org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.semgrep.pattern.SemgrepJavaPatternParsingResult
import org.opentaint.semgrep.pattern.SemgrepJavaPatternParser
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

interface SemgrepPatternParser {
    fun parseOrNull(
        pattern: String,
        semgrepError: AbstractSemgrepError,
        semgrepStep: SemgrepError.Step,
    ): SemgrepJavaPattern?

    fun cached() = CachedSemgrepPatternParser(this)

    companion object {
        fun create(): SemgrepPatternParser = DefaultSemgrepPatternParser()
    }
}

class DefaultSemgrepPatternParser(
    private val parser: SemgrepJavaPatternParser = SemgrepJavaPatternParser()
) : SemgrepPatternParser {
    override fun parseOrNull(
        pattern: String,
        semgrepError: AbstractSemgrepError,
        semgrepStep: SemgrepError.Step,
    ): SemgrepJavaPattern? {
        return when (val result = parser.parseSemgrepJavaPattern(pattern)) {
            is SemgrepJavaPatternParsingResult.FailedASTParsing -> {
                semgrepError += SemgrepError(
                    semgrepStep,
                    "Pattern parsing AST failed with errors:\n${result.errorMessages.joinToString("\n")}",
                    Level.ERROR,
                    SemgrepError.Reason.ERROR,
                )
                null
            }

            is SemgrepJavaPatternParsingResult.Ok -> {
                result.pattern
            }

            is SemgrepJavaPatternParsingResult.ParserFailure -> {
                semgrepError += SemgrepError(
                    semgrepStep,
                    "Pattern parsing failed: ${result.exception.message}, ${result.exception.element.text}",
                    Level.ERROR,
                    SemgrepError.Reason.ERROR,
                )
                null
            }

            is SemgrepJavaPatternParsingResult.OtherFailure -> {
                semgrepError += SemgrepError(
                    semgrepStep,
                    "Pattern parsing failed: ${result.exception.message}",
                    Level.ERROR,
                    SemgrepError.Reason.ERROR,
                )
                null
            }
        }
    }
}

class CachedSemgrepPatternParser(
    private val parser: SemgrepPatternParser,
) : SemgrepPatternParser {
    private val cache = ConcurrentHashMap<String, Optional<SemgrepJavaPattern>>()

    override fun parseOrNull(
        pattern: String,
        semgrepError: AbstractSemgrepError,
        semgrepStep: SemgrepError.Step,
    ): SemgrepJavaPattern? =
        cache.computeIfAbsent(pattern) {
            Optional.ofNullable(parser.parseOrNull(pattern, semgrepError, semgrepStep))
        }.getOrNull()
}

private val logger = KotlinLogging.logger {}
