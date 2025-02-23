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
    override fun toString() = "[ZERO fact]"
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
    fun obtainSequentFlowFunction(current: JIRInst, next: JIRInst): FlowFunctionInstance
    fun obtainCallToStartFlowFunction(callStatement: JIRInst, callee: JIRMethod): FlowFunctionInstance
    fun obtainCallToReturnFlowFunction(callStatement: JIRInst, returnSite: JIRInst): FlowFunctionInstance
    fun obtainExitToReturnSiteFlowFunction(callStatement: JIRInst, returnSite: JIRInst, exitStatement: JIRInst): FlowFunctionInstance
}

/**
 * [Analyzer] interface describes how facts are propagated and how vulnerabilities are produced by these facts during
 * the run of tabulation algorithm by [IfdsBaseUnitRunner].
 *
 * There are two methods that can turn facts into vulnerabilities or other [SummaryFact]s: [getSummaryFacts] and
 * [getSummaryFactsPostIfds]. First is called during the analysis, each time a new path edge is found, and second
 * is called only after all path edges were found.
 * While some analyses really need full set of facts to find vulnerabilities, most analyses can report [SummaryFact]s
 * right after some fact is reached, so [getSummaryFacts] is a recommended way to report vulnerabilities when possible.
 *
 * Note that methods and properties of this interface may be accessed concurrently from different threads,
 * so the implementations should be thread-safe.
 *
 * @property flowFunctions a [FlowFunctionsSpace] instance that describes how facts are generated and propagated
 * during run of tabulation algorithm.
 *
 * @property saveSummaryEdgesAndCrossUnitCalls when true, summary edges and cross-unit calls will be automatically
 * saved to summary (usually this property is true for forward analyzers and false for backward analyzers).
 */
interface Analyzer {
    val flowFunctions: FlowFunctionsSpace

    val saveSummaryEdgesAndCrossUnitCalls: Boolean
        get() = true

    /**
     * This method is called by [IfdsBaseUnitRunner] each time a new path edge is found.
     *
     * @return [SummaryFact]s that are produced by this edge, that need to be saved to summary.
     */
    fun getSummaryFacts(edge: IfdsEdge): List<SummaryFact> = emptyList()

    /**
     * This method is called once by [IfdsBaseUnitRunner] when the propagation of facts is finished
     * (normally or due to cancellation).
     *
     * @return [SummaryFact]s that can be obtained after the facts propagation was completed.
     */
    fun getSummaryFactsPostIfds(ifdsResult: IfdsResult): List<SummaryFact> = emptyList()
}

/**
 * A functional interface that allows to produce [Analyzer] by [JIRApplicationGraph].
 *
 * It simplifies instantiation of [IfdsUnitRunner]s because this way you don't have to pass graph and reversed
 * graph to [Analyzer]s directly, relying on runner to do it by itself.
 */
fun interface AnalyzerFactory {
    fun newAnalyzer(graph: JIRApplicationGraph): Analyzer
}