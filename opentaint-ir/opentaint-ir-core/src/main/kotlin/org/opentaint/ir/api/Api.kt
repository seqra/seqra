package org.opentaint.ir.api

import java.io.Closeable
import java.io.File

interface ClassId {

    val location: ByteCodeLocation

    val name: String
    val simpleName: String

    suspend fun methods(): List<MethodId>

    suspend fun superclass(): ClassId?
    suspend fun interfaces(): List<ClassId>
    suspend fun annotations(): List<ClassId>

}

interface ByteCodeLocation {

    val isJar: Boolean

    val file: File
}

interface MethodId {
    val name: String

    val classId: ClassId
    suspend fun returnType(): ClassId?
    suspend fun parameters(): List<ClassId>
    suspend fun annotations(): List<ClassId>

    suspend fun readBody(): Any //TODO return something

}

interface ClasspathSet: Closeable {

    val locations: List<ByteCodeLocation>

    suspend fun findClass(name: String): ClassId?
}


interface CompilationDatabase {

    suspend fun classpathSet(locations: List<File>): ClasspathSet

    suspend fun load(dirOrJar: File): CompilationDatabase
    suspend fun load(dirOrJars: List<File>): CompilationDatabase

    suspend fun refresh(): CompilationDatabase

    fun watchFileSystemChanges(): CompilationDatabase
}