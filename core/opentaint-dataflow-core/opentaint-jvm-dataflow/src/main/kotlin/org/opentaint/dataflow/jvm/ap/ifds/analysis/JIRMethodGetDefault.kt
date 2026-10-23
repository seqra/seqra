package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.taint.TaintAnalysisContext.RuleWithCondition
import org.opentaint.dataflow.configuration.jvm.ActionPosition.Exact
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.PositionAccessor
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.TaintPassThrough
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.taint.RuleConditionRewriter
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.impl.cfg.util.isArray
import org.opentaint.ir.impl.types.TypeNameImpl

class JIRMethodGetDefault(
    private val config: Configuration,
) {
    interface Configuration {
        fun enableDefaultPropagationForClass(cls: JIRClassOrInterface): Boolean
    }

    private val objectTypeName = TypeNameImpl.fromTypeName("java.lang.Object")

    private fun TypeName.mayBeArray(): Boolean = isArray || this == objectTypeName

    private val getDefaultActions = listOf(
        CopyAllMarks(from = Exact(This), to = Exact(Result))
    )

    private val getDefaultArrayActions = listOf(
        CopyAllMarks(from = Exact(This), to = Exact(PositionWithAccess(Result, PositionAccessor.ElementAccessor)))
    )

    fun defaultPropagationRules(method: JIRMethod): List<RuleWithCondition<TaintPassThrough>> {
        if (method.isStatic) return emptyList()

        if (!method.name.startsWith("get")) return emptyList()

        if (!config.enableDefaultPropagationForClass(method.enclosingClass)) return emptyList()

        var actions = getDefaultActions
        if (method.returnType.mayBeArray()) {
            actions = actions + getDefaultArrayActions
        }

        val getDefaultRule = TaintPassThrough(method, mkTrue(), actions, info = null)
        return listOf(RuleWithCondition(getDefaultRule, RuleConditionRewriter.trueExpr))
    }
}
