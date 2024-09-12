package org.opentaint.java.compilation.database.impl

import org.junit.jupiter.api.Assertions
import java.io.File

interface LibrariesMixin {

    val allClasspath: List<File>
        get() {
            return classpath.map { File(it) }
        }

    val guavaLib: File
        get() {
            val guavaUrl = classpath.first { it.contains("guava-31.1-jre.jar") }
            return File(guavaUrl).also {
                Assertions.assertTrue(it.isFile && it.exists())
            }
        }

    val allJars: List<File>
        get() {
            return classpath.filter { it.endsWith(".jar") }.map { File(it) }
        }


    private val classpath: List<String>
        get() {
            val classpath = System.getProperty("java.class.path")
            return classpath.split(File.pathSeparatorChar).toList()
        }

}