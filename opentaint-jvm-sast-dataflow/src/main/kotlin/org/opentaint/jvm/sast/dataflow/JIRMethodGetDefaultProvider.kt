package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.configuration.jvm.ConstantTrue
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.TaintPassThrough
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRMethod

class JIRMethodGetDefaultProvider(
    val base: TaintRulesProvider,
    private val projectClasses: ClassLocationChecker,
) : TaintRulesProvider by base {
    override fun passTroughRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ): Iterable<TaintPassThrough> {
        val baseRules = base.passTroughRulesForMethod(method, statement, fact, allRelevant)

        if (method !is JIRMethod || method.isStatic) return baseRules

        if (!method.name.startsWith("get")) return baseRules

        if (projectClasses.isProjectClass(method.enclosingClass)) return baseRules

        val getDefaultRule = TaintPassThrough(method, ConstantTrue, getDefaultActions, info = null)
        return baseRules + getDefaultRule
    }

    companion object {
        private val getDefaultActions = listOf(
            CopyAllMarks(from = This, to = Result)
        )
    }
}
