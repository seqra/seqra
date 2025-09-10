package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.taint.configuration.TaintCleaner
import org.opentaint.ir.taint.configuration.TaintEntryPointSource
import org.opentaint.ir.taint.configuration.TaintMethodSink
import org.opentaint.ir.taint.configuration.TaintMethodSource
import org.opentaint.ir.taint.configuration.TaintPassThrough

interface TaintRulesProvider {
    fun entryPointRulesForMethod(method: CommonMethod): Iterable<TaintEntryPointSource>
    fun sourceRulesForMethod(method: CommonMethod): Iterable<TaintMethodSource>
    fun sinkRulesForMethod(method: CommonMethod): Iterable<TaintMethodSink>
    fun passTroughRulesForMethod(method: CommonMethod): Iterable<TaintPassThrough>
    fun cleanerRulesForMethod(method: CommonMethod): Iterable<TaintCleaner>
}
