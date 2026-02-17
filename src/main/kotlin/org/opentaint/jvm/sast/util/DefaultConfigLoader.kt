package org.opentaint.jvm.sast.util

import org.opentaint.config.ConfigLoader
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig

fun loadDefaultConfig(): SerializedTaintConfig {
    return ConfigLoader.getConfig() ?: error("Error while loading config")
}
