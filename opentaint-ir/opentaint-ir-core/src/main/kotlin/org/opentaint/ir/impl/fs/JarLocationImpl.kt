package org.opentaint.ir.impl.fs

import mu.KLogging
import org.opentaint.ir.api.LocationType
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile
import kotlin.streams.toList

open class JarLocation(file: File, private val isRuntime: Boolean) : AbstractByteCodeLocation(file) {

    protected inner class JarWithClasses(
        val jar: JarFile,
        val classes: Map<String, JarEntry>
    )

    companion object : KLogging()

    override val hash by lazy { fileChecksum }

    override val type: LocationType
        get() = when {
            isRuntime -> LocationType.RUNTIME
            else -> LocationType.APP
        }

    override fun createRefreshed() = JarLocation(jarOrFolder, isRuntime)

    override fun currentHash() = fileChecksum

    override val classes: Map<String, ByteArray>?
        get() {
            try {
                val content = jarWithClasses ?: return null
                val jar = content.jar
                return content.classes.mapValues { (_, entry) ->
                    jar.getInputStream(entry).readBytes()
                }.also {
                    jar.close()
                }
            } catch (e: Exception) {
                logger.warn(e) { "error loading classes from jar: ${jarOrFolder.absolutePath}. returning empty loader" }
                return null
            }
        }

    override val classNames: Set<String>?
        get() = jarFile?.entries()?.toList()?.mapNotNull { it.className }?.toSet()

    override fun resolve(classFullName: String): ByteArray? {
        val jar = jarFile ?: return null
        val jarEntryName = classFullName.replace(".", "/") + ".class"
        val jarEntry = jar.getJarEntry(jarEntryName)
        return jar.use {
            it.getInputStream(jarEntry).readBytes()
        }
    }

    protected open val jarWithClasses: JarWithClasses?
        get() {
            val jar = jarFile ?: return null
            val classesMap = jar.stream().map { entry ->
                entry?.className?.let { it to entry }
            }.toList().filterNotNull().toMap()
            return JarWithClasses(
                jar = jar,
                classes = classesMap
            )
        }

    private val JarEntry.className: String?
        get() {
            val name = this.name
            if (name.endsWith(".class") && !name.contains("module-info")) {
                return name.removeSuffix(".class").replace("/", ".")
            }
            return null
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
            }
            return null
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
            return jarOrFolder.let {
                it.absolutePath + it.lastModified() + it.length()
            }
//            val buffer = ByteArray(8192)
//            var count: Int
//            val digest = MessageDigest.getInstance("SHA-256")
//            val bis = BufferedInputStream(FileInputStream(jarOrFolder))
//            while (bis.read(buffer).also { count = it } > 0) {
//                digest.update(buffer, 0, count)
//            }
//            bis.close()
//            return Base64.getEncoder().encodeToString(digest.digest())
        }

}