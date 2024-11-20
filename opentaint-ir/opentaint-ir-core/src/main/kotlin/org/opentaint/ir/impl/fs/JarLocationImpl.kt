package org.opentaint.ir.impl.fs

import mu.KLogging
import org.opentaint.ir.api.JavaVersion
import org.opentaint.ir.api.LocationType
import java.io.File
import java.util.jar.JarFile

open class JarLocation(
    file: File,
    private val isRuntime: Boolean,
    private val runtimeVersion: JavaVersion
) : AbstractByteCodeLocation(file) {

    companion object : KLogging()

    override val fsId by lazy { fileChecksum }

    override val type: LocationType
        get() = when {
            isRuntime -> LocationType.RUNTIME
            else -> LocationType.APP
        }

    override fun createRefreshed() = JarLocation(jarOrFolder, isRuntime, runtimeVersion)

    override fun currentHash() = fileChecksum

    override val classes: Map<String, ByteArray>?
        get() {
            try {
                val classes = jarFacade.classes
                val result = hashMapOf<String, ByteArray>()
                classes.forEach { (name, entry) ->
                    val readBytes = jarFacade.inputStreamOf(entry)?.readBytes()
                    if (readBytes != null) {
                        result[name] = readBytes
                    }
                }.also {
                    jarFacade.close()
                }
                return result
            } catch (e: Exception) {
                logger.warn(e) { "error loading classes from jar: ${jarOrFolder.absolutePath}. returning empty loader" }
                return null
            }
        }

    override val classNames: Set<String>?
        get() = jarFacade.classes.keys

    override fun resolve(classFullName: String): ByteArray? {
        return jarFacade.use {
            it.inputStreamOf(classFullName)?.readBytes()
        }
    }

    protected open val jarFacade: JarFacade by lazy {
        JarFacade(runtimeVersion.majorVersion) {
            if (!jarOrFolder.exists() || !jarOrFolder.isFile) {
                null
            } else {
                try {
                    JarFile(jarOrFolder)
                } catch (e: Exception) {
                    logger.warn(e) { "error processing jar ${jarOrFolder.absolutePath}" }
                    null
                }
            }
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