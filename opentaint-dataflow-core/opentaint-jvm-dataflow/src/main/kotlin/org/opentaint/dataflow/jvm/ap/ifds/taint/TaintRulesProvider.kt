package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.dataflow.configuration.CommonTaintRulesProvider
import org.opentaint.dataflow.configuration.jvm.TaintCleaner
import org.opentaint.dataflow.configuration.jvm.TaintEntryPointSource
import org.opentaint.dataflow.configuration.jvm.TaintMethodEntrySink
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodSource
import org.opentaint.dataflow.configuration.jvm.TaintPassThrough
import org.opentaint.dataflow.configuration.jvm.TaintStaticFieldSource

interface TaintRulesProvider : CommonTaintRulesProvider {
    fun entryPointRulesForMethod(method: CommonMethod): Iterable<TaintEntryPointSource>
    fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst): Iterable<TaintMethodSource>
    fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst): Iterable<TaintMethodSink>
    fun sinkRulesForMethodEntry(method: CommonMethod): Iterable<TaintMethodEntrySink>
    fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst): Iterable<TaintMethodExitSink>
    fun sinkRulesForAnalysisEnd(method: CommonMethod, statement: CommonInst): Iterable<TaintMethodExitSink>
    fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst): Iterable<TaintPassThrough>
    fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst): Iterable<TaintCleaner>
    fun sourceRulesForStaticField(field: JIRField, statement: CommonInst): Iterable<TaintStaticFieldSource>
}
