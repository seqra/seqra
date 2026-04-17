package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField

class JIRCombinedTaintRulesProvider(
    private val base: TaintRulesProvider,
    private val combined: TaintRulesProvider,
    private val combinationOptions: CombinationOptions = CombinationOptions(),
) : TaintRulesProvider {
    enum class CombinationMode {
        EXTEND, OVERRIDE, IGNORE
    }

    data class CombinationOptions(
        val entryPoint: CombinationMode = CombinationMode.OVERRIDE,
        val source: CombinationMode = CombinationMode.OVERRIDE,
        val sink: CombinationMode = CombinationMode.OVERRIDE,
        val passThrough: CombinationMode = CombinationMode.EXTEND,
        val cleaner: CombinationMode = CombinationMode.EXTEND,
    )

    override fun entryPointRulesForMethod(method: CommonMethod, fact: FactAp?, allRelevant: Boolean) =
        combine(combinationOptions.entryPoint) { entryPointRulesForMethod(method, fact, allRelevant) }

    override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        combine(combinationOptions.source) { sourceRulesForMethod(method, statement, fact, allRelevant) }

    override fun exitSourceRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = combine(combinationOptions.source) { exitSourceRulesForMethod(method, statement, fact, allRelevant) }

    override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        combine(combinationOptions.sink) { sinkRulesForMethod(method, statement, fact, allRelevant) }

    override fun sinkRulesForMethodExit(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        initialFacts: Set<InitialFactAp>?,
        allRelevant: Boolean
    ): Iterable<TaintMethodExitSink> =
        combine(combinationOptions.sink) { sinkRulesForMethodExit(method, statement, fact, initialFacts, allRelevant) }

    override fun sinkRulesForMethodEntry(method: CommonMethod, fact: FactAp?, allRelevant: Boolean) =
        combine(combinationOptions.sink) { sinkRulesForMethodEntry(method, fact, allRelevant) }

    override fun passTroughRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = combine(combinationOptions.passThrough) { passTroughRulesForMethod(method, statement, fact, allRelevant) }

    override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        combine(combinationOptions.cleaner) { cleanerRulesForMethod(method, statement, fact, allRelevant) }

    override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        combine(combinationOptions.source) { sourceRulesForStaticField(field, statement, fact, allRelevant) }

    private inline fun <T> combine(
        mode: CombinationMode,
        rules: TaintRulesProvider.() -> Iterable<T>,
    ): Iterable<T> {
        val baseRules = base.rules()
        val combinedRules = combined.rules().toList()

        return when (mode) {
            CombinationMode.EXTEND -> baseRules + combinedRules
            CombinationMode.OVERRIDE -> combinedRules.takeIf { it.isNotEmpty() } ?: baseRules
            CombinationMode.IGNORE -> baseRules
        }
    }
}
