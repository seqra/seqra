package org.opentaint.ir.analysis.engine

import org.opentaint.ir.analysis.DumpableAnalysisResult
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.cfg.JIRInst

interface FlowFunctionInstance {
    val inIds: List<SpaceId>
    fun compute(fact: DomainFact): Collection<DomainFact>
}

interface SpaceId {
    val value: String
}

interface DomainFact {
    val id: SpaceId
}

object ZEROFact : DomainFact {
    override val id = object : SpaceId {
        override val value: String = "ZERO fact id"
    }

    override fun toString() = "[ZERO fact]"
}

interface FlowFunctionsSpace {
    val inIds: List<SpaceId>
    fun obtainStartFacts(startStatement: JIRInst): Collection<DomainFact>
    fun obtainSequentFlowFunction(current: JIRInst, next: JIRInst): FlowFunctionInstance
    fun obtainCallToStartFlowFunction(callStatement: JIRInst, callee: JIRMethod): FlowFunctionInstance
    fun obtainCallToReturnFlowFunction(callStatement: JIRInst, returnSite: JIRInst): FlowFunctionInstance
    fun obtainExitToReturnSiteFlowFunction(callStatement: JIRInst, returnSite: JIRInst, exitStatement: JIRInst): FlowFunctionInstance

    val backward: FlowFunctionsSpace
}

interface Analyzer {
    val backward: Analyzer
    val flowFunctions: FlowFunctionsSpace
    fun calculateSources(ifdsResult: IFDSResult): DumpableAnalysisResult
}