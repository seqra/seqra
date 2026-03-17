package org.opentaint.ir.testing

import java.net.URL
import java.nio.file.Files
import java.nio.file.Files.createDirectories
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory

fun cookJar(link: String): Path {
    val url = URL(link)
    val result = createTempJar(url.file)
    Files.copy(url.openStream(), result, StandardCopyOption.REPLACE_EXISTING)
    return result
}

fun createTempJar(name: String) =
    Path(createTempDirectory("jIRdb-temp-jar").toString(), name).also {
        createDirectories(it.parent)
    }