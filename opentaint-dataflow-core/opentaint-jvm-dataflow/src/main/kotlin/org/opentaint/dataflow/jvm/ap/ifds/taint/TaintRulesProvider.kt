package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.dataflow.configuration.CommonTaintRulesProvider
import org.opentaint.dataflow.configuration.jvm.TaintCleaner
import org.opentaint.dataflow.configuration.jvm.TaintEntryPointSource
import org.opentaint.dataflow.configuration.jvm.TaintMethodEntrySink
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSource
import org.opentaint.dataflow.configuration.jvm.TaintMethodSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodSource
import org.opentaint.dataflow.configuration.jvm.TaintPassThrough
import org.opentaint.dataflow.configuration.jvm.TaintStaticFieldSource

interface TaintRulesProvider : CommonTaintRulesProvider {
    fun entryPointRulesForMethod(method: CommonMethod, fact: FactAp?): Iterable<TaintEntryPointSource>
    fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?): Iterable<TaintMethodSource>
    fun exitSourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?): Iterable<TaintMethodExitSource>
    fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?): Iterable<TaintMethodSink>
    fun sinkRulesForMethodEntry(method: CommonMethod, fact: FactAp?): Iterable<TaintMethodEntrySink>
    fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst, fact: FactAp?, initialFacts: Set<InitialFactAp>?): Iterable<TaintMethodExitSink>
    fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?): Iterable<TaintPassThrough>
    fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?): Iterable<TaintCleaner>
    fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?): Iterable<TaintStaticFieldSource>
}
