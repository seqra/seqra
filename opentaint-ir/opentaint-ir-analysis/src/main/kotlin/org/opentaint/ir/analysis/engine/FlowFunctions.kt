package org.opentaint.ir.analysis.engine

import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRInst

fun interface FlowFunctionInstance {
    fun compute(fact: DomainFact): Collection<DomainFact>
}

interface DomainFact

object ZEROFact : DomainFact {
    override fun toString() = "[ZERO fact]"
}

interface FlowFunctionsSpace {
    fun obtainPossibleStartFacts(startStatement: JIRInst): Collection<DomainFact>
    fun obtainSequentFlowFunction(current: JIRInst, next: JIRInst): FlowFunctionInstance
    fun obtainCallToStartFlowFunction(callStatement: JIRInst, callee: JIRMethod): FlowFunctionInstance
    fun obtainCallToReturnFlowFunction(callStatement: JIRInst, returnSite: JIRInst): FlowFunctionInstance
    fun obtainExitToReturnSiteFlowFunction(callStatement: JIRInst, returnSite: JIRInst, exitStatement: JIRInst): FlowFunctionInstance
}

interface Analyzer {
    val flowFunctions: FlowFunctionsSpace

    val saveSummaryEdgesAndCrossUnitCalls: Boolean
        get() = true

    fun getSummaryFacts(edge: IfdsEdge): List<SummaryFact> = emptyList()

    fun getSummaryFactsPostIfds(ifdsResult: IfdsResult): List<SummaryFact> = emptyList()
}

fun interface AnalyzerFactory {
    fun newAnalyzer(graph: JIRApplicationGraph): Analyzer
}