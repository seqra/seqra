package org.opentaint.dataflow.python.rules

import org.opentaint.dataflow.configuration.python.TaintCleaner
import org.opentaint.dataflow.configuration.python.TaintEntryPointSource
import org.opentaint.dataflow.configuration.python.TaintExitSink
import org.opentaint.dataflow.configuration.python.TaintPassThrough
import org.opentaint.dataflow.configuration.python.TaintSink
import org.opentaint.dataflow.configuration.python.TaintSource
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonTaintConfig
import org.opentaint.ir.api.python.PIRFunction
import java.util.concurrent.ConcurrentHashMap
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonCleaner
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonEntryPointSource
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonExitSink
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonPassThrough
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonSink
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonSource

class PIRTaintConfiguration() {
    private val entryPoint: MutableList<SerializedPythonEntryPointSource> = mutableListOf()
    private val source: MutableList<SerializedPythonSource> = mutableListOf()
    private val sink: MutableList<SerializedPythonSink> = mutableListOf()
    private val methodExitSink: MutableList<SerializedPythonExitSink> = mutableListOf()
    private val passThrough: MutableList<SerializedPythonPassThrough> = mutableListOf()
    private val cleaner: MutableList<SerializedPythonCleaner> = mutableListOf()

    private val entryPointsByMethod = ConcurrentHashMap<PIRFunction, List<TaintEntryPointSource>>()
    private val sourcesByMethod = ConcurrentHashMap<PIRFunction, List<TaintSource>>()
    private val sinksByMethod = ConcurrentHashMap<PIRFunction, List<TaintSink>>()
    private val exitSinksByMethod = ConcurrentHashMap<PIRFunction, List<TaintExitSink>>()
    private val passThroughByMethod = ConcurrentHashMap<PIRFunction, List<TaintPassThrough>>()
    private val passThroughBySimpleMethod = ConcurrentHashMap<PIRFunction, List<TaintPassThrough>>()
    private val cleanersByMethod = ConcurrentHashMap<PIRFunction, List<TaintCleaner>>()

    private val sourcesByAttribute = ConcurrentHashMap<String, List<TaintSource>>()
    private val sinksByAttribute = ConcurrentHashMap<String, List<TaintSink>>()
    private val passThroughByAttribute = ConcurrentHashMap<String, List<TaintPassThrough>>()
    private val cleanersByAttribute = ConcurrentHashMap<String, List<TaintCleaner>>()

    fun loadConfig(config: SerializedPythonTaintConfig) {
        entryPoint += config.entryPoint
        source += config.source
        sink += config.sink
        methodExitSink += config.methodExitSink
        passThrough += config.passThrough
        cleaner += config.cleaner
    }

    fun entryPointSourcesForMethod(method: PIRFunction): List<TaintEntryPointSource> =
        entryPointsByMethod.cached(method) { MethodTaintConfigurationResolver(it).resolveEntryPoints(entryPoint) }

    fun sourcesForMethod(method: PIRFunction): List<TaintSource> =
        sourcesByMethod.cached(method) { MethodTaintConfigurationResolver(it).resolveSources(source) }

    fun sinksForMethod(method: PIRFunction): List<TaintSink> =
        sinksByMethod.cached(method) { MethodTaintConfigurationResolver(it).resolveSinks(sink) }

    fun exitSinksForMethod(method: PIRFunction): List<TaintExitSink> =
        exitSinksByMethod.cached(method) { MethodTaintConfigurationResolver(it).resolveExitSinks(methodExitSink) }

    fun passThroughForMethod(method: PIRFunction, bySimpleName: Boolean): List<TaintPassThrough> =
        if (bySimpleName) {
            passThroughBySimpleMethod.cached(method) {
                MethodTaintConfigurationResolver(it, bySimpleName = true).resolvePassThrough(passThrough)
            }
        } else {
            passThroughByMethod.cached(method) {
                MethodTaintConfigurationResolver(it, bySimpleName = false).resolvePassThrough(passThrough)
            }
        }

    fun cleanersForMethod(method: PIRFunction): List<TaintCleaner> =
        cleanersByMethod.cached(method) { MethodTaintConfigurationResolver(it).resolveCleaners(cleaner) }

    fun sourcesForAttribute(name: String): List<TaintSource> =
        sourcesByAttribute.cached(name) { MethodTaintConfigurationResolver(method = null).resolveAttributeSources(source, it) }

    fun sinksForAttribute(name: String): List<TaintSink> =
        sinksByAttribute.cached(name) { MethodTaintConfigurationResolver(method = null).resolveAttributeSinks(sink, it) }

    fun passThroughForAttribute(name: String): List<TaintPassThrough> =
        passThroughByAttribute.cached(name) { MethodTaintConfigurationResolver(method = null).resolveAttributePassThrough(passThrough, it) }

    fun cleanersForAttribute(name: String): List<TaintCleaner> =
        cleanersByAttribute.cached(name) { MethodTaintConfigurationResolver(method = null).resolveAttributeCleaners(cleaner, it) }

    private inline fun <K : Any, V> ConcurrentHashMap<K, List<V>>.cached(key: K, crossinline resolve: (K) -> List<V>): List<V> =
        computeIfAbsent(key) { resolve(it).ifEmpty { emptyList() } }
}
