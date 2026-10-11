package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.MethodKey

/** The unit runner is the sole caller of an actor during a run. */
interface RunActor {
    val key: MethodKey
    val steps: Long
    val hasZeroWork: Boolean
    val demandLayerEdges: Long
    val recordCrossings: Long

    /** True only after all local work and pending publications are complete. */
    fun work(quantum: Int): Boolean

    /** Called at a complete barrier, after every runner has joined. */
    fun freeze()
}

/** Later steps supply the method and summary handlers through this required factory. */
fun interface RunnerHandlerFactory<A : RunActor> {
    fun create(port: RunnerPort<A>): RunnerHandler<A>
}

interface RunnerHandler<A : RunActor> {
    fun createActor(key: MethodKey): A
    /** Work is handled by the owning runner. Other events use their protocol handler. */
    fun handle(event: RunEvent)
}
