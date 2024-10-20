package org.opentaint.ir.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// block may be called few times
// like lazy(NONE)
class SuspendableLazy<T>(private val block: suspend () -> T) {

    private var result: T? = null

    suspend operator fun invoke() = result ?: block().also {
        result = it
    }
}

fun <T> suspendableLazy(block: suspend () -> T) = SuspendableLazy(block)

object BackgroundScope : CoroutineScope {
    override val coroutineContext = Dispatchers.IO + SupervisorJob()
}

private val lock = ReentrantLock()

fun <T> persistentOperation(action: () -> T): T = lock.withLock {
    action()
}
