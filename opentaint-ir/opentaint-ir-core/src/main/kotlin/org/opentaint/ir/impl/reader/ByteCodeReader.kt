package org.opentaint.ir.impl.reader

import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile
import kotlin.streams.asSequence


class ByteCodeReader {

    private val File.extractClasses: Pair<JarFile, Sequence<JarEntry>>
        get() {
            val jar = JarFile(this)
            return jar to jar.stream().filter { it.name.endsWith(".class") }.asSequence()
        }

    fun readJar(file: File): Sequence<ClassMetaInfo> {
        val (jar, classes) = file.extractClasses
        return classes.map { ClassByteCodeReader(jar, it).readClassMeta() }
    }
}