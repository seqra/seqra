package org.opentaint.ir.api

import java.io.File

interface DatabaseClass {

    val location: ByteCodeLocation

    val name: String
    val simpleName: String

    val methods: List<DatabaseClassMethod>

    val parents: List<DatabaseClass>
    val interfaces: List<DatabaseClass>
    val annotations: List<DatabaseClass>
}

interface ByteCodeLocation {

    var isJar: Boolean

    val file: File
}

interface DatabaseClassMethod {

    val name: String
    val signature: Any // todo return something meaning full

    val annotations: List<DatabaseClass>
    suspend fun readBody(): Any //TODO return something

}

interface ClasspathSet {

    val locations: List<ByteCodeLocation>

    suspend fun findClass(name: String): DatabaseClass?
}


interface CompilationDatabase {

    suspend fun classpathSet(locations: List<File>): ClasspathSet

    suspend fun load(dirOrJar: File)
    suspend fun load(dirOrJars: List<File>)

    suspend fun refresh()

    fun watchFileSystemChanges()
}