package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.FlowFunction
import org.opentaint.ir.analysis.ifds.FlowFunctions
import org.opentaint.ir.analysis.ifds.toPath
import org.opentaint.ir.analysis.ifds.toPathOrNull
import org.opentaint.ir.analysis.util.getArgumentsOf
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRSpecialCallExpr
import org.opentaint.ir.api.cfg.JIRStaticCallExpr
import org.opentaint.ir.api.ext.cfg.callExpr

class UnusedVariableFlowFunctions(
    private val graph: JIRApplicationGraph,
) : FlowFunctions<UnusedVariableDomainFact> {
    private val cp: JIRClasspath
        get() = graph.classpath

    override fun obtainPossibleStartFacts(
        method: JIRMethod,
    ): Collection<UnusedVariableDomainFact> {
        return setOf(UnusedVariableZeroFact)
    }

    override fun obtainSequentFlowFunction(
        current: JIRInst,
        next: JIRInst,
    ) = FlowFunction<UnusedVariableDomainFact> { fact ->
        if (current !is JIRAssignInst) {
            return@FlowFunction setOf(fact)
        }

        if (fact == UnusedVariableZeroFact) {
            val toPath = current.lhv.toPath()
            if (!toPath.isOnHeap) {
                return@FlowFunction setOf(UnusedVariableZeroFact, UnusedVariable(toPath, current))
            } else {
                return@FlowFunction setOf(UnusedVariableZeroFact)
            }
        }
        check(fact is UnusedVariable)

        val toPath = current.lhv.toPath()
        val default = if (toPath == fact.variable) emptySet() else setOf(fact)
        val fromPath = current.rhv.toPathOrNull()
            ?: return@FlowFunction default

        if (fromPath.isOnHeap || toPath.isOnHeap) {
            return@FlowFunction default
        }

        if (fromPath == fact.variable) {
            return@FlowFunction default + fact.copy(variable = toPath)
        }

        default
    }

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: JIRInst,
        returnSite: JIRInst,
    ) = obtainSequentFlowFunction(callStatement, returnSite)

    override fun obtainCallToStartFlowFunction(
        callStatement: JIRInst,
        calleeStart: JIRInst,
    ) = FlowFunction<UnusedVariableDomainFact> { fact ->
        val callExpr = callStatement.callExpr
            ?: error("Call statement should have non-null callExpr")

        if (fact == UnusedVariableZeroFact) {
            if (callExpr !is JIRStaticCallExpr && callExpr !is JIRSpecialCallExpr) {
                return@FlowFunction setOf(UnusedVariableZeroFact)
            }
            return@FlowFunction buildSet {
                add(UnusedVariableZeroFact)
                val callee = calleeStart.location.method
                val formalParams = cp.getArgumentsOf(callee)
                for (formal in formalParams) {
                    add(UnusedVariable(formal.toPath(), callStatement))
                }
            }
        }
        check(fact is UnusedVariable)

        emptySet()
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JIRInst,
        returnSite: JIRInst,
        exitStatement: JIRInst,
    ) = FlowFunction<UnusedVariableDomainFact> { fact ->
        if (fact == UnusedVariableZeroFact) {
            setOf(UnusedVariableZeroFact)
        } else {
            emptySet()
        }
    }
}
