package org.opentaint.ir.impl

import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.api.CompilationDatabase
import org.opentaint.ir.impl.reader.readClasses
import org.opentaint.ir.impl.tree.ClassTree
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CompilationDatabaseImpl : CompilationDatabase {

    private val classTree = ClassTree()

    override suspend fun classpathSet(locations: List<File>): ClasspathSet {
        load(locations)
        return ClasspathSetImpl(locations.map { it.byteCodeLocation }.toPersistentList(), classTree)
    }

    override suspend fun load(dirOrJar: File) = apply {
        load(listOf(dirOrJar))
    }

    override suspend fun load(dirOrJars: List<File>) = apply {
        dirOrJars.forEach {
            if (!it.exists()) {
                throw IllegalStateException("file or folder does not exists: ${it.absolutePath}")
            }
        }
        withContext(Dispatchers.IO) {
            dirOrJars.map { dirOrJar ->
                launch(Dispatchers.IO) {
                    val location = dirOrJar.byteCodeLocation
                    location.readClasses().forEach {
                        classTree.addClass(location, it.name, it)
                    }
                }
            }
        }.joinAll()
    }

    override suspend fun refresh(): CompilationDatabase {
        TODO("Not yet implemented")
    }

    override fun watchFileSystemChanges(): CompilationDatabase {
        TODO("Not yet implemented")
    }

}