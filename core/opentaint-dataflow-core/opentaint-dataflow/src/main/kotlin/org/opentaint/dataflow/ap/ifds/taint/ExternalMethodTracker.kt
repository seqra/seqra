package org.opentaint.dataflow.ap.ifds.taint

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks external (unresolved) method calls encountered during taint analysis.
 * Collects method signatures, fact positions where dataflow was killed, and whether
 * pass-through rules were actually applied.
 *
 * Thread-safe: uses ConcurrentHashMap for deduplication and aggregation.
 * Pattern follows [TaintSinkTracker].
 */
class ExternalMethodTracker {

    // Dedup key: method+signature+factPosition
    private val seen = ConcurrentHashMap.newKeySet<String>()

    // Per-method aggregation: method+signature → aggregation
    private val records = ConcurrentHashMap<String, ExternalMethodAggregation>()

    /**
     * Report an external method call with a specific fact position.
     *
     * @param method fully qualified method name, e.g. "com.example.Foo#bar"
     * @param signature JVM-style signature, e.g. "(Ljava/lang/String;)V"
     * @param factPosition human-readable fact position: "this", "arg(0)", "arg(1)", "result"
     * @param passRulesApplied true if pass-through rules were actually applied (applyPassThrough returned Some)
     */
    fun report(
        method: String,
        signature: String,
        factPosition: String,
        passRulesApplied: Boolean,
    ) {
        val dedupKey = "$method|$signature|$factPosition"
        if (!seen.add(dedupKey)) return

        val methodKey = "$method|$signature"
        records.computeIfAbsent(methodKey) {
            ExternalMethodAggregation(method, signature)
        }.apply {
            addFactPosition(factPosition)
            if (passRulesApplied) markPassRulesApplied()
            incrementCallSites()
        }
    }

    /**
     * Get the aggregated results, split into methods without and with pass-through rules applied.
     * Both lists are sorted by call site count (descending) for agent prioritization.
     */
    fun getResults(): ExternalMethodResults {
        val withoutRules = mutableListOf<ExternalMethodRecord>()
        val withRules = mutableListOf<ExternalMethodRecord>()

        for (agg in records.values) {
            val record = agg.toRecord()
            if (record.passRulesApplied) {
                withRules.add(record)
            } else {
                withoutRules.add(record)
            }
        }

        return ExternalMethodResults(
            withoutRules = withoutRules.sortedByDescending { it.callSites },
            withRules = withRules.sortedByDescending { it.callSites },
        )
    }
}

/**
 * A single external method record in the output.
 */
data class ExternalMethodRecord(
    val method: String,
    val signature: String,
    val factPositions: Set<String>,
    val passRulesApplied: Boolean,
    val callSites: Int,
)

/**
 * Aggregated results from the external method tracker.
 * [withoutRules] — methods where no pass-through rules fired (dataflow fact killed).
 * [withRules] — methods where pass-through rules were applied.
 */
data class ExternalMethodResults(
    val withoutRules: List<ExternalMethodRecord>,
    val withRules: List<ExternalMethodRecord>,
)

/**
 * Internal aggregation state for a single method+signature pair.
 * Thread-safe via atomic operations.
 */
internal class ExternalMethodAggregation(
    private val method: String,
    private val signature: String,
) {
    private val factPositions = ConcurrentHashMap.newKeySet<String>()
    private val passRulesApplied = AtomicBoolean(false)
    private val callSiteCount = AtomicInteger(0)

    fun addFactPosition(position: String) {
        factPositions.add(position)
    }

    fun markPassRulesApplied() {
        passRulesApplied.set(true)
    }

    fun incrementCallSites() {
        callSiteCount.incrementAndGet()
    }

    fun toRecord() = ExternalMethodRecord(
        method = method,
        signature = signature,
        factPositions = factPositions.toSet(),
        passRulesApplied = passRulesApplied.get(),
        callSites = callSiteCount.get(),
    )
}
