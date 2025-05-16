package org.opentaint.ir.impl.fs

import mu.KLogging
import org.opentaint.ir.api.jvm.JavaVersion
import org.opentaint.ir.api.jvm.LocationType
import java.io.File
import java.util.jar.JarFile

open class JarLocation(
    file: File,
    private val isRuntime: Boolean,
    private val runtimeVersion: JavaVersion
) : AbstractByteCodeLocation(file) {

    companion object : KLogging()

    override val fileSystemId by lazy { fileChecksum }

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
                return jarFacade.bytecode
            } catch (e: Exception) {
                logger.warn(e) { "error loading classes from jar: ${jarOrFolder.absolutePath}. returning empty loader" }
                return null
            }
        }

    override val classNames: Set<String>?
        get() = jarFacade.classes.keys

    override fun resolve(classFullName: String): ByteArray? {
        return jarFacade.inputStreamOf(classFullName)
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
            }.shaHash
        }

}
