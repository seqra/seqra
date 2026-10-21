package org.opentaint.dataflow.configuration.python.serialized

import kotlinx.serialization.Serializable

@Serializable
data class SerializedPythonTaintConfig(
    val language: String? = null,
    val entryPoint: List<SerializedPythonEntryPointSource> = emptyList(),
    val source: List<SerializedPythonSource> = emptyList(),
    val sink: List<SerializedPythonSink> = emptyList(),
    val methodExitSink: List<SerializedPythonExitSink> = emptyList(),
    val passThrough: List<SerializedPythonPassThrough> = emptyList(),
    val cleaner: List<SerializedPythonCleaner> = emptyList(),
)
