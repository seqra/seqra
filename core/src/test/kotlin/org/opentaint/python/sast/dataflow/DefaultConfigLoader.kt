package org.opentaint.python.sast.dataflow

import org.opentaint.dataflow.python.rules.PIRConfigTaintRulesProvider
import org.opentaint.dataflow.python.rules.PIRTaintConfiguration
import org.opentaint.dataflow.python.rules.PIRTaintRulesProvider
import org.opentaint.python.config.PythonDefaultConfigLoader

private val defaultConfig by lazy { PythonDefaultConfigLoader.loadConfig() }

fun loadDefaultConfig(): PIRTaintRulesProvider {
    val serialized = defaultConfig ?: error("Error while loading config")
    val taintConfig = PIRTaintConfiguration().apply { loadConfig(serialized) }
    return PIRConfigTaintRulesProvider(taintConfig)
}
