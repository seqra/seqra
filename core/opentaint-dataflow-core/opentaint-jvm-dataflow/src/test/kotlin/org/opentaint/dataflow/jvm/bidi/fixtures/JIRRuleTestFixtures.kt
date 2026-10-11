package org.opentaint.dataflow.jvm.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ApOps
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactTypeChecker
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.bidi.interp.JIRRuleForms
import org.opentaint.dataflow.jvm.bidi.interp.JIRTypeFilters
import org.opentaint.dataflow.jvm.bidi.interp.RuleErrors
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRField

/** Real rule owners and indexed accessors; expected forms remain in each test. */
internal class JIRRuleTestFixtures(val cp: JIRClasspath) {
    val manager = ApManager(Cancellation())
    val ops = ApOps(manager)
    val checker = JIRFactTypeChecker(cp)
    val typeFilters = JIRTypeFilters(checker, manager)

    fun method(name: String, owner: String = SAMPLE_CLASS) =
        cp.findClassOrNull(owner)!!.declaredMethods.single { it.name == name }

    fun forms(
        rules: TaintRulesProvider = NoTaintRules,
        errors: RuleErrors = RuleErrors(),
        resultBase: AccessPathBase = AccessPathBase.Return,
    ): JIRRuleForms = JIRRuleForms(rules, manager, errors, typeFilters, resultBase)

    fun source(actions: List<AssignMark>, condition: Condition = trueCondition,
               method: CommonMethod = method("plain")) = TaintMethodSource(method, condition, actions, null)
    fun pass(actions: List<Action>, condition: Condition = trueCondition,
             method: CommonMethod = method("plain")) = TaintPassThrough(method, condition, actions, null)
    fun cleaner(actions: List<Action>, condition: Condition = trueCondition,
                method: CommonMethod = method("plain")) = TaintCleaner(method, condition, actions, null)
    fun sink(actions: List<AssignMark> = emptyList(), condition: Condition = trueCondition,
             method: CommonMethod = method("plain")) = TaintMethodSink(method, condition, actions,
        "rule-sink", TaintSinkMeta("test sink", CommonTaintConfigurationSinkMeta.Severity.Error, null), null)

    companion object {
        const val SAMPLE_CLASS = "sample.bidi.BidiRuleSample"
        @Suppress("UNCHECKED_CAST")
        val trueCondition: Condition = CommonCondition.True as Condition
    }
}

internal object NoTaintRules : TaintRulesProvider {
    override fun entryPointRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = emptyList<TaintEntryPointSource>()
    override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = emptyList<TaintMethodSource>()
    override fun exitSourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = emptyList<TaintMethodExitSource>()
    override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = emptyList<TaintMethodSink>()
    override fun sinkRulesForMethodEntry(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = emptyList<TaintMethodEntrySink>()
    override fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst, fact: FactAp?, initialFacts: Set<InitialFactAp>?, allRelevant: Boolean) = emptyList<TaintMethodExitSink>()
    override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst?, fact: FactAp?, allRelevant: Boolean) = emptyList<TaintPassThrough>()
    override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = emptyList<TaintCleaner>()
    override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = emptyList<TaintStaticFieldSource>()
    override fun selectRules(ruleIds: Set<String>) = Unit
}

internal class RecordingTaintRules(var staticSources: List<TaintStaticFieldSource> = emptyList()) : TaintRulesProvider by NoTaintRules {
    data class StaticQuery(val field: JIRField, val statement: CommonInst, val fact: FactAp?, val allRelevant: Boolean)
    val staticQueries = arrayListOf<StaticQuery>()
    override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintStaticFieldSource> {
        staticQueries += StaticQuery(field, statement, fact, allRelevant)
        return staticSources
    }
}
