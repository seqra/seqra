package org.opentaint.dataflow.bidi.driver

import org.opentaint.dataflow.bidi.engine.RunConfig
import org.opentaint.dataflow.bidi.engine.RunMethodAnalyzer
import org.opentaint.dataflow.bidi.engine.RunResult
import org.opentaint.dataflow.bidi.engine.RunStatus
import java.util.Collections
import kotlin.time.Duration

interface IterationPolicy {
    fun fieldLimit(runIndex: Int): Int

    /** Called only after a complete forward run and the two fixed stop checks. */
    fun continueAfter(run: RunConfig, result: RunResult<RunMethodAnalyzer>, frontiers: List<Frontier>): Boolean

    fun timeout(run: RunConfig, remaining: Duration): Duration = remaining
}

class FixedLimits(limits: List<Int>) : IterationPolicy {
    private val limits: List<Int> = Collections.unmodifiableList(ArrayList(limits))

    override fun fieldLimit(runIndex: Int): Int = limits[runIndex - 1]

    override fun continueAfter(run: RunConfig, result: RunResult<RunMethodAnalyzer>, frontiers: List<Frontier>): Boolean =
        run.index + 2 <= limits.size
}

sealed interface Next {
    class Run(val config: RunConfig) : Next
    class End(val status: RunStatus, val reason: EndReason = EndReason.ABNORMAL) : Next
}
