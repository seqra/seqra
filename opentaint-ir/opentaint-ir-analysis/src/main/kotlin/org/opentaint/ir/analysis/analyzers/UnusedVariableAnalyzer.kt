package org.opentaint.ir.analysis.analyzers

import org.opentaint.ir.analysis.AnalysisResult
import org.opentaint.ir.analysis.VulnerabilityInstance
import org.opentaint.ir.analysis.engine.Analyzer
import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.FlowFunctionInstance
import org.opentaint.ir.analysis.engine.FlowFunctionsSpace
import org.opentaint.ir.analysis.engine.IFDSResult
import org.opentaint.ir.analysis.engine.IFDSVertex
import org.opentaint.ir.analysis.engine.SpaceId
import org.opentaint.ir.analysis.engine.TaintRealisationsGraph
import org.opentaint.ir.analysis.engine.ZEROFact
import org.opentaint.ir.analysis.paths.AccessPath
import org.opentaint.ir.analysis.paths.toPath
import org.opentaint.ir.analysis.paths.toPathOrNull
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRArgument
import org.opentaint.ir.api.cfg.JIRArrayAccess
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRBranchingInst
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.cfg.JIRLocal
import org.opentaint.ir.api.cfg.JIRSpecialCallExpr
import org.opentaint.ir.api.cfg.JIRStaticCallExpr
import org.opentaint.ir.api.cfg.JIRTerminatingInst
import org.opentaint.ir.api.cfg.values
import org.opentaint.ir.api.ext.cfg.callExpr

class UnusedVariableAnalyzer(
    val graph: JIRApplicationGraph
) : Analyzer {
    override val flowFunctions: FlowFunctionsSpace = UnusedVariableForwardFunctions(graph.classpath)
    override val name: String = value

    override val backward: Analyzer
        get() = error("No backward analysis for Unused variable")

    companion object : SpaceId {
        override val value: String = "unused variable analysis"
    }

    private fun AccessPath.isUsedAt(expr: JIRExpr): Boolean {
        return this in expr.values.map { it.toPathOrNull() }
    }

    private fun AccessPath.isUsedAt(inst: JIRInst, withResult: IFDSResult): Boolean {
        val callExpr = inst.callExpr

        if (callExpr != null) {
            // Don't count constructor calls as usages
            if (callExpr.method.method.isConstructor && isUsedAt((callExpr as JIRSpecialCallExpr).instance)) {
                return false
            }

            if (graph.callees(inst).none() || inst in withResult.crossUnitCallees.keys.map { it.statement }) {
                return isUsedAt(callExpr)
            }

            if (callExpr is JIRInstanceCallExpr) {
                return isUsedAt(callExpr.instance)
            }
            return false
        }
        if (inst is JIRAssignInst) {
            if (inst.lhv is JIRArrayAccess && isUsedAt((inst.lhv as JIRArrayAccess))) {
                return true
            }
            return isUsedAt(inst.rhv) && (inst.lhv !is JIRLocal || inst.rhv !is JIRLocal)
        }
        if (inst is JIRTerminatingInst || inst is JIRBranchingInst) {
            return inst.operands.any { isUsedAt(it) }
        }
        return false
    }

    override fun calculateSources(ifdsResult: IFDSResult): AnalysisResult {
        val used: MutableMap<JIRInst, Boolean> = mutableMapOf()
        ifdsResult.resultFacts.forEach { (inst, facts) ->
            facts.filterIsInstance<UnusedVariableNode>().forEach { fact ->
                if (fact.initStatement !in used) {
                    used[fact.initStatement] = false
                }

                if (fact.variable.isUsedAt(inst, ifdsResult)) {
                    used[fact.initStatement] = true
                }
            }
        }
        val vulnerabilities = used.filterValues { !it }.keys.map {
            VulnerabilityInstance(value, TaintRealisationsGraph(IFDSVertex(it, ZEROFact), setOf(IFDSVertex(it, ZEROFact)), emptyMap()))
        }
        return AnalysisResult(vulnerabilities)
    }
}

private class UnusedVariableForwardFunctions(
    val classpath: JIRClasspath
) : FlowFunctionsSpace {

    override val inIds: List<SpaceId> get() = listOf(UnusedVariableAnalyzer, ZEROFact.id)

    override fun obtainStartFacts(startStatement: JIRInst): Collection<DomainFact> {
        return listOf(ZEROFact)
    }

    override fun obtainSequentFlowFunction(current: JIRInst, next: JIRInst) = object : FlowFunctionInstance {
        override val inIds = this@UnusedVariableForwardFunctions.inIds

        override fun compute(fact: DomainFact): Collection<DomainFact> {
            if (current !is JIRAssignInst) {
                return listOf(fact)
            }

            if (fact == ZEROFact) {
                val toPath = current.lhv.toPathOrNull() ?: return listOf(ZEROFact)
                return if (!toPath.isOnHeap) {
                    listOf(ZEROFact, UnusedVariableNode(toPath, current))
                } else {
                    listOf(ZEROFact)
                }
            }

            if (fact !is UnusedVariableNode) {
                return emptyList()
            }

            val default = if (fact.variable == current.lhv.toPathOrNull()) emptyList() else listOf(fact)
            val fromPath = current.rhv.toPathOrNull() ?: return default
            val toPath = current.lhv.toPathOrNull() ?: return default

            if (fromPath.isOnHeap || toPath.isOnHeap) {
                return default
            }

            if (fromPath == fact.variable) {
                return default.plus(UnusedVariableNode(toPath, fact.initStatement))
            }
            return default
        }

    }

    override fun obtainCallToStartFlowFunction(callStatement: JIRInst, callee: JIRMethod) = object : FlowFunctionInstance {
        override val inIds = this@UnusedVariableForwardFunctions.inIds

        override fun compute(fact: DomainFact): Collection<DomainFact> {
            val callExpr = callStatement.callExpr ?: error("Call expr is expected to be not-null")
            val formalParams = callee.parameters.map {
                JIRArgument.of(it.index, it.name, classpath.findTypeOrNull(it.type.typeName)!!)
            }

            if (fact == ZEROFact) {
                // We don't show unused parameters for virtual calls
                if (callExpr !is JIRStaticCallExpr && callExpr !is JIRSpecialCallExpr) {
                    return listOf(ZEROFact)
                }
                return formalParams.map { UnusedVariableNode(it.toPath(), callStatement) }.plus(ZEROFact)
            }

            if (fact !is UnusedVariableNode) {
                return emptyList()
            }

            return formalParams.zip(callExpr.args)
                .filter { (_, actual) -> actual.toPathOrNull() == fact.variable }
                .map { UnusedVariableNode(it.first.toPath(), fact.initStatement) }
        }

    }

    override fun obtainCallToReturnFlowFunction(callStatement: JIRInst, returnSite: JIRInst) =
        obtainSequentFlowFunction(callStatement, returnSite)

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JIRInst,
        returnSite: JIRInst,
        exitStatement: JIRInst
    ) = object : FlowFunctionInstance {
        override val inIds = this@UnusedVariableForwardFunctions.inIds

        override fun compute(fact: DomainFact): Collection<DomainFact> {
            return if (fact == ZEROFact) listOf(ZEROFact) else emptyList()
        }
    }
}