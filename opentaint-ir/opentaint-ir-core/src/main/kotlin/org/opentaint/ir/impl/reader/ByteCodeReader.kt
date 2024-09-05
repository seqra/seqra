package org.opentaint.ir.impl.reader

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
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

    suspend fun readJar(file: File): Flow<ClassMetaInfo> {
        val (jar, classes) = file.extractClasses
        return classes.asFlow().map { ClassByteCodeReader(jar, it).readClassMeta() }
    }
}

fun main() {
    val kotlin = "C:\\Users\\a00663039\\.m2\\repository\\org\\jetbrains\\kotlin\\kotlin-stdlib\\1.6.10\\kotlin-stdlib-1.6.10.jar"
    runBlocking {
        ByteCodeReader().readJar(File(kotlin)).toList()
    }
}