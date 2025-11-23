package org.opentaint.org.opentaint.semgrep.pattern.conversion

import mu.KotlinLogging
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepJavaPatternParsingResult
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepJavaPatternParser
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

interface SemgrepPatternParser {
    fun parseOrNull(pattern: String): SemgrepJavaPattern?

    fun cached() = CachedSemgrepPatternParser(this)

    companion object {
        fun create(): SemgrepPatternParser = DefaultSemgrepPatternParser()
    }
}

class DefaultSemgrepPatternParser(
    private val parser: SemgrepJavaPatternParser = SemgrepJavaPatternParser()
) : SemgrepPatternParser {
    override fun parseOrNull(pattern: String): SemgrepJavaPattern? {
        return when (val result = parser.parseSemgrepJavaPattern(pattern)) {
            is SemgrepJavaPatternParsingResult.FailedASTParsing -> {
                logger.error { "Pattern parsing failed with errors:\n${result.errorMessages.joinToString("\n")}" }
                null
            }

            is SemgrepJavaPatternParsingResult.Ok -> {
                result.pattern
            }

            is SemgrepJavaPatternParsingResult.ParserFailure -> {
                logger.error { "Pattern parsing failed: ${result.exception.message}, ${result.exception.element.text}" }
                null
            }

            is SemgrepJavaPatternParsingResult.OtherFailure -> {
                logger.error { "Pattern parsing failed: ${result.exception.message}" }
                null
            }
        }
    }
}

class CachedSemgrepPatternParser(
    private val parser: SemgrepPatternParser,
) : SemgrepPatternParser {
    private val cache = ConcurrentHashMap<String, Optional<SemgrepJavaPattern>>()

    override fun parseOrNull(pattern: String): SemgrepJavaPattern? =
        cache.computeIfAbsent(pattern) {
            Optional.ofNullable(parser.parseOrNull(pattern))
        }.getOrNull()
}

private val logger = KotlinLogging.logger {}
