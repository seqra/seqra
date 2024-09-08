package org.opentaint.java.compilation.database.impl

import kotlinx.coroutines.*
import mu.KLogging
import org.opentaint.java.compilation.database.CompilationDatabaseSettings
import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.api.ClasspathSet
import org.opentaint.java.compilation.database.api.CompilationDatabase
import org.opentaint.java.compilation.database.impl.fs.JavaRuntime
import org.opentaint.java.compilation.database.impl.fs.asByteCodeLocation
import org.opentaint.java.compilation.database.impl.fs.filterExisted
import org.opentaint.java.compilation.database.impl.tree.ClassTree
import org.opentaint.java.compilation.database.impl.tree.SubTypesInstallationListener
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

object BackgroundScope : CoroutineScope {
    override val coroutineContext = Dispatchers.IO + SupervisorJob()
}

class CompilationDatabaseImpl(private val settings: CompilationDatabaseSettings) : CompilationDatabase {
    companion object : KLogging()

    private val classTree = ClassTree(listeners = listOf(SubTypesInstallationListener))
    private val javaRuntime = JavaRuntime(settings.apiLevel, settings.jre)

    private val apiLevel = settings.apiLevel

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
        awaitBackgroundJobs()
        return this
    }

    override fun watchFileSystemChanges(): CompilationDatabase {
        val delay = settings.watchFileSystemChanges?.delay
        if (delay != null) { // just paranoid check
            BackgroundScope.launch {
                while (true) {
                    delay(delay)
                    refresh()
                }
            }
        }
        return this
    }

    suspend fun awaitBackgroundJobs() {
        backgroundJobs.toList().joinAll()
    }
}