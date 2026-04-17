package org.opentaint.dataflow.jvm.ap.ifds.taint

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ExternalMethodTracker {
    private val seen = ConcurrentHashMap.newKeySet<String>()
    private val records = ConcurrentHashMap<String, ExternalMethodAggregation>()

    fun trackExternalMethod(
        method: String,
        signature: String,
        factPosition: String,
        rulesApplied: Boolean,
    ) {
        val dedupKey = "$method|$signature|$factPosition"
        if (!seen.add(dedupKey)) return

        val methodKey = "$method|$signature"
        records.computeIfAbsent(methodKey) {
            ExternalMethodAggregation(method, signature)
        }.apply {
            addFactPosition(factPosition)
            if (rulesApplied) markPassRulesApplied()
            incrementCallSites()
        }
    }

    fun getExternalMethods(): SkippedExternalMethods {
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

        return SkippedExternalMethods(
            withoutRules = withoutRules.sortedByDescending { it.callSites },
            withRules = withRules.sortedByDescending { it.callSites },
        )
    }
}

@Serializable
data class ExternalMethodRecord(
    val method: String,
    val signature: String,
    val factPositions: List<String>,
    @Transient val passRulesApplied: Boolean = false,
    val callSites: Int,
)

data class SkippedExternalMethods(
    val withoutRules: List<ExternalMethodRecord>,
    val withRules: List<ExternalMethodRecord>,
)

private class ExternalMethodAggregation(
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
        factPositions = factPositions.sorted().distinct(),
        passRulesApplied = passRulesApplied.get(),
        callSites = callSiteCount.get(),
    )
}
