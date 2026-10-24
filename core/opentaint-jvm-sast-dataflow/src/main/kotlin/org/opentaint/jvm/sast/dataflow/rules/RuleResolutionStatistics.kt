package org.opentaint.jvm.sast.dataflow.rules

import java.util.concurrent.atomic.AtomicLong

/** Optional aggregate work counts. The default resolver has no statistics owner. */
class RuleResolutionStatistics {
    private val calls = AtomicLong()
    private val contexts = AtomicLong()
    private val queries = AtomicLong()
    val binderCalls: Long get() = calls.get()
    val contextVisits: Long get() = contexts.get()
    val cacheQueries: Long get() = queries.get()
    internal fun queried() { queries.incrementAndGet() }
    internal fun entered() { calls.incrementAndGet() }
    internal fun contexts(count: Int) { contexts.addAndGet(count.toLong()) }
}
