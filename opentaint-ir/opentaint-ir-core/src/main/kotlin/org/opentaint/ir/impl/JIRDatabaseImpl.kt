package org.opentaint.ir.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import org.opentaint.ir.impl.fs.lazySources
import org.opentaint.ir.impl.fs.sources
import org.opentaint.ir.impl.storage.PersistentLocationRegistry
import org.opentaint.ir.impl.vfs.GlobalClassesVfs
import org.opentaint.ir.impl.vfs.RemoveLocationsVisitor
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class JIRDBImpl(
    override val persistence: JIRDBPersistence,
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

    private val backgroundScope = BackgroundScope()

    init {
        featureRegistry.bind(this)
        locationsRegistry = PersistentLocationRegistry(persistence, featureRegistry)
    }

    override val locations: List<JIRByteCodeLocation>
        get() = locationsRegistry.actualLocations.map { it.jirLocation }

    suspend fun restore() {
        persistence.setup()
        locationsRegistry.cleanup()
        val runtime = JavaRuntime(settings.jre).allLocations
        locationsRegistry.setup(runtime).new.process()
        locationsRegistry.registerIfNeeded(
            settings.predefinedDirOrJars.filter { it.exists() }.map { it.asByteCodeLocation(isRuntime = false) }
        ).new.process()
    }

    override suspend fun classpath(dirOrJars: List<File>): JIRClasspath {
        assertNotClosed()
        val existedLocations = dirOrJars.filterExisted().map { it.asByteCodeLocation() }
        val processed = locationsRegistry.registerIfNeeded(existedLocations.toList())
            .also { it.new.process() }.registered + locationsRegistry.runtimeLocations
        return JIRClasspathImpl(
            locationsRegistry.newSnapshot(processed),
            this,
            classesVfs
        )
    }

    fun new(cp: JIRClasspathImpl): JIRClasspath {
        assertNotClosed()
        return JIRClasspathImpl(
            locationsRegistry.newSnapshot(cp.registeredLocations),
            cp.db,
            classesVfs
        )
    }

    override suspend fun load(dirOrJar: File) = apply {
        assertNotClosed()
        load(listOf(dirOrJar))
    }

    override suspend fun load(dirOrJars: List<File>) = apply {
        assertNotClosed()
        loadLocations(dirOrJars.filterExisted().map { it.asByteCodeLocation() })
    }

    override suspend fun loadLocations(locations: List<JIRByteCodeLocation>) = apply {
        assertNotClosed()
        locationsRegistry.registerIfNeeded(locations).new.process()
    }

    private suspend fun List<RegisteredLocation>.process(): List<RegisteredLocation> {
        withContext(Dispatchers.IO) {
            map { location ->
                async {
                    // here something may go wrong
                    location.lazySources.forEach {
                        classesVfs.addClass(it)
                    }
                }
            }
        }.awaitAll()
        val backgroundJobId = jobId.incrementAndGet()
        backgroundJobs[backgroundJobId] = backgroundScope.launch {
            val parentScope = this
            map { location ->
                async {
                    val sources = location.sources
                    if (parentScope.isActive) {
                        persistence.persist(location, sources)
                        classesVfs.visit(RemoveLocationsVisitor(listOf(location)))
                        featureRegistry.index(location, sources)
                    }
                }
            }.joinAll()
            locationsRegistry.afterProcessing(this@process)
            backgroundJobs.remove(backgroundJobId)
        }
        return this
    }

    override suspend fun refresh() {
        awaitBackgroundJobs()
        locationsRegistry.refresh().new.process()
        val result = locationsRegistry.cleanup()
        classesVfs.visit(RemoveLocationsVisitor(result.outdated))
    }

    override suspend fun rebuildFeatures() {
        rebuildFeatures(true)
    }

    private suspend fun rebuildFeatures(await: Boolean) {
        if (await) {
            awaitBackgroundJobs()
        }
        featureRegistry.broadcast(JIRInternalSignal.Rebuild)

        withContext(Dispatchers.IO) {
            val locations = locationsRegistry.actualLocations
            val parentScope = this
            locations.map {
                async {
                    val addedClasses = persistence.findClasses(it)
                    if (parentScope.isActive) {
                        featureRegistry.index(it, addedClasses)
                    }
                }
            }.joinAll()
        }
    }

    override fun watchFileSystemChanges(): JIRDB {
        val delay = settings.watchFileSystemDelay?.toLong()
        if (delay != null) { // just paranoid check
            backgroundScope.launch {
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
        runBlocking {
            awaitBackgroundJobs()
        }
        backgroundScope.cancel()
        backgroundJobs.clear()
        persistence.close()
        hooks.forEach { it.afterStop() }
    }

    private fun assertNotClosed() {
        if (isClosed.get()) {
            throw IllegalStateException("Database is already closed")
        }
    }

}