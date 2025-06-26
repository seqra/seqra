package org.opentaint.ir.analysis.ifds

import kotlinx.coroutines.CoroutineScope
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

interface Manager<out Fact, in Event, out Method, out Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    fun handleEvent(event: Event)

    fun handleControlEvent(event: ControlEvent)

    fun subscribeOnSummaryEdges(
        method: @UnsafeVariance Method,
        scope: CoroutineScope,
        handler: (Edge<Fact, Statement>) -> Unit,
    )
}

sealed interface ControlEvent

data class QueueEmptinessChanged(
    val runner: Runner<*, *, *>,
    val isEmpty: Boolean,
) : ControlEvent
