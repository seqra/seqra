package org.opentaint.dataflow.go.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.configuration.CommonTaintAssignAction
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSource
import org.opentaint.dataflow.configuration.isTrue
import org.opentaint.dataflow.go.GoFlowFunctionUtils
import org.opentaint.dataflow.go.GoFlowFunctionUtils.resolvePosAccess
import org.opentaint.dataflow.go.rules.ActionPosition
import org.opentaint.dataflow.go.rules.GoAssignAction
import org.opentaint.dataflow.go.rules.TaintRule
import org.opentaint.dataflow.taint.PositionAccess
import org.opentaint.dataflow.taint.SourceActionEvaluator
import org.opentaint.ir.go.inst.GoIRAssignInst
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.util.onSome

inline fun <T> applyGlobalOrFieldReadSourceRules(
    currentInst: GoIRInst,
    context: GoMethodAnalysisContext,
    mkSourceEvaluator: () -> SourceActionEvaluator<T>,
    body: (CommonTaintConfigurationSource, CommonTaintAssignAction, AccessPathBase.LocalVar, T) -> Unit
) {
    val inst = currentInst as? GoIRAssignInst ?: return

    val sourceRules = mutableListOf<TaintRule.GoSourceRule>()

    val fieldName = GoFlowFunctionUtils.detectFieldReadName(inst)
    if (fieldName != null) {
        sourceRules += context.taint.sourceRulesForFieldRead(fieldName, currentInst)
    }

    val globalName = GoFlowFunctionUtils.detectGlobalReadName(inst)
    if (globalName != null) {
        sourceRules += context.taint.sourceRulesForGlobal(globalName, currentInst)
    }

    if (sourceRules.isEmpty()) return

    val lhv = AccessPathBase.LocalVar(inst.register.index)
    val sourceEvaluator = mkSourceEvaluator()

    for (rule in sourceRules) {
        if (!rule.condition.isTrue()) {
            TODO("Field/global source with complex condition")
        }

        for (action in rule.actionsAfter) {
            val pos = action.resolvePosAccess()
            val mark = TaintMarkAccessor(action.mark)

            sourceEvaluator.evaluate(rule, action, pos, mark).onSome { evaluatedFacts ->
                evaluatedFacts.forEach {
                    body(rule, action, lhv, it)
                }
            }
        }
    }
}

fun GoAssignAction.resolvePosAccess(): PositionAccess = when (val actionPos = pos) {
    is ActionPosition.Exact -> actionPos.position.resolvePosAccess()
    is ActionPosition.AnyAccessorAfter -> PositionAccess.Complex(actionPos.position.resolvePosAccess(), AnyAccessor)
}
