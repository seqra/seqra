package org.opentaint.semgrep.pattern.conversion

import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonCleaner
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonEntryPointSource
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonExitSink
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonPassThrough
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonRule
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonSink
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonSource
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonTaintConfig
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep

fun TaintRuleFromSemgrep<SerializedPythonRule>.toSerializedPythonTaintConfig(): SerializedPythonTaintConfig {
    val items = taintRules.flatMap { it.rules }
    return SerializedPythonTaintConfig(
        entryPoint = items.filterIsInstance<SerializedPythonEntryPointSource>(),
        source = items.filterIsInstance<SerializedPythonSource>(),
        sink = items.filterIsInstance<SerializedPythonSink>(),
        methodExitSink = items.filterIsInstance<SerializedPythonExitSink>(),
        passThrough = items.filterIsInstance<SerializedPythonPassThrough>(),
        cleaner = items.filterIsInstance<SerializedPythonCleaner>(),
    )
}
