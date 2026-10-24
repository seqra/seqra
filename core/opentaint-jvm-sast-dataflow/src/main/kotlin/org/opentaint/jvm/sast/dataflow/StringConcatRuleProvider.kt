package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.TaintPassThrough
import org.opentaint.dataflow.configuration.jvm.appendSyntheticRules
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.originalPassThroughRulesForAliases
import org.opentaint.dataflow.jvm.ap.ifds.taint.JVMRuleSelectionCapability
import org.opentaint.dataflow.jvm.ap.ifds.taint.executableRuleCatalog
import org.opentaint.dataflow.jvm.ap.ifds.taint.includeExecutableRuleScope
import org.opentaint.dataflow.jvm.ap.ifds.taint.currentMarkReduction
import org.opentaint.dataflow.jvm.ap.ifds.taint.applyExecutableMarkReduction
import org.opentaint.dataflow.jvm.ap.ifds.taint.JVMExecutableRuleCatalog
import org.opentaint.dataflow.jvm.ap.ifds.taint.ExecutableMarkReduction
import org.opentaint.dataflow.jvm.ap.ifds.taint.BidirectionalRulesProviderFactory
import org.opentaint.dataflow.jvm.ap.ifds.taint.forBidirectionalCore
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRMethod

class StringConcatRuleProvider(private val base: TaintRulesProvider) : TaintRulesProvider by base, BidirectionalRulesProviderFactory, JVMRuleSelectionCapability {
    override fun executableRuleCatalog() = base.executableRuleCatalog()
    override fun includeExecutableRuleScope(key: Any, scope: () -> JVMExecutableRuleCatalog?) =
        base.includeExecutableRuleScope(key, scope)
    override fun currentMarkReduction() = base.currentMarkReduction()
    override fun applyExecutableMarkReduction(reduction: ExecutableMarkReduction) =
        base.applyExecutableMarkReduction(reduction)

    override fun bidirectionalRulesView(): TaintRulesProvider = StringConcatRuleProvider(base.forBidirectionalCore())
    private var stringConcatPassThrough: TaintPassThrough? = null

    private fun stringConcatPassThrough(method: JIRMethod): TaintPassThrough =
        stringConcatPassThrough ?: generateRule(method).also { stringConcatPassThrough = it }

    private fun generateRule(method: JIRMethod): TaintPassThrough {
        // todo: string concat hack
        val possibleArgs = (0..20).map { Argument(it) }

        return TaintPassThrough(
            method = method,
            condition = mkTrue(),
            actionsAfter = possibleArgs.map { CopyAllMarks(from = it, to = Result) },
            info = null
        )
    }

    override fun passTroughRulesForMethod(
        method: CommonMethod,
        statement: CommonInst?,
        fact: FactAp?,
        allRelevant: Boolean
    ): Iterable<TaintPassThrough> {
        check(method is JIRMethod) { "Expected method to be JIRMethod" }
        val baseRules = base.passTroughRulesForMethod(method, statement, fact, allRelevant)

        return withConcat(method, baseRules)
    }

    override fun originalPassThroughRulesForAliases(method: CommonMethod): Iterable<TaintPassThrough> {
        check(method is JIRMethod) { "Expected method to be JIRMethod" }
        return withConcat(method, base.originalPassThroughRulesForAliases(method))
    }

    private fun withConcat(method: JIRMethod, baseRules: Iterable<TaintPassThrough>): Iterable<TaintPassThrough> {
        if (method.name == "makeConcatWithConstants" && method.enclosingClass.name == "java.lang.invoke.StringConcatFactory") {
            return appendSyntheticRules(baseRules, listOf(stringConcatPassThrough(method)), prepend = true)
        }

        return baseRules
    }
}
