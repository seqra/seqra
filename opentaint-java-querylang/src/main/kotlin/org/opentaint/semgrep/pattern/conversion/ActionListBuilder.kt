package org.opentaint.org.opentaint.semgrep.pattern.conversion

import org.opentaint.org.opentaint.semgrep.pattern.SemgrepJavaPattern
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

interface ActionListBuilder {
    fun createActionList(pattern: SemgrepJavaPattern): SemgrepPatternActionList?

    fun cached() = CachedActionListBuilder(this)

    companion object {
        fun create(): ActionListBuilder = PatternToActionListConverter()
    }
}

class CachedActionListBuilder(
    private val builder: ActionListBuilder
) : ActionListBuilder {
    private val cache = ConcurrentHashMap<SemgrepJavaPattern, Optional<SemgrepPatternActionList>>()

    override fun createActionList(pattern: SemgrepJavaPattern): SemgrepPatternActionList? =
        cache.computeIfAbsent(pattern) {
            Optional.ofNullable(builder.createActionList(pattern))
        }.getOrNull()
}
