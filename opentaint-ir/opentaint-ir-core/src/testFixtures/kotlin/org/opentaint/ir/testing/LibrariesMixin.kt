package org.opentaint.ir.testing

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.condition.JRE
import java.io.File

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
        return classpath.split(File.pathSeparatorChar)
            .filter { !it.contains("sootup") }
            .toList()
    }

inline fun skipAssertionsOn(jre: JRE, assertions: () -> Unit) {
    val currentVersion = JRE.currentVersion()
    if (currentVersion != jre) {
        assertions()
    }
}