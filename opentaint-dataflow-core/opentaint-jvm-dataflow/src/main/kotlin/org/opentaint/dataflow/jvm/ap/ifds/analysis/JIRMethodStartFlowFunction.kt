package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction.StartFact
import org.opentaint.dataflow.jvm.ap.ifds.CalleePositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.JIRInstanceTypeMethodContext
import org.opentaint.dataflow.jvm.ap.ifds.jirDowncast
import org.opentaint.dataflow.jvm.ap.ifds.taint.CalleePositionToAccessPath
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.util.onSome

class JIRMethodStartFlowFunction(
    private val apManager: ApManager,
    private val context: JIRMethodAnalysisContext,
) : MethodStartFlowFunction {
    override fun propagateZero(): List<StartFact> {
        val result = mutableListOf<StartFact>()
        result.add(StartFact.Zero)

        applySinkRules()

        JIRMethodCallFlowFunction.applyEntryPointConfigDefault(
            apManager,
            context.taint.taintConfig as TaintRulesProvider,
            context.methodEntryPoint.method as JIRMethod,
            context.factTypeChecker
        ).onSome { facts ->
            facts.mapTo(result) { StartFact.Fact(it) }
        }

        return result
    }

    override fun propagateFact(fact: FinalFactAp): List<StartFact.Fact> {
        val checkedFact = checkInitialFactTypes(context.methodEntryPoint, fact) ?: return emptyList()
        return listOf(StartFact.Fact(checkedFact))
    }

    private fun checkInitialFactTypes(methodEntryPoint: MethodEntryPoint, factAp: FinalFactAp): FinalFactAp? {
        if (factAp.base !is AccessPathBase.This) return factAp

        val thisClass = when (val context = methodEntryPoint.context) {
            EmptyMethodContext -> {
                val method = methodEntryPoint.method
                jirDowncast<JIRMethod>(method)
                method.enclosingClass
            }
            is JIRInstanceTypeMethodContext -> context.type
            else -> error("Unexpected value for context: $context")
        }

        val thisType = thisClass.toType()
        return context.factTypeChecker.filterFactByLocalType(thisType, factAp)
    }

    private fun applySinkRules() {
        val config = context.taint.taintConfig as TaintRulesProvider
        val method = context.methodEntryPoint.method
        val statement = context.methodEntryPoint.statement

        val sinkRules = config.sinkRulesForMethodEntry(method).toList()
        if (sinkRules.isEmpty()) return

        val apResolver = CalleePositionToAccessPath(resultAp = null)
        val valueResolver = CalleePositionToJIRValueResolver(method as JIRMethod)
        val conditionEvaluator = JIRFactAwareConditionEvaluator(
            emptyList(), apResolver, valueResolver, context.factTypeChecker,
        )

        for (rule in sinkRules) {
            if (!conditionEvaluator.evalWithAssumptionsCheck(rule.condition)) {
                continue
            }

            context.taint.taintSinkTracker.addUnconditionalVulnerability(
                context.methodEntryPoint, statement, rule
            )
        }
    }
}
