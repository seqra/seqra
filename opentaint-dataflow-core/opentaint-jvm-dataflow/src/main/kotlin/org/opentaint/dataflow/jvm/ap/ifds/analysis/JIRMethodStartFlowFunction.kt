package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction.StartFact
import org.opentaint.dataflow.jvm.ap.ifds.CalleePositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionRewriter
import org.opentaint.dataflow.jvm.ap.ifds.JIRSimpleFactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.applyEntryPointConfig
import org.opentaint.dataflow.jvm.ap.ifds.jIRDowncast
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintSourceActionEvaluator
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.util.onSome

class JIRMethodStartFlowFunction(
    private val apManager: ApManager,
    private val context: JIRMethodAnalysisContext,
) : MethodStartFlowFunction {
    override fun propagateZero(): List<StartFact> {
        val result = mutableListOf<StartFact>()
        result.add(StartFact.Zero)

        applySinkRules().mapTo(result) { StartFact.Fact(it) }

        val method = context.methodEntryPoint.method as JIRMethod
        val valueResolver = CalleePositionToJIRValueResolver(method)
        val conditionRewriter = JIRMarkAwareConditionRewriter(
            valueResolver, context.factTypeChecker
        )

        val conditionEvaluator = JIRSimpleFactAwareConditionEvaluator(conditionRewriter, evaluator = null)

        val sourceEvaluator = TaintSourceActionEvaluator(
            apManager,
            exclusion = ExclusionSet.Universe,
            context.factTypeChecker,
            returnValueType = null
        )

        applyEntryPointConfig(
            context.taint.taintConfig as TaintRulesProvider,
            method, fact = null, conditionEvaluator, sourceEvaluator
        ).onSome { facts ->
            facts.mapTo(result) {
                it.getAllAccessors()
                    .filterIsInstanceTo<TaintMarkAccessor, _>(context.taintMarksAssignedOnMethodEnter)

                StartFact.Fact(it)
            }
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
                jIRDowncast<JIRMethod>(method)
                method.enclosingClass
            }
            is org.opentaint.dataflow.jvm.ap.ifds.JIRInstanceTypeMethodContext -> context.type
            else -> error("Unexpected value for context: $context")
        }

        val thisType = thisClass.toType()
        return context.factTypeChecker.filterFactByLocalType(thisType, factAp)
    }

    private fun applySinkRules(): List<FinalFactAp> {
        val config = context.taint.taintConfig as TaintRulesProvider
        val method = context.methodEntryPoint.method
        val statement = context.methodEntryPoint.statement

        val sinkRules = config.sinkRulesForMethodEntry(method, fact = null).toList()
        if (sinkRules.isEmpty()) return emptyList()

        val valueResolver = CalleePositionToJIRValueResolver(method as JIRMethod)
        val conditionRewriter = JIRMarkAwareConditionRewriter(
            valueResolver,
            context.factTypeChecker
        )

        val conditionEvaluator = JIRSimpleFactAwareConditionEvaluator(conditionRewriter, evaluator = null)

        val sourceEvaluator = TaintSourceActionEvaluator(
            apManager,
            exclusion = ExclusionSet.Universe,
            context.factTypeChecker,
            returnValueType = null
        )

        val factsAfterSink = mutableListOf<FinalFactAp>()
        for (rule in sinkRules) {
            if (!conditionEvaluator.eval(rule.condition)) {
                continue
            }

            if (rule.trackFactsReachAnalysisEnd.isEmpty()) {
                context.taint.taintSinkTracker.addUnconditionalVulnerability(
                    context.methodEntryPoint, statement, rule
                )
                continue
            }

            val requiredEndFacts = hashSetOf<FinalFactAp>()
            rule.trackFactsReachAnalysisEnd.forEach { action ->
                sourceEvaluator.evaluate(rule, action).onSome { facts ->
                    facts.forEach { f ->
                        requiredEndFacts += f
                        factsAfterSink += f
                    }
                }
            }

            context.taint.taintSinkTracker.addUnconditionalVulnerabilityWithEndFactRequirement(
                context.methodEntryPoint, statement, rule, requiredEndFacts
            )
        }

        return factsAfterSink
    }
}
