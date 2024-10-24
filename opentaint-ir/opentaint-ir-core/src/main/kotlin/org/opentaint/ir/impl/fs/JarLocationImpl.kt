package org.opentaint.ir.impl.fs

import mu.KLogging
import org.opentaint.ir.api.ClassLoadingContainer
import org.opentaint.ir.api.LocationType
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile
import kotlin.streams.toList

open class JarLocation(file: File, private val isRuntime: Boolean) : AbstractByteCodeLocation(file) {
    companion object : KLogging()

    override val hash by lazy { fileChecksum }

    override val type: LocationType
        get() = LocationType.RUNTIME.takeIf { isRuntime } ?: LocationType.RUNTIME

    override fun createRefreshed() = JarLocation(jarOrFolder, isRuntime)

    override fun currentHash() = fileChecksum

    override suspend fun classes(): ClassLoadingContainer? {
        try {
            val content = jarWithClasses ?: return null
            val jar = content.jar
            val classes = content.classes.mapValues { (_, entry) ->
                jar.getInputStream(entry)
            }
            return ClassLoadingContainerImpl(classes) {
                jar.close()
            }
        } catch (e: Exception) {
            logger.warn(e) { "error loading classes from jar: ${jarOrFolder.absolutePath}. returning empty loader" }
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
            if (!jarOrFolder.exists() || !jarOrFolder.isFile) {
                return null
            }

            try {
                return JarFile(jarOrFolder)
            } catch (e: Exception) {
                logger.warn(e) { "error processing jar ${jarOrFolder.absolutePath}" }
                return null
            }
        }

    override fun toString(): String = jarOrFolder.absolutePath

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JarLocation) {
            return false
        }
        return other.jarOrFolder == jarOrFolder
    }

    override fun hashCode(): Int {
        return jarOrFolder.hashCode()
    }

    private val fileChecksum: String
        get() {
            val buffer = ByteArray(8192)
            var count: Int
            val digest = MessageDigest.getInstance("SHA-256")
            val bis = BufferedInputStream(FileInputStream(jarOrFolder))
            while (bis.read(buffer).also { count = it } > 0) {
                digest.update(buffer, 0, count)
            }
            bis.close()
            return Base64.getEncoder().encodeToString(digest.digest())
        }

}

class JarWithClasses(
    val jar: JarFile,
    val classes: Map<String, JarEntry>
)