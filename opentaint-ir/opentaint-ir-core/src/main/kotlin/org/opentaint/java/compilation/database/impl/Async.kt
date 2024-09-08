package org.opentaint.java.compilation.database.impl

// block may be called few times
// like lazy(NONE)
class SuspendableLazy<T>(private val block: suspend () -> T) {

    private var result: T? = null

    suspend operator fun invoke() = result ?: block().also {
        result = it
    }
}

fun <T> suspendableLazy(block: suspend () -> T) = SuspendableLazy(block)