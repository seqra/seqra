package org.opentaint.ir.impl

import org.opentaint.ir.ApiLevel
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.api.CompilationDatabase
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.fs.filterExisted
import org.opentaint.ir.impl.fs.sources
import org.opentaint.ir.impl.tree.ClassTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.KLogging
import java.io.File

class CompilationDatabaseImpl(private val apiLevel: ApiLevel, javaLocation: File) : CompilationDatabase {
    companion object : KLogging()

    private val classTree = ClassTree()
    private val javaRuntime = JavaRuntime(apiLevel, javaLocation)

    suspend fun loadJavaLibraries() {
        javaRuntime.allLocations.loadAll()
    }

    override suspend fun classpathSet(dirOrJars: List<File>): ClasspathSet {
        val existedLocations = dirOrJars.filterExisted()
        load(existedLocations)
        return ClasspathSetImpl(existedLocations.map { it.asByteCodeLocation(apiLevel) }.toList() + javaRuntime.allLocations, classTree)
    }

    override suspend fun load(dirOrJar: File) = apply {
        load(listOf(dirOrJar))
    }

    override suspend fun load(dirOrJars: List<File>) = apply {
        dirOrJars.filterExisted().map { it.asByteCodeLocation(apiLevel) }.loadAll()
    }

    private suspend fun List<ByteCodeLocation>.loadAll() = this@CompilationDatabaseImpl.apply {
        withContext(Dispatchers.IO) {
            map { location ->
                launch(Dispatchers.IO) {
                    location.sources().forEach {
                        classTree.addClass(it)
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