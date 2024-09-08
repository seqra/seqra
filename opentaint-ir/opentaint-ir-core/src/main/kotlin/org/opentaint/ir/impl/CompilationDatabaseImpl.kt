package org.opentaint.ir.impl

import org.opentaint.ir.ApiLevel
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.api.CompilationDatabase
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.fs.filterExisted
import org.opentaint.ir.impl.tree.ClassTree
import org.opentaint.ir.impl.tree.SubTypesInstallationListener
import kotlinx.coroutines.*
import mu.KLogging
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

object BackgroundScope : CoroutineScope {
    override val coroutineContext = Dispatchers.IO + SupervisorJob()
}

class CompilationDatabaseImpl(private val apiLevel: ApiLevel, javaLocation: File) : CompilationDatabase {
    companion object : KLogging()

    private val classTree = ClassTree(listeners = listOf(SubTypesInstallationListener))
    private val javaRuntime = JavaRuntime(apiLevel, javaLocation)

    private val backgroundJobs: Queue<Job> = ConcurrentLinkedQueue()

    suspend fun loadJavaLibraries() {
        javaRuntime.allLocations.loadAll()
    }

    override suspend fun classpathSet(dirOrJars: List<File>): ClasspathSet {
        val existedLocations = dirOrJars.filterExisted().map { it.asByteCodeLocation(apiLevel) }.also {
            it.loadAll()
        }
        return ClasspathSetImpl(existedLocations.toList() + javaRuntime.allLocations, this, classTree)
    }

    override suspend fun load(dirOrJar: File) = apply {
        load(listOf(dirOrJar))
    }

    override suspend fun load(dirOrJars: List<File>) = apply {
        dirOrJars.filterExisted().map { it.asByteCodeLocation(apiLevel) }.loadAll()
    }

    private suspend fun List<ByteCodeLocation>.loadAll() = apply {
        val actions = ConcurrentLinkedQueue<suspend () -> Unit>()

        withContext(Dispatchers.IO) {
            map { location ->
                async {
                    val asyncJob = location.loader().load(classTree)
                    actions.add(asyncJob)
                }
            }
        }.joinAll()
        backgroundJobs.add(BackgroundScope.launch {
            actions.map { action ->
                async {
                    action()
                }
            }.joinAll()
        })
    }

    override suspend fun refresh(): CompilationDatabase {
        TODO("Not yet implemented")
    }

    override fun watchFileSystemChanges(): CompilationDatabase {
        TODO("Not yet implemented")
    }

    suspend fun awaitBackgroundJobs() {
        backgroundJobs.toList().joinAll()
    }
}