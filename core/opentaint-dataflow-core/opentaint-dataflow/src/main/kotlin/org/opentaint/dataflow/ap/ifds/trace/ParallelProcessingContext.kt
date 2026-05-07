package org.opentaint.dataflow.ap.ifds.trace

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import mu.KotlinLogging
import org.opentaint.dataflow.util.Cancellation
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class ParallelProcessingContext<T, R : Any>(
    dispatcher: CoroutineDispatcher,
    private val name: String,
    private val tasks: List<T>,
) {
    sealed interface ProcessingResult<T, R> {
        data class Done<T, R>(val result: R) : ProcessingResult<T, R>
        data class Running<T, R>(val task: T) : ProcessingResult<T, R>
    }

    abstract fun processItem(item: T): ProcessingResult<T, R>

    abstract fun createUnprocessed(item: T): R

    open fun reportStats() {
    }

    val processed: Int
        get() = processedCounter.get()

    private val tasksQueue = Channel<Int>(Channel.UNLIMITED)
    private val workers = mutableListOf<Job>()

    private val completed = CompletableDeferred<Unit>()
    private val processedCounter = AtomicInteger()

    private val latestState: AtomicReferenceArray<T> = AtomicReferenceArray<T>(tasks.size).also {
        for (i in tasks.indices) it.set(i, tasks[i])
    }
    private val results: AtomicReferenceArray<R> = AtomicReferenceArray<R>(tasks.size)
    private val terminated: AtomicIntegerArray = AtomicIntegerArray(tasks.size)

    private val scope = CoroutineScope(dispatcher)

    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        logger.error(exception) { "$name failed" }
    }

    private fun processingResults(): List<R> {
        return List(tasks.size) { i ->
            results.get(i) ?: createUnprocessed(latestState.get(i))
        }
    }

    private fun markTerminal(index: Int) {
        if (terminated.compareAndSet(index, 0, 1)) {
            if (processedCounter.incrementAndGet() == tasks.size) {
                tasksQueue.close()
                completed.complete(Unit)
            }
        }
    }

    private fun processAllWithCompletion(cancellation: Cancellation): CompletableDeferred<Unit> {
        val workerCount = minOf(WORKER_COUNT, tasks.size)
        repeat(workerCount) {
            workers += scope.launch(exceptionHandler) {
                for (index in tasksQueue) {
                    if (!cancellation.isActive()) break

                    val task = latestState.get(index)
                    try {
                        when (val r = processItem(task)) {
                            is ProcessingResult.Done -> {
                                latestState.set(index, null)
                                results.set(index, r.result)
                                markTerminal(index)
                            }

                            is ProcessingResult.Running -> {
                                latestState.set(index, r.task)
                                tasksQueue.send(index)
                            }
                        }
                    } catch (ex: Throwable) {
                        logger.error(ex) { "$name failed" }
                        markTerminal(index)
                    }
                }
            }
        }
        return completed
    }

    fun processAll(
        progressScope: CoroutineScope,
        timeout: Duration,
        cancellationTimeout: Duration,
        cancellation: Cancellation,
    ): List<R> {
        for (i in tasks.indices) {
            tasksQueue.trySendBlocking(i)
        }

        val completion = processAllWithCompletion(cancellation)

        val progress = progressScope.launch {
            while (isActive) {
                delay(10.seconds)
                logger.info { "${name}: processed ${processed}/${tasks.size} items" }
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
                tasksQueue.cancel()

                progress.cancelAndJoin()
                joinCtx()
            }
        }

        return processingResults().also { result ->
            logger.info { "${name}: processed ${result.size}/${tasks.size} items" }
        }
    }

    suspend fun joinCtx() {
        workers.joinAll()
    }

    companion object {
        private const val WORKER_COUNT = 10

        private val logger = KotlinLogging.logger {}
    }
}
