package org.opentaint.ir.impl.fs

import jetbrains.exodus.util.LightByteArrayOutputStream
import org.opentaint.ir.impl.util.asSequence
import java.io.InputStream
import java.io.OutputStream
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile


/**
 * workaround for java 9 feature for multi-release jars
 */
class JarFacade(private val runtimeVersion: Int, private val getter: () -> JarFile?) {
    companion object {
        private const val META_INF = "META-INF/"
        private const val META_INF_VERSIONS = META_INF + "versions/"
        private val MULTI_RELEASE = Attributes.Name("Multi-Release")
    }

    private val isJmod: Boolean
    private val entries: Map<String, JarEntry>?
    private val isMultiReleaseEnabledInManifest: Boolean
    private val isMultiRelease: Boolean get() = runtimeVersion >= 9 && !isJmod && isMultiReleaseEnabledInManifest

    init {
        getter().use { jarFile ->
            isJmod = jarFile?.name?.endsWith(".jmod") ?: false
            isMultiReleaseEnabledInManifest = jarFile?.manifest?.mainAttributes?.getValue(MULTI_RELEASE).toBoolean()
            entries = jarFile?.entries()?.asSequence()?.filter {
                it.name.endsWith(".class") && !it.name.contains("module-info")
            }?.associate { it.name to JarEntry(it) }
        }
    }

    val classes: Map<String, JarEntry> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val result = entries.orEmpty().toMutableMap()
        if (isMultiRelease) {
            var version = 9
            while (version <= runtimeVersion) {
                val prefix = "$META_INF_VERSIONS${version}/"
                val prefixSize = prefix.length
                val specificEntries = entries.orEmpty().filter { it.key.startsWith(prefix) }
                result.putAll(specificEntries.mapKeys { it.key.drop(prefixSize) })
                version++
            }
        }
        result.filterKeys { !it.contains(META_INF) }
            .mapKeys { it.key.className }
    }

    private val String.className: String
        get() {
            val name = when {
                isJmod -> removePrefix("classes/")
                else -> this
            }
            return name.removeSuffix(".class").replace("/", ".")
        }

    fun inputStreamOf(className: String): ByteArray? {
        return classes[className]?.let { entry ->
            getter()?.use { it.getInputStream(entry).use { it.readBytes() } } // let's use new instance always
        }
    }

    val bytecode: Map<String, ByteArray>
        get() {
            val jarFile = getter() ?: return emptyMap()
            return jarFile.use {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                classes.entries.associate { it.key to jarFile.getInputStream(it.value).use { it.readBytes(buffer) } }
            }
        }

}

private fun InputStream.readBytes(buffer: ByteArray): ByteArray {
    val tempOutput = LightByteArrayOutputStream(available())
    copyTo(tempOutput, buffer)
    return tempOutput.toByteArray()
}

private fun InputStream.copyTo(out: OutputStream, buffer: ByteArray): Long {
    var bytesCopied: Long = 0
    var bytes = read(buffer)
    while (bytes >= 0) {
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        bytes = read(buffer)
    }
    return bytesCopied
}