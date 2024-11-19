package org.opentaint.ir.impl.fs

import java.io.Closeable
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile


/**
 * workaround for java 9 feature for multi-release jars
 */
class JarFacade(private val runtimeVersion: Int, private val getter: () -> JarFile?) : Closeable {
    companion object {
        private const val META_INF = "META-INF/"
        private const val META_INF_VERSIONS = META_INF + "versions/"
        private val MULTI_RELEASE = Attributes.Name("Multi-Release")
    }

    private val jarFile = AtomicReference(getter())

    private val isJmod = jarFile.get()?.name?.endsWith(".jmod") ?: false

    private val entries = jarFile.get()?.entries()?.toList()?.filter {
        it.name.endsWith(".class") && !it.name.contains("module-info")
    }?.associateBy { it.name }

    private val isMultiReleaseEnabledInManifest by lazy(LazyThreadSafetyMode.NONE) {
        jarFile.get()?.manifest?.mainAttributes?.getValue(MULTI_RELEASE).toBoolean()
    }

    private val isMultiRelease: Boolean get() = runtimeVersion >= 9 && !isJmod && isMultiReleaseEnabledInManifest

    val classes: Map<String, JarEntry> by lazy(LazyThreadSafetyMode.NONE) {
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

    fun inputStreamOf(className: String): InputStream? {
        return classes[className]?.let {
            getter()?.getInputStream(it) // let's use new instance  `
        }
    }

    fun inputStreamOf(entry: JarEntry): InputStream? {
        return jarFile.get()?.getInputStream(entry)
    }

    override fun close() {
        val oldFile = jarFile.get()
        jarFile.set(getter())
        oldFile?.close()
    }
}