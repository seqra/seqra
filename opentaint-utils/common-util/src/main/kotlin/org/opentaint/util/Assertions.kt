package org.opentaint.util

@PublishedApi
internal object Assertions {
    @JvmField
    @PublishedApi
    internal val ENABLED: Boolean = javaClass.desiredAssertionStatus()
}

/**
 * Assert that evaluates [condition] only if assertions are enabled (e.g. during tests)
 * */
inline fun assert(condition: () -> Boolean, message: () -> String) {
    if (Assertions.ENABLED) {
        assert(condition()) { message() }
    }
}
