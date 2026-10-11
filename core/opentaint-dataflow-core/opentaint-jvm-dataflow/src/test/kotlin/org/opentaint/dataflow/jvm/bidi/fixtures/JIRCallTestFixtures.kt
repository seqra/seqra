package org.opentaint.dataflow.jvm.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodEntrypointResolver
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver.MethodResolutionResult
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodGetDefault
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.bidi.interp.JIRCallPlanBuilder
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.jvm.graph.JMethodEnterInst

/** Real call geometry with independently supplied resolution rows. */
internal class JIRCallTestFixtures(val cp: JIRClasspath) {
    private val ruleFixtures = JIRRuleTestFixtures(cp)
    val manager get() = ruleFixtures.manager
    val ops get() = ruleFixtures.ops
    val checker get() = ruleFixtures.checker
    val typeFilters get() = ruleFixtures.typeFilters
    fun forms(rules: TaintRulesProvider = NoTaintRules) = ruleFixtures.forms(rules)
    fun method(name: String, owner: String = SAMPLE_CLASS): JIRMethod =
        cp.findClassOrNull(owner)!!.declaredMethods.single { it.name == name }
    fun call(name: String): JIRInst = method(name).instList.instructions.first {
        val call = it.callExpr ?: return@first false
        if (name == "constructor") call.method.method.enclosingClass.name == SAMPLE_CLASS && call.method.method.isConstructor
        else if (name.startsWith("alias")) call.method.method.name == "external"
        else if (name.startsWith("virtual")) call.method.method.name == "virtual"
        else true
    }
    fun expr(inst: JIRInst): JIRCallExpr = checkNotNull(inst.callExpr)
    fun key(name: String = "staticCall", context: MethodContext = EmptyMethodContext): MethodKey = key(method(name), context)
    fun key(method: JIRMethod, context: MethodContext = EmptyMethodContext): MethodKey =
        MethodEntryPoint(context, method.instList.instructions.first { it is JMethodEnterInst })
    fun resolved(name: String = "target", context: MethodContext = EmptyMethodContext,
                 owner: String = SAMPLE_CLASS): MethodResolutionResult =
        MethodResolutionResult.ConcreteMethod(MethodWithContext(method(name, owner), context))

    val epResolver: MethodEntrypointResolver = object : MethodEntrypointResolver {
        override fun resolveEntryPoints(method: CommonMethod, context: MethodContext): List<CommonInst> =
            (method as JIRMethod).instList.instructions.filterIsInstance<JMethodEnterInst>()
    }

    fun builder(
        name: String = "staticCall",
        rules: TaintRulesProvider = NoTaintRules,
        resolutions: List<MethodResolutionResult> = listOf(MethodResolutionResult.MethodResolutionFailed),
        lambdas: Map<Int, Set<JIRMethod>> = emptyMap(),
        defaultGetModel: JIRMethodGetDefault? = null,
        persistedAliases: (org.opentaint.dataflow.ap.ifds.AccessPathBase.LocalVar, JIRInst) -> List<JIRLocalAliasAnalysis.AliasApInfo> = { _, _ -> emptyList() },
    ): JIRCallPlanBuilder = builder(call(name), rules, resolutions, lambdas, defaultGetModel, persistedAliases)

    fun builder(
        inst: JIRInst,
        rules: TaintRulesProvider = NoTaintRules,
        resolutions: List<MethodResolutionResult> = listOf(MethodResolutionResult.MethodResolutionFailed),
        lambdas: Map<Int, Set<JIRMethod>> = emptyMap(),
        defaultGetModel: JIRMethodGetDefault? = null,
        persistedAliases: (org.opentaint.dataflow.ap.ifds.AccessPathBase.LocalVar, JIRInst) -> List<JIRLocalAliasAnalysis.AliasApInfo> = { _, _ -> emptyList() },
    ): JIRCallPlanBuilder = JIRCallPlanBuilder(manager, forms(rules), checker, typeFilters,
        key(inst.location.method), inst, expr(inst), { _, _, _ -> resolutions }, epResolver, lambdas,
        defaultGetModel = defaultGetModel, persistedAliases = persistedAliases)

    companion object { const val SAMPLE_CLASS = "sample.bidi.BidiCallSample" }
}

/** Records both reduced and all-relevant method queries without depending on a propagated fact. */
internal class CallRules : TaintRulesProvider by NoTaintRules {
    var sources = emptyList<TaintMethodSource>()
    var sinks = emptyList<TaintMethodSink>()
    var passes = emptyList<TaintPassThrough>()
    var cleaners = emptyList<TaintCleaner>()
    var allSources: List<TaintMethodSource>? = null
    var allCleaners: List<TaintCleaner>? = null
    data class Query(val kind: String, val method: CommonMethod, val statement: CommonInst?, val fact: FactAp?, val allRelevant: Boolean)
    val queries = arrayListOf<Query>()
    override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintMethodSource> {
        queries += Query("source", method, statement, fact, allRelevant)
        return if (allRelevant) allSources ?: sources else sources
    }
    override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintMethodSink> {
        queries += Query("sink", method, statement, fact, allRelevant)
        return sinks
    }
    override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst?, fact: FactAp?, allRelevant: Boolean): Iterable<TaintPassThrough> {
        queries += Query("pass", method, statement, fact, allRelevant)
        return passes
    }
    override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintCleaner> {
        queries += Query("cleaner", method, statement, fact, allRelevant)
        return if (allRelevant) allCleaners ?: cleaners else cleaners
    }
}
