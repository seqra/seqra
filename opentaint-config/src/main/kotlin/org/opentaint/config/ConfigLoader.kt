package org.opentaint.config

import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.loadSerializedTaintConfig
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.Collections
import kotlin.streams.asSequence

object ConfigLoader {
    private const val CONFIG_ROOT = "/config"
    private val config = lazy { loadConfig() }

    fun getConfig() = config.value

    private fun loadConfig(): SerializedTaintConfig? {
        val resources = javaClass.getResource(CONFIG_ROOT) ?: return null
        val uri = resources.toURI()

        // it is expected to be used as a .jar-dependency
        if (uri.scheme != "jar") return null

        val allFiles =
            FileSystems.newFileSystem(uri, Collections.emptyMap<String, String>()).use { fs ->
                val path = fs.getPath(CONFIG_ROOT)
                Files.walk(path).asSequence().map { path.relativize(it).toString() }.toList()
            }
        if (allFiles.isEmpty()) return null
        val files = allFiles.filter { it.endsWith(".yaml") }

        val passThrough = mutableListOf<SerializedRule.PassThrough>()
        files.forEach { file ->
            javaClass.getResourceAsStream("$CONFIG_ROOT${File.separator}$file").use {
                if (it == null) return null
                else {
                    val config = loadSerializedTaintConfig(it)
                    passThrough.addAll(config.passThrough.orEmpty())
                }
            }
        }

        return SerializedTaintConfig(passThrough = passThrough)
    }
}
