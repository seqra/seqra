package org.opentaint.ir.analysis.ifds

import kotlinx.coroutines.CoroutineScope
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRMethod

interface Manager<out Fact, in Event, out Method, out Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    fun handleEvent(event: Event)

    fun handleControlEvent(event: ControlEvent)

    fun subscribeOnSummaryEdges(
        method: @UnsafeVariance Method,
        scope: CoroutineScope,
        handler: (Edge<Fact, Method, Statement>) -> Unit,
    )
}

sealed interface ControlEvent

data class QueueEmptinessChanged(
    val runner: Runner<*, *, *>,
    val isEmpty: Boolean,
) : ControlEvent
