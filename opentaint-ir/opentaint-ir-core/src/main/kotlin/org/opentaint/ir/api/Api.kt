package org.opentaint.ir.api

import org.opentaint.ir.ApiLevel
import org.opentaint.ir.impl.fs.ByteCodeLoader
import org.objectweb.asm.tree.MethodNode
import java.io.Closeable
import java.io.File
import java.io.InputStream

interface Accessible {
    suspend fun access(): Int

}

interface ClassId : Accessible {

    val location: ByteCodeLocation?

    val name: String
    val simpleName: String

    suspend fun methods(): List<MethodId>

    suspend fun superclass(): ClassId?
    suspend fun interfaces(): List<ClassId>
    suspend fun annotations(): List<ClassId>
    suspend fun fields(): List<FieldId>

}

interface ByteCodeLocation {

    val apiLevel: ApiLevel
    val version: String
    val currentVersion: String

    fun isChanged(): Boolean {
        return version != currentVersion
    }

    suspend fun resolve(classFullName: String): InputStream?

    suspend fun loader(): ByteCodeLoader
}

interface MethodId : Accessible {
    val name: String

    val classId: ClassId
    suspend fun returnType(): ClassId?
    suspend fun parameters(): List<ClassId>
    suspend fun annotations(): List<ClassId>

    suspend fun readBody(): MethodNode

}

interface FieldId : Accessible {

    val name: String

    suspend fun type(): ClassId?

}

interface ClasspathSet : Closeable {

    val locations: List<ByteCodeLocation>

    suspend fun findClassOrNull(name: String): ClassId?
    suspend fun findSubTypesOf(name: String): List<ClassId>
}


interface CompilationDatabase {

    suspend fun classpathSet(dirOrJars: List<File>): ClasspathSet

    suspend fun load(dirOrJar: File): CompilationDatabase
    suspend fun load(dirOrJars: List<File>): CompilationDatabase

    suspend fun refresh(): CompilationDatabase

    fun watchFileSystemChanges(): CompilationDatabase
}