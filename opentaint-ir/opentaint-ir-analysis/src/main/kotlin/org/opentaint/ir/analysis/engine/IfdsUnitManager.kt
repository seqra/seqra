package org.opentaint.ir.analysis.engine

import kotlinx.coroutines.flow.FlowCollector
import org.opentaint.ir.api.core.CoreMethod
import org.opentaint.ir.api.core.cfg.CoreInst
import org.opentaint.ir.api.core.cfg.CoreInstLocation

/**
 * Implementations of this interface manage one or more runners and should be responsible for:
 * - communication between different runners, i.e. they
 * should submit received [EdgeForOtherRunnerQuery] to proper runners via [IfdsUnitRunner.submitNewEdge] call
 * - providing runners with summaries for other units
 * - saving the [NewSummaryFact]s produced by runners
 * - managing lifecycles of the launched runners
 */
interface IfdsUnitManager<UnitType, Method, Location, Statement>
        where Method : CoreMethod<Statement>,
              Location : CoreInstLocation<Method>,
              Statement : CoreInst<Location, Method, *> {
    suspend fun handleEvent(
        event: IfdsUnitRunnerEvent,
        runner: IfdsUnitRunner<UnitType, Method, Location, Statement>
    )
}

// TODO: provide visitor for this interface
sealed interface IfdsUnitRunnerEvent

data class QueueEmptinessChanged(val isEmpty: Boolean) : IfdsUnitRunnerEvent

/**
 * @property method the method for which summary edges the subscription is queried
 * @property collector the [FlowCollector] to which queried summary edges should be sent to,
 * somewhat similar to a callback
 */
data class SubscriptionForSummaryEdges<Method, Location, Statement>(
    val method: Method,
    val collector: FlowCollector<IfdsEdge<Method, Location, Statement>>
) : IfdsUnitRunnerEvent where Method : CoreMethod<Statement>,
                              Location : CoreInstLocation<Method>,
                              Statement : CoreInst<Location, Method, *>

/**
 * A common interface for all events that are allowed to be produced by [Analyzer]
 * (all others may be produced only in runners directly)
 */
sealed interface AnalysisDependentEvent : IfdsUnitRunnerEvent

data class NewSummaryFact<Method>(val fact: SummaryFact<Method>) : AnalysisDependentEvent
data class EdgeForOtherRunnerQuery<Method, Location, Statement>(
    val edge: IfdsEdge<Method, Location, Statement>
) : AnalysisDependentEvent where Method : CoreMethod<Statement>,
                                 Location : CoreInstLocation<Method>,
                                 Statement : CoreInst<Location, Method, *>