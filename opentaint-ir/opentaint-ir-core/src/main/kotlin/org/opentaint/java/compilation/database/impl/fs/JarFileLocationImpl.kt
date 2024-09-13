package org.opentaint.java.compilation.database.impl.fs

import mu.KLogging
import org.opentaint.java.compilation.database.api.ByteCodeLoader
import org.opentaint.java.compilation.database.api.md5
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import kotlin.streams.toList

open class JarFileLocationImpl(
    protected val file: File,
    protected val syncLoadClassesOnlyFrom: List<String>?
) : AbstractByteCodeLocation() {
    companion object : KLogging()

    override fun getCurrentId(): String {
        return (file.absolutePath + file.lastModified()).md5()
    }

    override fun createRefreshed() = JarFileLocationImpl(file, syncLoadClassesOnlyFrom)

    override suspend fun loader(): ByteCodeLoader? {
        try {
            val content = jarWithClasses ?: return null
            val jar = content.jar
            val classes = content.classes.mapValues { (className, entry) ->
                when (className.matchesOneOf(syncLoadClassesOnlyFrom)) {
                    true -> jar.getInputStream(entry)
                    else -> null // lazy
                }
            }
            val allClasses = ClassLoadingContainerImpl(classes)
            return ByteCodeLoaderImpl(this, allClasses) {
                ClassLoadingContainerImpl(content.classes.filterKeys { className ->
                    !className.matchesOneOf(syncLoadClassesOnlyFrom)
                }.mapValues { (_, entry) ->
                    jar.getInputStream(entry)
                }) {
                    jar.close()
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "error loading classes from build folder: ${file.absolutePath}. returning empty loader" }
            return null
        }
    }

    override suspend fun resolve(classFullName: String): InputStream? {
        val jar = jarFile() ?: return null
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
            val jarFile = jarFile() ?: return null
            return JarWithClasses(
                jar = jarFile,
                classes = jarFile.stream().filter { it.name.endsWith(".class") }.map {
                    val className = it.name.removeSuffix(".class").replace("/", ".")
                    className to it
                }.toList().toMap()
            )
        }

    private fun jarFile(): JarFile? {
        if (!file.exists() || !file.isFile) {
            return null
        }

        try {
            return JarFile(file)
        } catch (e: Exception) {
            logger.warn(e) { "error processing jar located ${file.absolutePath}" }
            return null
        }
    }

    override fun toString(): String = file.absolutePath
}

class JarWithClasses(
    val jar: JarFile,
    val classes: Map<String, JarEntry>
)