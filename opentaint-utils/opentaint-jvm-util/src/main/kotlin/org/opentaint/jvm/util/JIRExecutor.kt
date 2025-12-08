package org.opentaint.jvm.util

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

private class JIRThreadFactory(private val customClassLoader: ClassLoader?) : ThreadFactory {

    companion object {
        private var threadIdx = 0
        private fun threadName() = "$threadPrefix${threadIdx++}"
    }

    private var currentThread: Thread? = null

    override fun newThread(runnable: Runnable): Thread {
        check(currentThread == null)
        val thread = Thread(runnable, threadName())

        if (customClassLoader != null) {
            thread.contextClassLoader = customClassLoader
            thread.isDaemon = true
        }

        currentThread = thread
        return thread
    }

    fun getCurrentThread(): Thread? {
        return currentThread
    }
}

private const val threadPrefix = "ExecutorThread-"

val Thread.isExecutorThread: Boolean get() = name.startsWith(threadPrefix)

open class JIRExecutor(customClassLoader: ClassLoader? = null) {
    private val threadFactory = JIRThreadFactory(customClassLoader)

    private val executor = Executors.newSingleThreadExecutor(threadFactory)

    private var lastTask: Future<*>? = null

    private val alreadyInExecutor: Boolean
        get() = Thread.currentThread() === threadFactory.getCurrentThread()

    private val taskIsRunning: Boolean
        get() = lastTask != null && lastTask?.isDone == false

    private fun executeInternal(timeout: Duration?, task: Runnable) {
        if (alreadyInExecutor) {
            check(taskIsRunning)
            task.run()
            return
        }

        check(!taskIsRunning)
        val future = executor.submit(task)
        lastTask = future
        if (timeout != null)
            future.get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        else future.get()
    }

    fun execute(timeout: Duration, task: Runnable) {
        executeInternal(timeout, task)
    }

    fun execute(task: Runnable) {
        executeInternal(null, task)
    }

    private fun unfoldException(e: Throwable): Throwable {
        return when {
            e is ExecutionException && e.cause != null -> unfoldException(e.cause!!)
            e is InvocationTargetException -> e.targetException
            else -> e
        }
    }

    fun executeWithResult(timeout: Duration, body: () -> Any?): Pair<Any?, Throwable?> {
        var result: Any? = null
        var exception: Throwable? = null
        executeInternal(timeout) {
            try {
                result = body()
            } catch (e: Throwable) {
                exception = unfoldException(e)
            }
        }

        return result to exception
    }

    fun executeWithResult(body: () -> Any?): Pair<Any?, Throwable?> {
        var result: Any? = null
        var exception: Throwable? = null
        executeInternal(null) {
            try {
                result = body()
            } catch (e: Throwable) {
                exception = unfoldException(e)
            }
        }

        return result to exception
    }
}
