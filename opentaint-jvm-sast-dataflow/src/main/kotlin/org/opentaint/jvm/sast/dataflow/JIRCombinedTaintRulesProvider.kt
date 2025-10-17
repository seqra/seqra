package org.opentaint.api.checkers

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.TaintRulesProvider

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

    override fun entryPointRulesForMethod(method: CommonMethod) =
        combine(combinationOptions.entryPoint) { entryPointRulesForMethod(method) }

    override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst) =
        combine(combinationOptions.source) { sourceRulesForMethod(method, statement) }

    override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst) =
        combine(combinationOptions.sink) { sinkRulesForMethod(method, statement) }

    override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst) =
        combine(combinationOptions.passThrough) { passTroughRulesForMethod(method, statement) }

    override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst) =
        combine(combinationOptions.cleaner) { cleanerRulesForMethod(method, statement) }

    private inline fun <T> combine(
        mode: CombinationMode,
        rules: TaintRulesProvider.() -> Iterable<T>,
    ): Iterable<T> = when (mode) {
        CombinationMode.EXTEND -> base.rules() + combined.rules()
        CombinationMode.OVERRIDE -> combined.rules()
        CombinationMode.IGNORE -> base.rules()
    }
}
