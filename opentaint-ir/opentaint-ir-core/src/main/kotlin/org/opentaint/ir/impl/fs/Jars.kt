package org.opentaint.ir.impl.fs

import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * workaround for java 9 feature for multi-release jars
 */
class JarFacade(private val runtimeVersion: Int, private val getter: () -> JarFile?)  {
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
            entries = jarFile?.entries()?.toList()?.filter {
                it.name.endsWith(".class") && !it.name.contains("module-info")
            }?.associateBy { it.name }
        }
    }

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

    fun inputStreamOf(className: String): ByteArray? {
        return classes[className]?.let { entry ->
            getter()?.use { it.getInputStream(entry).use { it.readBytes() } } // let's use new instance always
        }
    }

    val bytecode: Map<String, ByteArray>?
        get() {
            val jarFile = getter() ?: return null
            return jarFile.use {
                classes.map { it.key to jarFile.getInputStream(it.value).readBytes() }
            }.toMap()
        }

}