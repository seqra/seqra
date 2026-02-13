package org.opentaint.ir.impl.fs

import org.opentaint.ir.api.jvm.JIRByteCodeLocation
import org.opentaint.ir.api.jvm.JavaVersion
import java.io.File
import java.nio.file.Paths

class JavaRuntime(private val javaHome: File) {

    val version: JavaVersion = try {
        val releaseFile = when {
            javaHome.endsWith("jre") -> File(javaHome.parentFile, "release")
            // this is jre folder inside jdk
            else -> File(javaHome, "release")

        }
        val javaVersion = releaseFile.readLines().first { it.startsWith("JAVA_VERSION=") }
        parseRuntimeVersion(javaVersion.substring("JAVA_VERSION=".length + 1, javaVersion.length - 1))
    } catch (e: Exception) {
        logger.info("Can't find or parse 'release' file inside java runtime folder. Use 8 java version for this runtime.")
        parseRuntimeVersion("1.8.0")
    }

    val allLocations: List<JIRByteCodeLocation> = findRuntimeLocations()

    private fun findRuntimeLocations(): List<JIRByteCodeLocation> {
        val modulesPath = javaHome.resolve("lib/modules")
        if (modulesPath.exists()) {
            val modulesLocation = JavaRuntimeModuleLocation.loadModules(javaHome)
            if (modulesLocation.isNotEmpty()) return modulesLocation

            val modules = locations("jmods")
            if (modules.isEmpty()) {
                logger.warn("Can't load JDK modules")
            }
        }

        modules.takeIf { it.isNotEmpty() }?.let { return it }

        return bootstrapJars + extJars
    }

    private val modules: List<JIRByteCodeLocation> get() = locations("jmods")

    private val bootstrapJars: List<JIRByteCodeLocation>
        get() {
            return when (isJDK) {
                true -> locations("jre", "lib")
                else -> locations("lib")
            }
        }

    private val extJars: List<JIRByteCodeLocation>
        get() {
            return when (isJDK) {
                true -> locations("jre", "lib", "ext")
                else -> locations("lib", "ext")
            }
        }

    private val isJDK: Boolean get() = !javaHome.endsWith("jre")

    private fun locations(vararg subFolders: String): List<JIRByteCodeLocation> {
        return Paths.get(javaHome.toPath().toString(), *subFolders).toFile()
            .listFiles { file -> file.name.endsWith(".jar") || file.name.endsWith(".jmod") }
            .orEmpty()
            .toList()
            .flatMap { it.dirOrJarAsBytecodeLocation(version, true) }
            .distinct()
    }
}
