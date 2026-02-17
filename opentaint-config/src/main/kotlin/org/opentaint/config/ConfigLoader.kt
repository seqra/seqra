package org.opentaint.config

import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.loadSerializedTaintConfig
import java.io.File

private object ConfigLoader

fun loadConfig(): SerializedTaintConfig? {
    val resources = ConfigLoader.javaClass.getResource("/config") ?: return null
    val path = resources.path
    val files = File(path).listFiles()?.filter { it.extension == "yaml" } ?: return null

    val passThrough = mutableListOf<SerializedRule.PassThrough>()
    files.forEach { file ->
        file.inputStream().use {
            val config = loadSerializedTaintConfig(it)
            passThrough.addAll(config.passThrough.orEmpty())
        }
    }

    return SerializedTaintConfig(passThrough = passThrough)
}
