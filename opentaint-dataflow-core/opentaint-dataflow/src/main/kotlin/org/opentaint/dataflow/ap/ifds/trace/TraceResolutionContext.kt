package org.opentaint.dataflow.ap.ifds.trace

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker.TaintVulnerability
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class TraceResolutionContext(
    dispatcher: CoroutineDispatcher,
    private val vulnerabilities: List<TaintVulnerability>
) {
    fun resolvedTraces(): List<VulnerabilityWithTrace> {
        val vulnerabilityWithTraces = mutableListOf<VulnerabilityWithTrace>()
        vulnerabilityWithTraces.addAll(result)

        vulnerabilities.mapNotNullTo(vulnerabilityWithTraces) { vulnerability ->
            if (vulnerability in processedVulnerabilities) return@mapNotNullTo null

            VulnerabilityWithTrace(vulnerability, trace = null)
        }

        return vulnerabilityWithTraces
    }

    val processed: Int
        get() = processedCounter.get()

    private val completed = CompletableDeferred<Unit>()
    private val processedCounter = AtomicInteger()
    private val result = ConcurrentLinkedQueue<VulnerabilityWithTrace>()
    private val processedVulnerabilities = ConcurrentHashMap.newKeySet<TaintVulnerability>()
    private val jobs = mutableListOf<Job>()
    private val scope = CoroutineScope(dispatcher)

    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        logger.error(exception) { "Trace resolution failed" }
        updatedProcessed()
    }

    fun resolveAll(
        body: (TaintVulnerability) -> VulnerabilityWithTrace
    ): CompletableDeferred<Unit> {
        vulnerabilities.mapTo(jobs) { vulnerability ->
            scope.launch(exceptionHandler) {
                result.add(body(vulnerability))
                processedVulnerabilities.add(vulnerability)
                updatedProcessed()
            }
        }
        return completed
    }

    suspend fun join() {
        jobs.joinAll()
    }

    private fun updatedProcessed() {
        if (processedCounter.incrementAndGet() == vulnerabilities.size) {
            completed.complete(Unit)
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
