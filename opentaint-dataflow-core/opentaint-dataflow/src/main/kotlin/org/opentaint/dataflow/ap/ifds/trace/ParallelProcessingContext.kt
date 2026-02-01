package org.opentaint.dataflow.ap.ifds.trace

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class ParallelProcessingContext<T, R : Any>(
    dispatcher: CoroutineDispatcher,
    private val name: String,
    private val data: List<T>,
) {
    abstract fun createUnprocessed(item: T): R

    open fun reportStats() {
    }

    fun processingResults(): List<R> {
        val processingRes = mutableListOf<R>()
        processingRes.addAll(result)

        data.mapNotNullTo(processingRes) {
            if (it in processedItems) return@mapNotNullTo null

            createUnprocessed(it)
        }

        return processingRes
    }

    val processed: Int
        get() = processedCounter.get()

    private val completed = CompletableDeferred<Unit>()
    private val processedCounter = AtomicInteger()
    private val result = ConcurrentLinkedQueue<R>()
    private val processedItems = ConcurrentHashMap.newKeySet<T>()
    private val jobs = mutableListOf<Job>()
    private val scope = CoroutineScope(dispatcher)

    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        logger.error(exception) { "$name failed" }
        updatedProcessed()
    }

    fun processAllWithCompletion(
        body: (T) -> R
    ): CompletableDeferred<Unit> {
        data.mapTo(jobs) { vulnerability ->
            scope.launch(exceptionHandler) {
                try {
                    result.add(body(vulnerability))
                    processedItems.add(vulnerability)
                } catch (ex: Throwable) {
                    logger.error(ex) { "$name failed" }
                } finally {
                    updatedProcessed()
                }
            }
        }
        return completed
    }

    fun processAll(
        progressScope: CoroutineScope,
        timeout: Duration,
        cancellationTimeout: Duration,
        cancellation: ProcessingCancellation,
        body: (T) -> R
    ): List<R> {
        val completion = processAllWithCompletion(body)

        val progress = progressScope.launch {
            while (isActive) {
                delay(10.seconds)
                logger.info { "${name}: processed ${processed}/${data.size} items" }
                reportStats()
            }
        }

        runBlocking {
            val traceResolutionStatus = withTimeoutOrNull(timeout) { completion.await() }
            if (traceResolutionStatus == null) {
                logger.warn { "${name}: processing timeout" }
            }

            withTimeoutOrNull(cancellationTimeout) {
                cancellation.cancel()

                progress.cancelAndJoin()
                joinCtx()
            }
        }

        return processingResults().also { result ->
            logger.info { "${name}: processed ${result.size}/${data.size} items" }
        }
    }

    suspend fun joinCtx() {
        jobs.joinAll()
    }

    private fun updatedProcessed() {
        if (processedCounter.incrementAndGet() == data.size) {
            completed.complete(Unit)
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
