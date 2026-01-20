package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration

class JIRTaintRulesProvider(
    private val taintConfiguration: TaintConfiguration
) : TaintRulesProvider {
    override fun entryPointRulesForMethod(method: CommonMethod, fact: FactAp?) = getRules(method) {
        taintConfiguration.entryPointForMethod(it)
    }

    override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?) = getRules(method) {
        taintConfiguration.sourceForMethod(it)
    }

    override fun exitSourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?) = getRules(method) {
        taintConfiguration.exitSourceForMethod(it)
    }

    override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?) = getRules(method) {
        taintConfiguration.sinkForMethod(it)
    }

    override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?) = getRules(method) {
        taintConfiguration.passThroughForMethod(it)
    }

    override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?) = getRules(method) {
        taintConfiguration.cleanerForMethod(it)
    }

    override fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst, fact: FactAp?, initialFacts: Set<InitialFactAp>?): Iterable<TaintMethodExitSink> = getRules(method) {
        taintConfiguration.methodExitSinkForMethod(it)
    }

    override fun sinkRulesForMethodEntry(method: CommonMethod, fact: FactAp?) = getRules(method) {
        taintConfiguration.methodEntrySinkForMethod(it)
    }

    override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?) =
        taintConfiguration.sourceForStaticField(field)

    private inline fun <T : TaintConfigurationItem> getRules(
        method: CommonMethod,
        body: (JIRMethod) -> Iterable<T>
    ): Iterable<T> {
        check(method is JIRMethod) { "Expected method to be JIRMethod" }
        return body(method)
    }
}
