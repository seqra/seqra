package org.opentaint.ir.impl

import kotlinx.coroutines.*
import mu.KLogging
import org.opentaint.ir.CompilationDatabaseSettings
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.api.CompilationDatabase
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.fs.filterExisted
import org.opentaint.ir.impl.fs.load
import org.opentaint.ir.impl.index.SubClassIndex
import org.opentaint.ir.impl.tree.ClassTree
import org.opentaint.ir.impl.tree.RemoveLocationsVisitor
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

object BackgroundScope : CoroutineScope {
    override val coroutineContext = Dispatchers.IO + SupervisorJob()
}

class CompilationDatabaseImpl(private val settings: CompilationDatabaseSettings) : CompilationDatabase {
    companion object : KLogging()

    private val classTree = ClassTree()
    internal val javaRuntime = JavaRuntime(settings.jre)

    internal val indexesRegistry = IndexesRegistry(listOf(SubClassIndex) + settings.additionalIndexes)
    internal val registry = LocationsRegistry(indexesRegistry)

    private val backgroundJobs: Queue<Job> = ConcurrentLinkedQueue()

    private val isClosed = AtomicBoolean()

    suspend fun loadJavaLibraries() {
        assertNotClosed()
        javaRuntime.allLocations.loadAll()
    }

    override suspend fun classpathSet(dirOrJars: List<File>): ClasspathSet {
        assertNotClosed()
        val existedLocations = dirOrJars.filterExisted().map { it.asByteCodeLocation() }.also {
            it.loadAll()
        }
        val classpathSetLocations = existedLocations.toList() + javaRuntime.allLocations
        return ClasspathSetImpl(
            registry.snapshot(classpathSetLocations),
            indexesRegistry,
            this,
            classTree
        )
    }

    fun classpathSet(locations: List<ByteCodeLocation>): ClasspathSet {
        assertNotClosed()
        val classpathSetLocations = locations.toSet() + javaRuntime.allLocations
        return ClasspathSetImpl(
            registry.snapshot(classpathSetLocations.toList()),
            indexesRegistry,
            this,
            classTree
        )
    }

    override suspend fun load(dirOrJar: File) = apply {
        assertNotClosed()
        load(listOf(dirOrJar))
    }

    override suspend fun load(dirOrJars: List<File>) = apply {
        assertNotClosed()
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
        val locationClasses = libraryTrees.map {
            it.location to it.pushInto(classTree).values
        }.toMap()

        backgroundJobs.add(BackgroundScope.launch {
            actions.map { (location, action) ->
                async {
                    action()
                    val addedClasses = locationClasses[location]
                    if (addedClasses != null) {
                        indexesRegistry.index(location, addedClasses)
                    }
                }
            }.joinAll()
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

    suspend override fun awaitBackgroundJobs() {
        backgroundJobs.toList().joinAll()
    }

    override fun close() {
        isClosed.set(true)
        registry.close()
    }

    private fun assertNotClosed() {
        if (isClosed.get()) {
            throw IllegalStateException("Database is already closed")
        }
    }
}