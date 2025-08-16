package org.opentaint.ir.impl.fs

import mu.KLogging
import org.opentaint.ir.api.jvm.JavaVersion
import org.opentaint.ir.api.jvm.JIRByteCodeLocation
import java.io.File
import java.nio.file.Paths
import java.util.jar.JarFile

val logger = object : KLogging() {}.logger

/**
 * Returns collection of `JIRByteCodeLocation` of a file or directory.
 * Any jar file can have its own classpath defined in the manifest, that's why the method returns collection.
 * The method called of different files can have same locations in the result, so use `distinct()` to
 * filter duplicates out.
 */
fun File.asByteCodeLocation(runtimeVersion: JavaVersion, isRuntime: Boolean = false): Collection<JIRByteCodeLocation> {
    if (!exists()) {
        throw IllegalArgumentException("file $absolutePath doesn't exist")
    }
    if (isFile && name.endsWith(".jar") || name.endsWith(".jmod")) {
        return mutableSetOf<File>().also { classPath(it) }.map { JarLocation(it, isRuntime, runtimeVersion) }
    } else if (isDirectory) {
        return listOf(BuildFolderLocation(this))
    }
    error("$absolutePath is nether a jar file nor a build directory")
}

fun Collection<File>.filterExisting(): List<File> = filter { file ->
    file.exists().also {
        if (!it) {
            logger.warn("${file.absolutePath} doesn't exists. make sure there is no mistake")
        }
    }
}

private fun File.classPath(classpath: MutableCollection<File>) {
    if (exists() && classpath.add(this)) {
        JarFile(this).use { jarFile ->
            jarFile.manifest?.mainAttributes?.getValue("Class-Path")?.split(' ')?.forEach { ref ->
                Paths.get(
                    if (ref.startsWith("file:")) ref.substring("file:".length) else ref
                ).toFile().classPath(classpath)
            }
        }
    }
}