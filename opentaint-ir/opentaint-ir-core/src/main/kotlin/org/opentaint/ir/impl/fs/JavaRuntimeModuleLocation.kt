package org.opentaint.ir.impl.fs

import com.google.common.hash.Hashing
import org.opentaint.ir.api.jvm.JIRByteCodeLocation
import org.opentaint.ir.api.jvm.LocationType
import java.io.File
import java.math.BigInteger
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.walk
import kotlin.text.Charsets.UTF_8

open class JavaRuntimeModuleLocation(
    val javaHome: File,
    val module: String,
) : AbstractByteCodeLocation() {

    override val path: String get() = createPath(javaHome.absolutePath, module)

    override val type: LocationType get() = LocationType.RUNTIME

    @Suppress("UnstableApiUsage")
    override val currentHash: BigInteger
        get() {
            val hasher = Hashing.sha256().newHasher()
            hasher.putString(module, UTF_8)
            useModule { moduleBase, moduleBasePath ->
                moduleBase.walk()
                    .filter { it.isValidClassFile() }
                    .sortedBy { it.toString() }
                    .forEach { classFile ->
                        val classFileName = classFile.classFileName(moduleBasePath)
                        hasher.putString(classFileName, UTF_8)
                        hasher.putLong(classFile.fileSize())
                    }
            }
            return BigInteger(hasher.hash().asBytes())
        }

    override val classNames: Set<String>?
        get() = useModule { moduleBase, moduleBasePath ->
            moduleBase.walk()
                .filter { it.isValidClassFile() }
                .mapTo(hashSetOf()) { it.className(moduleBasePath) }
        }

    override val classes: Map<String, ByteArray>
        get() = useModule { moduleBase, moduleBasePath ->
            moduleBase.walk()
                .filter { it.isValidClassFile() }
                .associateTo(hashMapOf()) { classFile ->
                    val className = classFile.className(moduleBasePath)
                    val classBytes = classFile.readBytes()
                    className to classBytes
                }
        }

    override fun resolve(classFullName: String): ByteArray? {
        val classFilePath = classFullName.replace('.', '/') + ".class"
        useModule { moduleBase, _ ->
            val classFile = moduleBase.resolve(classFilePath)
            if (!classFile.exists()) return null
            return classFile.readBytes()
        }
    }

    override fun createRefreshed(): JIRByteCodeLocation? = JavaRuntimeModuleLocation(javaHome, module)

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JavaRuntimeModuleLocation) {
            return false
        }
        return other.module == module && other.javaHome == javaHome
    }

    override fun hashCode(): Int {
        var result = javaHome.hashCode()
        result = 31 * result + module.hashCode()
        return result
    }

    private val fs by lazy {
        newFs(javaHome) ?: error("JRT file system not available")
    }

    private inline fun <T> useModule(body: (Path, String) -> T): T {
        val module = fs.getPath(MODULES, module)
        return body(module, "$module/")
    }

    companion object {
        private const val MODULES = "/modules"

        fun loadModules(javaHome: File): List<JavaRuntimeModuleLocation> =
            newFs(javaHome)?.use { fs ->
                val modulesDir = fs.getPath(MODULES)
                val modules = modulesDir.listDirectoryEntries()
                modules.map { JavaRuntimeModuleLocation(javaHome, it.name) }
            }.orEmpty()

        private fun newFs(javaHome: File): FileSystem? {
            val env = hashMapOf<String, Any>("java.home" to javaHome.absolutePath)

            return runCatching {
                FileSystems.newFileSystem(URI.create(JRT), env)
            }.getOrNull()
        }

        private const val JRT = "jrt:/"

        // note: use null symbol because it can't appear in any path
        private const val SEPARATOR = '\u0000'

        private const val PATH_PREFIX = "$JRT$SEPARATOR"

        fun isModuleLocation(path: String): Boolean = path.startsWith(PATH_PREFIX)

        fun fromPath(path: String): JavaRuntimeModuleLocation {
            val moduleHomeSeparatorPos = path.indexOf(SEPARATOR, startIndex = PATH_PREFIX.length)
            val module = path.substring(PATH_PREFIX.length + 1, moduleHomeSeparatorPos)
            val home = path.substring(moduleHomeSeparatorPos + 1)
            return JavaRuntimeModuleLocation(File(home), module)
        }

        private fun createPath(javaHome: String, module: String): String =
            "$PATH_PREFIX$module$SEPARATOR$javaHome"

        private fun Path.isValidClassFile(): Boolean =
            name.endsWith(".class") && !name.contains("module-info")

        private fun Path.classFileName(moduleBasePath: String): String =
            toString().removePrefix(moduleBasePath)

        private fun Path.className(moduleBasePath: String): String =
            classFileName(moduleBasePath).removeSuffix(".class").replace('/', '.')
    }
}
