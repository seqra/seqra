package org.opentaint.dataflow.go.rules

/**
 * Bridges GoTaintConfig to rule queries used by flow functions.
 * Pre-indexes rules by function name for O(1) lookup.
 */
class GoTaintRulesProvider(val config: GoTaintConfig) {

    private val sourcesByFunction: Map<String, List<TaintRules.Source>> =
        config.sources.groupBy { it.function }

    private val sinksByFunction: Map<String, List<TaintRules.Sink>> =
        config.sinks.groupBy { it.function }

    private val passByFunction: Map<String, List<TaintRules.Pass>> =
        config.propagators.groupBy { it.function }

    fun sourceRulesForCall(calleeName: String): List<TaintRules.Source> =
        sourcesByFunction[calleeName] ?: emptyList()

    fun sinkRulesForCall(calleeName: String): List<TaintRules.Sink> =
        sinksByFunction[calleeName] ?: emptyList()

    fun passRulesForCall(calleeName: String): List<TaintRules.Pass> =
        passByFunction[calleeName] ?: emptyList()

    fun hasAnyRulesForCall(calleeName: String): Boolean =
        calleeName in sourcesByFunction || calleeName in sinksByFunction || calleeName in passByFunction
}
