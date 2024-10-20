package org.opentaint.ir.impl.fs

import mu.KLogging
import org.opentaint.ir.api.ByteCodeLoader
import org.opentaint.ir.api.LocationScope
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import kotlin.streams.toList

open class JarLocation(file: File, private val isRuntime: Boolean) : AbstractByteCodeLocation(file) {
    companion object : KLogging()

    override val scope: LocationScope
        get() = LocationScope.RUNTIME.takeIf { isRuntime } ?: LocationScope.RUNTIME

    override fun getCurrentId(): String {
        return file.absolutePath + file.lastModified()
    }

    override fun createRefreshed() = JarLocation(file, isRuntime)

    override suspend fun loader(): ByteCodeLoader? {
        try {
            val content = jarWithClasses ?: return null
            val jar = content.jar
            val classes = content.classes.mapValues { (_, entry) ->
                jar.getInputStream(entry)
            }
            val allClasses = ClassLoadingContainerImpl(classes) {
                jar.close()
            }
            return ByteCodeLoaderImpl(this, allClasses)
        } catch (e: Exception) {
            logger.warn(e) { "error loading classes from jar: ${file.absolutePath}. returning empty loader" }
            return null
        }
    }

    override suspend fun resolve(classFullName: String): InputStream? {
        val jar = jarFile ?: return null
        val jarEntryName = classFullName.replace(".", "/") + ".class"
        val jarEntry = jar.getJarEntry(jarEntryName)
        return object : BufferedInputStream(jar.getInputStream(jarEntry)) {
            override fun close() {
                super.close()
                jar.close()
            }
        }
    }

    protected open val jarWithClasses: JarWithClasses?
        get() {
            val jar = jarFile ?: return null
            return JarWithClasses(
                jar = jar,
                classes = jar.stream().filter { it.name.endsWith(".class") && !it.name.contains("module-info") }.map {
                    val className = it.name.removeSuffix(".class").replace("/", ".")
                    className to it
                }.toList().toMap()
            )
        }

    private val jarFile: JarFile?
        get() {
            if (!file.exists() || !file.isFile) {
                return null
            }

            try {
                return JarFile(file)
            } catch (e: Exception) {
                logger.warn(e) { "error processing jar ${file.absolutePath}" }
                return null
            }
        }

    override fun toString(): String = file.absolutePath

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JarLocation) {
            return false
        }
        return other.file == file
    }

    override fun hashCode(): Int {
        return file.hashCode()
    }
}

class JarWithClasses(
    val jar: JarFile,
    val classes: Map<String, JarEntry>
)