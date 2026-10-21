package org.opentaint.dataflow.python.rules

import org.opentaint.dataflow.configuration.CommonTaintRulesProvider
import org.opentaint.dataflow.configuration.python.TaintCleaner
import org.opentaint.dataflow.configuration.python.TaintEntryPointSource
import org.opentaint.dataflow.configuration.python.TaintExitSink
import org.opentaint.dataflow.configuration.python.TaintPassThrough
import org.opentaint.dataflow.configuration.python.TaintSink
import org.opentaint.dataflow.configuration.python.TaintSource
import org.opentaint.ir.api.python.PIRFunction

interface PIRTaintRulesProvider : CommonTaintRulesProvider {
    fun entryPointSourcesForMethod(method: PIRFunction): List<TaintEntryPointSource>
    fun sourcesForMethod(method: PIRFunction): List<TaintSource>
    fun sinksForMethod(method: PIRFunction): List<TaintSink>

    fun exitSinksForMethod(method: PIRFunction): List<TaintExitSink>
    fun passThroughForMethod(method: PIRFunction, bySimpleName: Boolean = false): List<TaintPassThrough>
    fun cleanersForMethod(method: PIRFunction): List<TaintCleaner>

    fun sourcesForAttribute(name: String): List<TaintSource>
    fun sinksForAttribute(name: String): List<TaintSink>
    fun passThroughForAttribute(name: String): List<TaintPassThrough>
    fun cleanersForAttribute(name: String): List<TaintCleaner>
}
