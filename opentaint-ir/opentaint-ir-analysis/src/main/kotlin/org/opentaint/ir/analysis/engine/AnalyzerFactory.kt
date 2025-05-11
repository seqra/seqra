package org.opentaint.ir.analysis.engine

import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRInst

/**
 * Interface for flow functions -- mappings of kind DomainFact -> Collection of DomainFacts
 */
fun interface FlowFunctionInstance {
    fun compute(fact: DomainFact): Collection<DomainFact>
}

/**
 * An interface with which facts appearing in analyses should be marked
 */
interface DomainFact

/**
 * A special [DomainFact] that always holds
 */
object ZEROFact : DomainFact {
    override fun toString(): String = "[ZERO fact]"
}

/**
 * Implementations of the interface should provide all four kinds of flow functions mentioned in RHS95,
 * thus fully describing how the facts are propagated through the supergraph.
 */
interface FlowFunctionsSpace {
    /**
     * @return facts that may hold when analysis is started from [startStatement]
     * (these are needed to initiate worklist in ifds analysis)
     */
    fun obtainPossibleStartFacts(startStatement: JIRInst): Collection<DomainFact>

    fun obtainSequentFlowFunction(
        current: JIRInst,
        next: JIRInst,
    ): FlowFunctionInstance

    fun obtainCallToStartFlowFunction(
        callStatement: JIRInst,
        callee: JIRMethod,
    ): FlowFunctionInstance

    fun obtainCallToReturnFlowFunction(
        callStatement: JIRInst,
        returnSite: JIRInst,
        graph: JIRApplicationGraph,
    ): FlowFunctionInstance

    fun obtainExitToReturnSiteFlowFunction(
        callStatement: JIRInst,
        returnSite: JIRInst,
        exitStatement: JIRInst,
    ): FlowFunctionInstance
}

/**
 * [Analyzer] interface describes how facts are propagated and how [AnalysisDependentEvent]s are
 * produced by these facts during the run of tabulation algorithm by [BaseIfdsUnitRunner].
 *
 * Note that methods and properties of this interface may be accessed concurrently from different
 * threads, so the implementations should be thread-safe.
 *
 * @property flowFunctions a [FlowFunctionsSpace] instance that describes how facts are generated
 * and propagated during run of tabulation algorithm.
 */
interface Analyzer {
    val flowFunctions: FlowFunctionsSpace

    fun isSkipped(method: JIRMethod): Boolean = false

    /**
     * This method is called by [BaseIfdsUnitRunner] each time a new path edge is found.
     *
     * @return [AnalysisDependentEvent]s that are produced by this edge.
     * Usually these are [NewSummaryFact] events with [SummaryEdgeFact] or [VulnerabilityLocation] facts
     */
    fun handleNewEdge(edge: IfdsEdge): List<AnalysisDependentEvent>

    /**
     * This method is called by [BaseIfdsUnitRunner] each time a new cross-unit called is observed.
     *
     * @return [AnalysisDependentEvent]s that are produced by this [fact].
     */
    fun handleNewCrossUnitCall(fact: CrossUnitCallFact): List<AnalysisDependentEvent>

    /**
     * This method is called once by [BaseIfdsUnitRunner] when the propagation of facts is finished
     * (normally or due to cancellation).
     *
     * @return [AnalysisDependentEvent]s that should be processed after the facts propagation was completed
     * (usually these are some [NewSummaryFact]s).
     */
    fun handleIfdsResult(ifdsResult: IfdsResult): List<AnalysisDependentEvent>
}

/**
 * A functional interface that allows to produce [Analyzer] by [JIRApplicationGraph].
 *
 * It simplifies instantiation of [IfdsUnitRunnerFactory]s because this way you don't have to pass graph and reversed
 * graph to analyzers' constructors directly, relying on runner to do it by itself.
 */
fun interface AnalyzerFactory {
    fun newAnalyzer(graph: JIRApplicationGraph): Analyzer
}
