package org.opentaint.dataflow.configuration.python.serialized

import com.charleskorn.kaml.YamlNode
import org.opentaint.dataflow.configuration.ConfigurationLoader

class PythonConfigurationLoader : ConfigurationLoader<SerializedPythonTaintConfig> {
    override val language: String get() = "python"

    override fun load(node: YamlNode): SerializedPythonTaintConfig =
        ConfigurationLoader.yaml.decodeFromYamlNode<SerializedPythonTaintConfig>(node)

    override fun join(config: List<SerializedPythonTaintConfig>) = SerializedPythonTaintConfig(
        language = language,
        entryPoint = config.flatMap { it.entryPoint },
        source = config.flatMap { it.source },
        sink = config.flatMap { it.sink },
        methodExitSink = config.flatMap { it.methodExitSink },
        passThrough = config.flatMap { it.passThrough },
        cleaner = config.flatMap { it.cleaner },
    )
}
