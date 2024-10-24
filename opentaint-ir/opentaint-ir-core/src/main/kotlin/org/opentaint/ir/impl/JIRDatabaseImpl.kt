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
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.JIRDBPersistence
import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.fs.filterExisted
import org.opentaint.ir.impl.fs.load
import org.opentaint.ir.impl.storage.PersistentLocationRegistry
import org.opentaint.ir.impl.storage.SQLitePersistenceImpl
import org.opentaint.ir.impl.vfs.GlobalClassesVfs
import org.opentaint.ir.impl.vfs.RemoveLocationsVisitor
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class JIRDBImpl(
    override val persistence: JIRDBPersistence? = null,
    val featureRegistry: FeaturesRegistry,
    private val settings: JIRDBSettings
) : JIRDB {

    private val classesVfs = GlobalClassesVfs()
    internal val javaRuntime = JavaRuntime(settings.jre)
    private val hooks = settings.hooks.map { it(this) }

    internal val locationsRegistry: LocationsRegistry
    private val backgroundJobs = ConcurrentHashMap<Int, Job>()

    private val isClosed = AtomicBoolean()
    private val jobId = AtomicInteger()

    init {
        featureRegistry.bind(this)
        locationsRegistry = if (persistence == null) {
            InMemoryLocationsRegistry(featureRegistry)
        } else {
            // todo rewrite in more elegant way
            PersistentLocationRegistry(persistence as SQLitePersistenceImpl, featureRegistry)
        }
    }

    private lateinit var runtimeLocations: List<RegisteredLocation>

    override val locations: List<JIRByteCodeLocation>
        get() = locationsRegistry.locations.toList()

    suspend fun loadJavaLibraries() {
        assertNotClosed()
        runtimeLocations = javaRuntime.allLocations.loadAll()
    }

    override suspend fun classpath(dirOrJars: List<File>): JIRClasspath {
        assertNotClosed()
        val existedLocations = dirOrJars.filterExisted().map { it.asByteCodeLocation() }.loadAll()
        val classpathSetLocations = existedLocations.toList() + runtimeLocations
        return JIRClasspathImpl(
            locationsRegistry.snapshot(classpathSetLocations),
            featureRegistry,
            this,
            classesVfs
        )
    }

    fun classpath(locations: List<RegisteredLocation>): JIRClasspath {
        assertNotClosed()
        val classpathSetLocations = locations.toSet() + runtimeLocations
        return JIRClasspathImpl(
            locationsRegistry.snapshot(classpathSetLocations.toList()),
            featureRegistry,
            this,
            classesVfs
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

    override suspend fun loadLocations(locations: List<JIRByteCodeLocation>) = apply {
        assertNotClosed()
        locations.loadAll()
    }

    private suspend fun List<JIRByteCodeLocation>.loadAll(): List<RegisteredLocation> {
        val actions = ConcurrentLinkedQueue<RegisteredLocation>()

        val registeredLocations = locationsRegistry.addLocations(this)

        val libraryTrees = withContext(Dispatchers.IO) {
            registeredLocations.map { location ->
                async {
                    // here something may go wrong
                    val libraryTree = location.jirLocation.load()
                    actions.add(location)
                    libraryTree
                }
            }
        }.awaitAll()
        persistence?.write {
            persistence.save(this@JIRDBImpl)
        }

        val locationClasses = libraryTrees.associate {
            it.location to it.pushInto(classesVfs).values
        }
        val backgroundJobId = jobId.incrementAndGet()
        backgroundJobs[backgroundJobId] = BackgroundScope.launch {
            val parentScope = this
            actions.map { location ->
                async {
                    if (parentScope.isActive) {
                        val addedClasses = locationClasses[location.jirLocation]
                        if (addedClasses != null) {
                            if (parentScope.isActive) {
                                persistence?.persist(location, addedClasses.toList())
                                featureRegistry.index(location, addedClasses)
                            }
                        }
                    }
                }
            }.joinAll()

            backgroundJobs.remove(backgroundJobId)
        }
        return registeredLocations
    }

    override suspend fun refresh() {
        awaitBackgroundJobs()
        locationsRegistry.refresh {
            listOf(it.jirLocation).loadAll()
        }
        val outdatedLocations = locationsRegistry.cleanup()
        classesVfs.visit(RemoveLocationsVisitor(outdatedLocations))
    }

    override suspend fun rebuildFeatures() {
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
        persistence?.close()
        hooks.forEach { it.afterStop() }
    }

    private fun assertNotClosed() {
        if (isClosed.get()) {
            throw IllegalStateException("Database is already closed")
        }
    }

}