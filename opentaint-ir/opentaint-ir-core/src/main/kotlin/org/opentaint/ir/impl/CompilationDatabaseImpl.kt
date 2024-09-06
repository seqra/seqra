package org.opentaint.ir.impl

import org.opentaint.ir.ApiLevel
import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.api.CompilationDatabase
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.fs.filterExisted
import org.opentaint.ir.impl.fs.sources
import org.opentaint.ir.impl.tree.ClassTree
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.KLogging
import java.io.File

class CompilationDatabaseImpl(private val apiLevel: ApiLevel) : CompilationDatabase {

    companion object : KLogging()

    private val classTree = ClassTree()

    override suspend fun classpathSet(locations: List<File>): ClasspathSet {
        val existedLocations = locations.filterExisted()
        load(existedLocations)
        return ClasspathSetImpl(existedLocations.map { it.asByteCodeLocation(apiLevel) }.toPersistentList(), classTree)
    }

    override suspend fun load(dirOrJar: File) = apply {
        load(listOf(dirOrJar))
    }

    override suspend fun load(dirOrJars: List<File>) = apply {
        val validSources = dirOrJars.filterExisted()
        withContext(Dispatchers.IO) {
            validSources.map { dirOrJar ->
                launch(Dispatchers.IO) {
                    val location = dirOrJar.asByteCodeLocation(apiLevel)
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