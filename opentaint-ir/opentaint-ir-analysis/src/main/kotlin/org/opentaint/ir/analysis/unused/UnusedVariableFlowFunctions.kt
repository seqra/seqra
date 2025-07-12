package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.FlowFunction
import org.opentaint.ir.analysis.ifds.FlowFunctions
import org.opentaint.ir.analysis.ifds.isOnHeap
import org.opentaint.ir.analysis.util.Traits
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.CommonProject
import org.opentaint.ir.api.common.analysis.ApplicationGraph
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRSpecialCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRStaticCallExpr

context(Traits<Method, Statement>)
class UnusedVariableFlowFunctions<Method, Statement>(
    private val graph: ApplicationGraph<Method, Statement>,
) : FlowFunctions<UnusedVariableDomainFact, Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    override fun obtainPossibleStartFacts(
        method: Method,
    ): Collection<UnusedVariableDomainFact> {
        return setOf(UnusedVariableZeroFact)
    }

    override fun obtainSequentFlowFunction(
        current: Statement,
        next: Statement,
    ) = FlowFunction<UnusedVariableDomainFact> { fact ->
        if (current !is CommonAssignInst) {
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
        callStatement: Statement,
        returnSite: Statement,
    ) = obtainSequentFlowFunction(callStatement, returnSite)

    override fun obtainCallToStartFlowFunction(
        callStatement: Statement,
        calleeStart: Statement,
    ) = FlowFunction<UnusedVariableDomainFact> { fact ->
        val callExpr = callStatement.getCallExpr()
            ?: error("Call statement should have non-null callExpr")

        if (fact == UnusedVariableZeroFact) {
            // FIXME: use common?
            if (callExpr !is JIRStaticCallExpr && callExpr !is JIRSpecialCallExpr) {
                return@FlowFunction setOf(UnusedVariableZeroFact)
            }
            return@FlowFunction buildSet {
                add(UnusedVariableZeroFact)
                val callee = graph.methodOf(calleeStart)
                val formalParams = getArgumentsOf(callee)
                for (formal in formalParams) {
                    add(UnusedVariable(formal.toPath(), callStatement))
                }
            }
        }
        check(fact is UnusedVariable)

        emptySet()
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: Statement,
        returnSite: Statement,
        exitStatement: Statement,
    ) = FlowFunction<UnusedVariableDomainFact> { fact ->
        if (fact == UnusedVariableZeroFact) {
            setOf(UnusedVariableZeroFact)
        } else {
            emptySet()
        }
    }
}
