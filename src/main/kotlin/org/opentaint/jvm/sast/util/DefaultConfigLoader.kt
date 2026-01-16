package org.opentaint.jvm.sast.util

import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.loadSerializedTaintConfig

private object DefaultConfigLoader

fun loadDefaultConfig(): SerializedTaintConfig {
    val config = DefaultConfigLoader.javaClass.classLoader.getResourceAsStream("config.yaml")
        ?: error("Default configuration not found")

    return config.use { loadSerializedTaintConfig(it) }
}
