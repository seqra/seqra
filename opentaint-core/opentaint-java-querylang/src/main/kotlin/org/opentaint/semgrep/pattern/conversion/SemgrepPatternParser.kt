package org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.PatternParsingAstFailed
import org.opentaint.semgrep.pattern.PatternParsingFailure
import org.opentaint.semgrep.pattern.PatternParsingFailureWithElement
import org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.semgrep.pattern.SemgrepJavaPatternParser
import org.opentaint.semgrep.pattern.SemgrepJavaPatternParsingResult
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

interface SemgrepPatternParser {
    fun parseOrNull(
        pattern: String,
        semgrepTrace: SemgrepRuleLoadStepTrace,
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
        semgrepTrace: SemgrepRuleLoadStepTrace,
    ): SemgrepJavaPattern? {
        return when (val result = parser.parseSemgrepJavaPattern(pattern)) {
            is SemgrepJavaPatternParsingResult.FailedASTParsing -> {
                semgrepTrace.error(PatternParsingAstFailed(result.errorMessages))
                null
            }

            is SemgrepJavaPatternParsingResult.Ok -> {
                result.pattern
            }

            is SemgrepJavaPatternParsingResult.ParserFailure -> {
                semgrepTrace.error(
                    PatternParsingFailureWithElement(
                        result.exception.message,
                        result.exception.element.text,
                    )
                )
                null
            }

            is SemgrepJavaPatternParsingResult.OtherFailure -> {
                semgrepTrace.error(PatternParsingFailure(result.exception.message))
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
        semgrepTrace: SemgrepRuleLoadStepTrace,
    ): SemgrepJavaPattern? =
        cache.computeIfAbsent(pattern) {
            Optional.ofNullable(parser.parseOrNull(pattern, semgrepTrace))
        }.getOrNull()
}
