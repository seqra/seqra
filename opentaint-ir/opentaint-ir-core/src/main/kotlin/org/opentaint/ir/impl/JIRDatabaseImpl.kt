package org.opentaint.ir.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opentaint.ir.JIRDBSettings
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.fs.filterExisted
import org.opentaint.ir.impl.fs.load
import org.opentaint.ir.impl.index.InMemoryGlobalIdsStore
import org.opentaint.ir.impl.storage.BytecodeLocationEntity
import org.opentaint.ir.impl.storage.PersistentEnvironment
import org.opentaint.ir.impl.tree.ClassTree
import org.opentaint.ir.impl.tree.RemoveLocationsVisitor
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class JIRDBImpl(
    private val persistentEnvironment: PersistentEnvironment? = null,
    private val settings: JIRDBSettings,
    override val globalIdStore: InMemoryGlobalIdsStore = InMemoryGlobalIdsStore()
) : JIRDB {

    private val classTree = ClassTree()
    internal val javaRuntime = JavaRuntime(settings.jre)
    private val hooks = settings.hooks.map { it(this) }

    internal val featureRegistry = FeaturesRegistry(persistentEnvironment, globalIdStore, settings.fullFeatures)
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
                        sql {
                            locationStore?.findOrNewTx(location)
                        }
                        libraryTree
                    } else {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
        sql {
            persistentEnvironment?.save(this@JIRDBImpl)
        }

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
                            val classes = addedClasses.map { it.info() }
                            sql {
                                locationStore?.saveClasses(location, classes)
                            }
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

    override suspend fun rebuildIndexes() {
        awaitBackgroundJobs()
        // todo implement me
    }

    override fun watchFileSystemChanges(): JIRDB {
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

    fun afterStart() {
        hooks.forEach { it.afterStart() }
    }

    override fun close() {
        isClosed.set(true)
        locationsRegistry.close()
        backgroundJobs.values.forEach {
            it.cancel()
        }
        backgroundJobs.clear()
        persistentEnvironment?.close()
        hooks.forEach { it.afterStop() }
    }

    private fun assertNotClosed() {
        if (isClosed.get()) {
            throw IllegalStateException("Database is already closed")
        }
    }

    internal suspend fun restoreDataFrom(locations: Map<BytecodeLocationEntity, ByteCodeLocation>) {
        val env = persistentEnvironment ?: return
//        env.globalIds.restore(globalIdStore)
//        val trees = withContext(Dispatchers.IO) {
//            locations.map { (entity, location) ->
//                async {
//                    transaction {
//                        val libraryClasses = LibraryClassTree(location)
//                        val classes = entity.classes
//                        classes.forEach { libraryClasses.addClass(LazyByteCodeSource(location, it)) }
//                        restoreIndexes(location, entity)
//                        libraryClasses
//                    }
//                }
//            }
//        }.awaitAll()
//        trees.forEach {
//            it.pushInto(classTree)
//        }
    }

//    private fun restoreIndexes(location: ByteCodeLocation, entity: LocationEntity) {
//        featureRegistry.features.forEach { it.restore(location, entity) }
//    }

//    private fun <T, INDEX : ByteCodeLocationIndex<T>> Feature<T, INDEX>.restore(
//        location: ByteCodeLocation,
//        entity: LocationEntity
//    ) {
//        val data = entity.index(key)
//        if (data != null) {
//            val index = try {
//                deserialize(globalIdStore, location, data)
//            } catch (e: Exception) {
//                logger.warn(e) { "can't parse location" }
//                null
//            }
//            if (index != null) {
//                featureRegistry.append(location, this, index)
//            } else {
//                logger.warn("index ${key} is not restored for $location")
//            }
//        }
//    }

}