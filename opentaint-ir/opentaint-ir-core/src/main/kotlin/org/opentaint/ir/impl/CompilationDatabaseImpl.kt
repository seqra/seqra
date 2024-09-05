package org.opentaint.ir.impl

import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.api.CompilationDatabase
import org.opentaint.ir.impl.tree.ClassTree
import kotlinx.collections.immutable.toPersistentList
import java.io.File

class CompilationDatabaseImpl : CompilationDatabase {

    private val classTree = ClassTree()

    override suspend fun classpathSet(locations: List<File>): ClasspathSet {
        load(locations)
        return ClasspathSetImpl(locations.map { ByteCodeLocationImpl(it) }.toPersistentList(), classTree)
    }

    override suspend fun load(dirOrJar: File) = with(this) {
        load(listOf(dirOrJar))
    }

    override suspend fun load(dirOrJars: List<File>): CompilationDatabase {
        TODO("Not yet implemented")
    }

    override suspend fun refresh(): CompilationDatabase {
        TODO("Not yet implemented")
    }

    override fun watchFileSystemChanges(): CompilationDatabase {
        TODO("Not yet implemented")
    }

}