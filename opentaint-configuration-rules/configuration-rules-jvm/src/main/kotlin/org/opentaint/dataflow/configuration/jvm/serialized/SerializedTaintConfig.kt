package org.opentaint.dataflow.configuration.jvm.serialized

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.decodeFromStream
import kotlinx.serialization.Serializable
import java.io.InputStream

@Serializable
data class SerializedTaintConfig(
    val entryPoint: List<SerializedRule.EntryPoint>? = null,
    val source: List<SerializedRule.Source>? = null,
    val sink: List<SerializedRule.Sink>? = null,
    val passThrough: List<SerializedRule.PassThrough>? = null,
    val cleaner: List<SerializedRule.Cleaner>? = null,
    val methodExitSink: List<SerializedRule.MethodExitSink>? = null,
    val methodEntrySink: List<SerializedRule.MethodEntrySink>? = null,
)

fun loadSerializedTaintConfig(stream: InputStream): SerializedTaintConfig {
    val yaml = Yaml(configuration = YamlConfiguration(codePointLimit = Int.MAX_VALUE))
    return yaml.decodeFromStream<SerializedTaintConfig>(stream)
}
