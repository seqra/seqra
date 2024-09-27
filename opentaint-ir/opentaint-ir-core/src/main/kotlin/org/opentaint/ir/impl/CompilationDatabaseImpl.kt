package org.opentaint.ir.impl

import kotlinx.coroutines.*
import mu.KLogging
import org.opentaint.ir.CompilationDatabaseSettings
import org.opentaint.ir.api.*
import org.opentaint.ir.impl.fs.*
import org.opentaint.ir.impl.storage.PersistentEnvironment
import org.opentaint.ir.impl.storage.scheme.LocationEntity
import org.opentaint.ir.impl.tree.ClassTree
import org.opentaint.ir.impl.tree.LibraryClassTree
import org.opentaint.ir.impl.tree.RemoveLocationsVisitor
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CompilationDatabaseImpl(
    private val persistentEnvironment: PersistentEnvironment? = null,
    private val settings: CompilationDatabaseSettings
) : CompilationDatabase {

    companion object : KLogging()

    private val classTree = ClassTree()
    internal val javaRuntime = JavaRuntime(settings.jre)

    internal val featureRegistry = FeaturesRegistry(persistentEnvironment, settings.fullFeatures)
    internal val locationsRegistry = LocationsRegistry(featureRegistry)
    private val backgroundJobs = ConcurrentHashMap<Int, Job>()

    private val isClosed = AtomicBoolean()
    private val jobId = AtomicInteger()

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
            locationsRegistry.snapshot(classpathSetLocations),
            featureRegistry,
            this,
            classTree
        )
    }

    fun classpathSet(locations: List<ByteCodeLocation>): ClasspathSet {
        assertNotClosed()
        val classpathSetLocations = locations.toSet() + javaRuntime.allLocations
        return ClasspathSetImpl(
            locationsRegistry.snapshot(classpathSetLocations.toList()),
            featureRegistry,
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

    override suspend fun loadLocations(locations: List<ByteCodeLocation>) = apply {
        assertNotClosed()
        locations.loadAll()
    }

    private suspend fun List<ByteCodeLocation>.loadAll() = apply {
        val actions = ConcurrentLinkedQueue<Pair<ByteCodeLocation, suspend () -> Unit>>()
        val locationStore = persistentEnvironment?.locationStore

        val libraryTrees = withContext(Dispatchers.IO) {
            map { location ->
                async {
                    val loader = location.loader()
                    // here something may go wrong
                    if (loader != null) {
                        val (libraryTree, asyncJob) = loader.load(classTree)
                        actions.add(location to asyncJob)
                        locationsRegistry.addLocation(location)
                        locationStore?.findOrNew(location)
                        libraryTree
                    } else {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
        persistentEnvironment?.databaseStore?.save(this@CompilationDatabaseImpl, false)

        val locationClasses = libraryTrees.map {
            it.location to it.pushInto(classTree).values
        }.toMap()
        val backgroundJobId = jobId.incrementAndGet()
        backgroundJobs[backgroundJobId] = BackgroundScope.launch {
            val parentScope = this
            actions.map { (location, action) ->
                async {
                    if (parentScope.isActive) {
                        action()
                    }
                    val addedClasses = locationClasses[location]
                    if (addedClasses != null) {
                        if (parentScope.isActive) {
                            locationStore?.saveClasses(location, addedClasses.map { it.info() })
                            featureRegistry.index(location, addedClasses)
                        }
                    }
                }
            }.joinAll()
            backgroundJobs.remove(backgroundJobId)
        }
    }

    override suspend fun refresh() {
        awaitBackgroundJobs()
        locationsRegistry.refresh {
            listOf(it).loadAll()
        }
        val outdatedLocations = locationsRegistry.cleanup()
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

    override suspend fun awaitBackgroundJobs() {
        backgroundJobs.values.toList().joinAll()
    }

    override fun close() {
        isClosed.set(true)
        locationsRegistry.close()
        backgroundJobs.values.forEach {
            it.cancel()
        }
        backgroundJobs.clear()
        persistentEnvironment?.close()
    }

    private fun assertNotClosed() {
        if (isClosed.get()) {
            throw IllegalStateException("Database is already closed")
        }
    }

    internal suspend fun restoreDataFrom(locations: Map<LocationEntity, ByteCodeLocation>) {
        val env = persistentEnvironment ?: return
        val trees = withContext(Dispatchers.IO) {
            locations.map { (entity, location) ->
                async {
                    env.transactional {
                        val libraryClasses = LibraryClassTree(location)
                        val classes = entity.classes
                        classes.forEach { libraryClasses.addClass(LazyByteCodeSource(location, it)) }
                        restoreIndexes(location, entity)
                        libraryClasses
                    }
                }
            }
        }.awaitAll()
        trees.forEach {
            it.pushInto(classTree)
        }
    }

    private fun restoreIndexes(location: ByteCodeLocation, entity: LocationEntity) {
        featureRegistry.features.forEach { it.restore(location, entity) }
    }

    private fun <T, INDEX : ByteCodeLocationIndex<T>> Feature<T, INDEX>.restore(
        location: ByteCodeLocation,
        entity: LocationEntity
    ) {
        val data = entity.index(key)
        if (data != null) {
            val index = try {
                deserialize(location, data)
            } catch (e: Exception) {
                logger.warn(e) { "can't parse location" }
                null
            }
            if (index != null) {
                featureRegistry.append(location, this, index)
            } else {
                logger.warn("index ${key} is not restored for $location")
            }
        }
    }


}