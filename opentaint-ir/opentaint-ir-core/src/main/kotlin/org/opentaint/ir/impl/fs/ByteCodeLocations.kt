package org.opentaint.ir.impl.fs

import mu.KLogging
import org.opentaint.ir.api.ByteCodeLocation
import java.io.File

val logger = object : KLogging() {}.logger

fun File.asByteCodeLocation(loadClassesOnlyFrom: List<String>? = null, isRuntime: Boolean = false): ByteCodeLocation {
    if (!exists()) {
        throw IllegalArgumentException("file $absolutePath doesn't exist")
    }
    if (isFile && name.endsWith(".jar")) {
        return JarLocation(this, isRuntime)
    } else if (isFile && name.endsWith(".jmod")) {
        return JmodLocation(this)
    } else if (!isFile) {
        return BuildFolderLocation(this)
    }
    throw IllegalArgumentException("file $absolutePath is not jar-file nor build dir folder")
}

fun List<File>.filterExisted(): List<File> = filter { file ->
    file.exists().also {
        if (!it) {
            logger.warn("${file.absolutePath} doesn't exists. make sure there is no mistake")
        }
    }
}

fun String.matchesOneOf(loadClassesOnlyFrom: List<String>?): Boolean {
    loadClassesOnlyFrom ?: return true
    return loadClassesOnlyFrom.any {
        startsWith(it)
    }
}