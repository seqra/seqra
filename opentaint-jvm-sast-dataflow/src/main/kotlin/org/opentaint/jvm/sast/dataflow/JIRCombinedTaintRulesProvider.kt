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

    override fun entryPointRulesForMethod(method: CommonMethod, fact: FactAp?) =
        combine(combinationOptions.entryPoint) { entryPointRulesForMethod(method, fact) }

    override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?) =
        combine(combinationOptions.source) { sourceRulesForMethod(method, statement, fact) }

    override fun exitSourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?) =
        combine(combinationOptions.source) { exitSourceRulesForMethod(method, statement, fact) }

    override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?) =
        combine(combinationOptions.sink) { sinkRulesForMethod(method, statement, fact) }

    override fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst, fact: FactAp?, initialFacts: Set<InitialFactAp>?): Iterable<TaintMethodExitSink> =
        combine(combinationOptions.sink) { sinkRulesForMethodExit(method, statement, fact, initialFacts) }

    override fun sinkRulesForMethodEntry(method: CommonMethod, fact: FactAp?) =
        combine(combinationOptions.sink) { sinkRulesForMethodEntry(method, fact) }

    override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?) =
        combine(combinationOptions.passThrough) { passTroughRulesForMethod(method, statement, fact) }

    override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?) =
        combine(combinationOptions.cleaner) { cleanerRulesForMethod(method, statement, fact) }

    override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?) =
        combine(combinationOptions.source) { sourceRulesForStaticField(field, statement, fact) }

    private inline fun <T> combine(
        mode: CombinationMode,
        rules: TaintRulesProvider.() -> Iterable<T>,
    ): Iterable<T> = when (mode) {
        CombinationMode.EXTEND -> base.rules() + combined.rules()
        CombinationMode.OVERRIDE -> combined.rules()
        CombinationMode.IGNORE -> base.rules()
    }
}
