package org.opentaint.dataflow.configuration.selection

/** A transient compiler-call boundary. It stores no provider, rule, IR or fact.
 * A delegated unknown wrapper must not certify its hidden base as complete. */
object RuleSelectionBoundary {
    private val completeScope = ThreadLocal<Boolean>()

    val isComplete: Boolean get() = completeScope.get() != false

    fun <T> withScope(complete: Boolean, body: () -> T): T {
        val previous = completeScope.get()
        completeScope.set(previous != false && complete)
        return try {
            body()
        } finally {
            if (previous == null) completeScope.remove() else completeScope.set(previous)
        }
    }
}
