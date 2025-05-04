package org.opentaint.ir.analysis.library.analyzers

import org.opentaint.ir.analysis.engine.AbstractAnalyzer
import org.opentaint.ir.analysis.engine.AnalysisDependentEvent
import org.opentaint.ir.analysis.engine.AnalyzerFactory
import org.opentaint.ir.analysis.engine.CrossUnitCallFact
import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.FlowFunctionInstance
import org.opentaint.ir.analysis.engine.FlowFunctionsSpace
import org.opentaint.ir.analysis.engine.IfdsResult
import org.opentaint.ir.analysis.engine.IfdsVertex
import org.opentaint.ir.analysis.engine.NewSummaryFact
import org.opentaint.ir.analysis.engine.VulnerabilityLocation
import org.opentaint.ir.analysis.engine.ZEROFact
import org.opentaint.ir.analysis.paths.AccessPath
import org.opentaint.ir.analysis.paths.toPath
import org.opentaint.ir.analysis.paths.toPathOrNull
import org.opentaint.ir.analysis.sarif.SarifMessage
import org.opentaint.ir.analysis.sarif.VulnerabilityDescription
import org.opentaint.ir.api.core.analysis.ApplicationGraph
import org.opentaint.ir.api.jvm.JIRProject
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRBranchingInst
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstLocation
import org.opentaint.ir.api.jvm.cfg.JIRLocal
import org.opentaint.ir.api.jvm.cfg.JIRSpecialCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRStaticCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRTerminatingInst
import org.opentaint.ir.api.jvm.cfg.values
import org.opentaint.ir.api.jvm.ext.cfg.callExpr

class JIRUnusedVariableAnalyzer(
    val graph: JIRApplicationGraph
) : AbstractAnalyzer<JIRMethod, JIRInstLocation, JIRInst>(graph) {
    override val flowFunctions: FlowFunctionsSpace<JIRInst, JIRMethod> = JIRUnusedVariableForwardFunctions(graph.classpath)

    override val isMainAnalyzer: Boolean
        get() = true

    companion object {
        const val ruleId: String = "unused-variable"
        private val vulnerabilityMessage = SarifMessage("Assigned value is unused")

        val vulnerabilityDescription = VulnerabilityDescription(vulnerabilityMessage, ruleId)
    }

    private fun AccessPath.isUsedAt(expr: JIRExpr): Boolean {
        return this in expr.values.map { it.toPathOrNull() }
    }

    private fun AccessPath.isUsedAt(inst: JIRInst): Boolean {
        val callExpr = inst.callExpr

        if (callExpr != null) {
            // Don't count constructor calls as usages
            if (callExpr.method.method.isConstructor && isUsedAt((callExpr as JIRSpecialCallExpr).instance)) {
                return false
            }

            return isUsedAt(callExpr)
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

    override fun handleNewCrossUnitCall(
        fact: CrossUnitCallFact<JIRMethod, JIRInstLocation, JIRInst>
    ): List<AnalysisDependentEvent> {
        return emptyList()
    }

    override fun handleIfdsResult(
        ifdsResult: IfdsResult<JIRMethod, JIRInstLocation, JIRInst>
    ): List<AnalysisDependentEvent> = buildList {
        val used: MutableMap<JIRInst, Boolean> = mutableMapOf()
        ifdsResult.resultFacts.forEach { (inst, facts) ->
            facts.filterIsInstance<UnusedVariableNode>().forEach { fact ->
                if (fact.initStatement !in used) {
                    used[fact.initStatement] = false
                }

                if (fact.variable.isUsedAt(inst)) {
                    used[fact.initStatement] = true
                }
            }
        }
        used.filterValues { !it }.keys.map {
            add(
                NewSummaryFact(VulnerabilityLocation(vulnerabilityDescription, IfdsVertex(it, ZEROFact)))
            )
        }
    }
}

val JIRUnusedVariableAnalyzerFactory = AnalyzerFactory { graph ->
    JIRUnusedVariableAnalyzer(graph as JIRApplicationGraph)
}

private class JIRUnusedVariableForwardFunctions(
    val classpath: JIRProject
) : FlowFunctionsSpace<JIRInst, JIRMethod> {

    override fun obtainPossibleStartFacts(startStatement: JIRInst): Collection<DomainFact> {
        return listOf(ZEROFact)
    }

    override fun obtainSequentFlowFunction(current: JIRInst, next: JIRInst) = FlowFunctionInstance { fact ->
        if (current !is JIRAssignInst) {
            return@FlowFunctionInstance listOf(fact)
        }

        if (fact == ZEROFact) {
            val toPath = current.lhv.toPathOrNull() ?: return@FlowFunctionInstance listOf(ZEROFact)
            return@FlowFunctionInstance if (!toPath.isOnHeap) {
                listOf(ZEROFact, UnusedVariableNode(toPath, current))
            } else {
                listOf(ZEROFact)
            }
        }

        if (fact !is UnusedVariableNode) {
            return@FlowFunctionInstance emptyList()
        }

        val default = if (fact.variable == current.lhv.toPathOrNull()) emptyList() else listOf(fact)
        val fromPath = current.rhv.toPathOrNull() ?: return@FlowFunctionInstance default
        val toPath = current.lhv.toPathOrNull() ?: return@FlowFunctionInstance default

        if (fromPath.isOnHeap || toPath.isOnHeap) {
            return@FlowFunctionInstance default
        }

        if (fromPath == fact.variable) {
            return@FlowFunctionInstance default.plus(UnusedVariableNode(toPath, fact.initStatement))
        }
        default
    }

    override fun obtainCallToStartFlowFunction(callStatement: JIRInst, callee: JIRMethod) = FlowFunctionInstance { fact ->
        val callExpr = callStatement.callExpr ?: error("Call expr is expected to be not-null")
        val formalParams = classpath.getFormalParamsOf(callee)

        if (fact == ZEROFact) {
            // We don't show unused parameters for virtual calls
            if (callExpr !is JIRStaticCallExpr && callExpr !is JIRSpecialCallExpr) {
                return@FlowFunctionInstance listOf(ZEROFact)
            }
            return@FlowFunctionInstance formalParams.map { UnusedVariableNode(it.toPath(), callStatement) }.plus(ZEROFact)
        }

        if (fact !is UnusedVariableNode) {
            return@FlowFunctionInstance emptyList()
        }

        emptyList()
    }

    override fun obtainCallToReturnFlowFunction(callStatement: JIRInst, returnSite: JIRInst) =
        obtainSequentFlowFunction(callStatement, returnSite)

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JIRInst,
        returnSite: JIRInst,
        exitStatement: JIRInst
    ) = FlowFunctionInstance { fact ->
        if (fact == ZEROFact) {
            listOf(ZEROFact)
        } else {
            emptyList()
        }
    }
}