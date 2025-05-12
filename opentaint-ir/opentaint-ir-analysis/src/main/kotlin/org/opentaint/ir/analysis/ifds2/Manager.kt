package org.opentaint.ir.analysis.ifds2

import kotlinx.coroutines.CoroutineScope
import org.opentaint.ir.api.JIRMethod

interface Manager<out Fact, in Event> {
    fun handleEvent(event: Event)

    suspend fun handleControlEvent(event: ControlEvent)

    fun subscribeOnSummaryEdges(
        method: JIRMethod,
        scope: CoroutineScope,
        handler: (Edge<Fact>) -> Unit,
    )
}

sealed interface ControlEvent

data class QueueEmptinessChanged(
    val runner: IRunner<*>,
    val isEmpty: Boolean,
) : ControlEvent
