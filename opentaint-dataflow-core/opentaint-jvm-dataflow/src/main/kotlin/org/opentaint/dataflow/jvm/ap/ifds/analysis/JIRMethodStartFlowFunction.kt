package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction.StartFact
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.JIRInstanceTypeMethodContext
import org.opentaint.dataflow.jvm.ap.ifds.jirDowncast
import org.opentaint.dataflow.jvm.util.JIRTraits
import org.opentaint.util.onSome

class JIRMethodStartFlowFunction(
    private val apManager: ApManager,
    private val context: JIRMethodAnalysisContext,
    private val traits: JIRTraits
) : MethodStartFlowFunction {
    override fun propagateZero(): List<StartFact> {
        val result = mutableListOf<StartFact>()
        result.add(StartFact.Zero)

        JIRMethodCallFlowFunction.applyEntryPointConfigDefault(
            apManager,
            context.taint.taintConfig,
            context.methodEntryPoint.method as JIRMethod,
            traits
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
}
