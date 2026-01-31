package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.ConstantTrue
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.TaintPassThrough
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRMethod

class StringConcatRuleProvider(private val base: TaintRulesProvider) : TaintRulesProvider by base {
    private var stringConcatPassThrough: TaintPassThrough? = null

    private fun stringConcatPassThrough(method: JIRMethod): TaintPassThrough =
        stringConcatPassThrough ?: generateRule(method).also { stringConcatPassThrough = it }

    private fun generateRule(method: JIRMethod): TaintPassThrough {
        // todo: string concat hack
        val possibleArgs = (0..20).map { Argument(it) }

        return TaintPassThrough(
            method = method,
            condition = ConstantTrue,
            actionsAfter = possibleArgs.map { CopyAllMarks(from = it, to = Result) },
            info = null
        )
    }

    override fun passTroughRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ): Iterable<TaintPassThrough> {
        check(method is JIRMethod) { "Expected method to be JIRMethod" }
        val baseRules = base.passTroughRulesForMethod(method, statement, fact, allRelevant)

        if (method.name == "makeConcatWithConstants" && method.enclosingClass.name == "java.lang.invoke.StringConcatFactory") {
            return (sequenceOf(stringConcatPassThrough(method)) + baseRules).asIterable()
        }

        return baseRules
    }
}
