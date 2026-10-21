package org.opentaint.dataflow.python.rules

import org.opentaint.ir.api.python.PIRFunction

class PIRConfigTaintRulesProvider(private val config: PIRTaintConfiguration) : PIRTaintRulesProvider {
    override fun entryPointSourcesForMethod(method: PIRFunction) = config.entryPointSourcesForMethod(method)
    override fun sourcesForMethod(method: PIRFunction) = config.sourcesForMethod(method)
    override fun sinksForMethod(method: PIRFunction) = config.sinksForMethod(method)
    override fun exitSinksForMethod(method: PIRFunction) = config.exitSinksForMethod(method)
    override fun passThroughForMethod(method: PIRFunction, bySimpleName: Boolean) = config.passThroughForMethod(method, bySimpleName)
    override fun cleanersForMethod(method: PIRFunction) = config.cleanersForMethod(method)

    override fun sourcesForAttribute(name: String) = config.sourcesForAttribute(name)
    override fun sinksForAttribute(name: String) = config.sinksForAttribute(name)
    override fun passThroughForAttribute(name: String) = config.passThroughForAttribute(name)
    override fun cleanersForAttribute(name: String) = config.cleanersForAttribute(name)
}
