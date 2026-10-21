package org.opentaint.dataflow.python.rules

import org.opentaint.ir.api.python.PIRFunction

class PIRCombinedTaintRulesProvider(
    private val base: PIRTaintRulesProvider,
    private val combined: PIRTaintRulesProvider,
    private val options: CombinationOptions = CombinationOptions(),
) : PIRTaintRulesProvider {
    enum class CombinationMode { EXTEND, OVERRIDE, IGNORE }

    data class CombinationOptions(
        val entryPoint: CombinationMode = CombinationMode.OVERRIDE,
        val source: CombinationMode = CombinationMode.OVERRIDE,
        val sink: CombinationMode = CombinationMode.OVERRIDE,
        val passThrough: CombinationMode = CombinationMode.EXTEND,
        val cleaner: CombinationMode = CombinationMode.EXTEND,
    )

    override fun entryPointSourcesForMethod(method: PIRFunction) =
        combine(options.entryPoint, base.entryPointSourcesForMethod(method), combined.entryPointSourcesForMethod(method))

    override fun sourcesForMethod(method: PIRFunction) =
        combine(options.source, base.sourcesForMethod(method), combined.sourcesForMethod(method))

    override fun sinksForMethod(method: PIRFunction) =
        combine(options.sink, base.sinksForMethod(method), combined.sinksForMethod(method))

    override fun exitSinksForMethod(method: PIRFunction) =
        combine(options.sink, base.exitSinksForMethod(method), combined.exitSinksForMethod(method))

    override fun passThroughForMethod(method: PIRFunction, bySimpleName: Boolean) =
        combine(options.passThrough, base.passThroughForMethod(method, bySimpleName), combined.passThroughForMethod(method, bySimpleName))

    override fun cleanersForMethod(method: PIRFunction) =
        combine(options.cleaner, base.cleanersForMethod(method), combined.cleanersForMethod(method))

    override fun sourcesForAttribute(name: String) =
        combine(options.source, base.sourcesForAttribute(name), combined.sourcesForAttribute(name))

    override fun sinksForAttribute(name: String) =
        combine(options.sink, base.sinksForAttribute(name), combined.sinksForAttribute(name))

    override fun passThroughForAttribute(name: String) =
        combine(options.passThrough, base.passThroughForAttribute(name), combined.passThroughForAttribute(name))

    override fun cleanersForAttribute(name: String) =
        combine(options.cleaner, base.cleanersForAttribute(name), combined.cleanersForAttribute(name))

    private fun <T> combine(mode: CombinationMode, base: List<T>, extra: List<T>): List<T> = when (mode) {
        CombinationMode.EXTEND -> base + extra
        CombinationMode.OVERRIDE -> extra.ifEmpty { base }
        CombinationMode.IGNORE -> base
    }
}
