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
import org.opentaint.java.compilation.database.impl.fs.load
import org.opentaint.java.compilation.database.impl.tree.ClassTree
import org.opentaint.java.compilation.database.impl.tree.RemoveLocationsVisitor
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
    internal val javaRuntime = JavaRuntime(settings.jre)

    internal val registry = LocationsRegistry()

    private val backgroundJobs: Queue<Job> = ConcurrentLinkedQueue()

    suspend fun loadJavaLibraries() {
        javaRuntime.allLocations.loadAll()
    }

    override suspend fun classpathSet(dirOrJars: List<File>): ClasspathSet {
        val existedLocations = dirOrJars.filterExisted().map { it.asByteCodeLocation() }.also {
            it.loadAll()
        }
        return ClasspathSetImpl(
            registry.snapshot(existedLocations.toList() + javaRuntime.allLocations),
            this,
            classTree
        )
    }

    override suspend fun load(dirOrJar: File) = apply {
        load(listOf(dirOrJar))
    }

    override suspend fun load(dirOrJars: List<File>) = apply {
        dirOrJars.filterExisted().map { it.asByteCodeLocation() }.loadAll()
    }

    private suspend fun List<ByteCodeLocation>.loadAll() = apply {
        val actions = ConcurrentLinkedQueue<Pair<ByteCodeLocation, suspend () -> Unit>>()

        val libraryTrees = withContext(Dispatchers.IO) {
            map { location ->
                async {
                    val loader = location.loader()
                    // here something may go wrong
                    if (loader != null) {
                        val (libraryTree, asyncJob) = loader.load(classTree)
                        actions.add(location to asyncJob)
                        registry.addLocation(location)
                        libraryTree
                    } else {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
        val addedClasses = libraryTrees.flatMap {
            it.pushInto(classTree).values
        }

        backgroundJobs.add(BackgroundScope.launch {
            actions.map { (_, action) ->
                async {
                    action()
                }
            }.joinAll()
            addedClasses.forEach {
                classTree.notifyOnByteCodeLoaded(it)
            }
        })
    }

    override suspend fun refresh() {
        awaitBackgroundJobs()
        registry.refresh {
            listOf(it).loadAll()
        }
        val outdatedLocations = registry.cleanup()
        classTree.visit(RemoveLocationsVisitor(outdatedLocations))
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