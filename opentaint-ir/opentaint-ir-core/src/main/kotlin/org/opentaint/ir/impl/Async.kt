package org.opentaint.ir.impl

import jetbrains.exodus.kotlin.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

object SQL {
//    override val coroutineContext = newSingleThreadContext("sql-write") + SupervisorJob()

    fun <T> write(action: () -> T): T = synchronized {
        action()
    }
}