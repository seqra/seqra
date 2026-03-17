package org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

interface ActionListBuilder {
    fun createActionList(
        pattern: SemgrepJavaPattern,
        semgrepTrace: SemgrepRuleLoadStepTrace,
    ): SemgrepPatternActionList?

    fun cached() = CachedActionListBuilder(this)

    companion object {
        fun create(): ActionListBuilder = PatternToActionListConverter()
    }
}

class CachedActionListBuilder(
    private val builder: ActionListBuilder
) : ActionListBuilder {
    private val cache = ConcurrentHashMap<SemgrepJavaPattern, Optional<SemgrepPatternActionList>>()

    override fun createActionList(
        pattern: SemgrepJavaPattern,
        semgrepTrace: SemgrepRuleLoadStepTrace,
    ): SemgrepPatternActionList? =
        cache.computeIfAbsent(pattern) {
            Optional.ofNullable(builder.createActionList(pattern, semgrepTrace))
        }.getOrNull()
}
