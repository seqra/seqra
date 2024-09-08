package org.opentaint.java.compilation.database.impl.fs

import org.opentaint.java.compilation.database.ApiLevel
import org.opentaint.java.compilation.database.api.ByteCodeLocation
import java.io.File
import java.nio.file.Paths

class JavaRuntime(val apiLevel: ApiLevel, private val javaHome: File) {

    companion object {
        private val loadedPackages = listOf("java.", "javax.")
    }

    val allLocations: List<ByteCodeLocation> = bootstrapJars + extJars

    private val bootstrapJars: List<ByteCodeLocation>
        get() {
            return when (isJDK) {
                true -> locations("jre", "lib")
                else -> locations("lib")
            }
        }

    private val extJars: List<ByteCodeLocation>
        get() {
            return when (isJDK) {
                true -> locations("jre", "lib", "ext")
                else -> locations("lib", "ext")
            }
        }

    private val isJDK: Boolean get() = !javaHome.endsWith("jre")

    private fun locations(vararg subFolders: String): List<ByteCodeLocation> {
        return Paths.get(javaHome.toPath().toString(), *subFolders).toFile()
            .listFiles { file -> file.name.endsWith(".jar") }
            .orEmpty()
            .toList()
            .map { it.asByteCodeLocation(apiLevel, loadedPackages) }
    }

}
