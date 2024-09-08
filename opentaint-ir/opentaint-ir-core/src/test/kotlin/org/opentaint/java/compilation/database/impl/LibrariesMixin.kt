package org.opentaint.java.compilation.database.impl

import org.junit.jupiter.api.Assertions
import java.io.File
import java.net.URLClassLoader

interface LibrariesMixin {

    val allClasspath: List<File>
        get() {
            val cl = ClassLoader.getSystemClassLoader()
            return (cl as URLClassLoader).urLs.map { File(it.file) }
        }

    val guavaLib: File
        get() {
            val cl = ClassLoader.getSystemClassLoader()
            val guavaUrl = (cl as URLClassLoader).urLs.first { it.path.contains("guava-31.1-jre.jar") }
            return File(guavaUrl.file).also {
                Assertions.assertTrue(it.isFile && it.exists())
            }
        }

    val allJars: List<File>
        get() {
            val cl = ClassLoader.getSystemClassLoader()
            val jars = (cl as URLClassLoader).urLs.filter { it.path.endsWith(".jar") }
            return jars.map { File(it.file) }
        }


}