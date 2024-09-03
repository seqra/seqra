package org.opentaint.ir.api

import java.io.File

interface ClassId {

    val location: ByteCodeLocation

    val name: String
    val simpleName: String

    val methods: List<MethodId>

    val parents: List<ClassId>
    val interfaces: List<ClassId>
    val annotations: List<ClassId>

}

interface ByteCodeLocation {

    var isJar: Boolean

    val file: File
}

interface MethodId {
    val name: String

    val classId: ClassId
    val returnType: ClassId
    val parameters: List<ClassId>
    val annotations: List<ClassId>

    suspend fun readBody(): Any //TODO return something

}

interface ClasspathSet {

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