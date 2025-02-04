package org.opentaint.ir.impl

import kotlinx.coroutines.CoroutineScope
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
import org.opentaint.ir.api.JavaVersion
import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRClasspathFeature
import org.opentaint.ir.api.JIRDatabase
import org.opentaint.ir.api.JIRDatabasePersistence
import org.opentaint.ir.api.JIRFeature
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.features.classpaths.ClasspathCache
import org.opentaint.ir.impl.features.classpaths.KotlinMetadata
import org.opentaint.ir.impl.features.classpaths.MethodInstructionsFeature
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

class JIRDatabaseImpl(
    internal val javaRuntime: JavaRuntime,
    override val persistence: JIRDatabasePersistence,
    val featureRegistry: FeaturesRegistry,
    private val settings: JIRSettings
) : JIRDatabase {

    private val classesVfs = GlobalClassesVfs()
    private val hooks = settings.hooks.map { it(this) }

    internal val locationsRegistry: LocationsRegistry
    private val backgroundJobs = ConcurrentHashMap<Int, Job>()

    private val isClosed = AtomicBoolean()
    private val jobId = AtomicInteger()

    private val backgroundScope = BackgroundScope()

    init {
        featureRegistry.bind(this)
        locationsRegistry = PersistentLocationRegistry(this, featureRegistry)
    }

    override val locations: List<RegisteredLocation>
        get() = locationsRegistry.actualLocations

    suspend fun restore() {
        persistence.setup()
        locationsRegistry.cleanup()
        val runtime = JavaRuntime(settings.jre).allLocations
        locationsRegistry.setup(runtime).new.process(false)
        locationsRegistry.registerIfNeeded(
            settings.predefinedDirOrJars.filter { it.exists() }
                .map { it.asByteCodeLocation(javaRuntime.version, isRuntime = false) }
        ).new.process(true)
    }

    private fun List<JIRClasspathFeature>?.appendBuiltInFeatures(): List<JIRClasspathFeature> {
        if (this != null && any { it is ClasspathCache }) {
            return this + listOf(KotlinMetadata, MethodInstructionsFeature)
        }
        return listOf(ClasspathCache(settings.cacheSettings), KotlinMetadata, MethodInstructionsFeature) + orEmpty()
    }

    override suspend fun classpath(dirOrJars: List<File>, features: List<JIRClasspathFeature>?): JIRClasspath {
        assertNotClosed()
        val existedLocations = dirOrJars.filterExisted().map { it.asByteCodeLocation(javaRuntime.version) }
        val processed = locationsRegistry.registerIfNeeded(existedLocations.toList())
            .also { it.new.process(true) }.registered + locationsRegistry.runtimeLocations
        return classpathOf(processed, features)
    }

    override fun classpathOf(locations: List<RegisteredLocation>, features: List<JIRClasspathFeature>?): JIRClasspath {
        return JIRClasspathImpl(
            locationsRegistry.newSnapshot(locations),
            this,
            features.appendBuiltInFeatures(),
            classesVfs
        )
    }

    fun new(cp: JIRClasspathImpl): JIRClasspath {
        assertNotClosed()
        return JIRClasspathImpl(
            locationsRegistry.newSnapshot(cp.registeredLocations),
            cp.db,
            cp.features,
            classesVfs
        )
    }

    override val runtimeVersion: JavaVersion
        get() = javaRuntime.version

    override suspend fun load(dirOrJar: File) = apply {
        assertNotClosed()
        load(listOf(dirOrJar))
    }

    override suspend fun load(dirOrJars: List<File>) = apply {
        assertNotClosed()
        loadLocations(dirOrJars.filterExisted().map { it.asByteCodeLocation(javaRuntime.version) })
    }

    override suspend fun loadLocations(locations: List<JIRByteCodeLocation>) = apply {
        assertNotClosed()
        locationsRegistry.registerIfNeeded(locations).new.process(true)
    }

    private suspend fun List<RegisteredLocation>.process(createIndexes: Boolean): List<RegisteredLocation> {
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
                    parentScope.ifActive { persistence.persist(location, sources) }
                    parentScope.ifActive {
                        classesVfs.visit(
                            RemoveLocationsVisitor(
                                listOf(location),
                                settings.byteCodeSettings.prefixes
                            )
                        )
                    }
                    parentScope.ifActive { featureRegistry.index(location, sources) }
                }
            }.joinAll()
            if(createIndexes) {
                persistence.createIndexes()
            }
            locationsRegistry.afterProcessing(this@process)
            backgroundJobs.remove(backgroundJobId)
        }
        return this
    }

    override suspend fun refresh() {
        awaitBackgroundJobs()
        locationsRegistry.refresh().new.process(true)
        val result = locationsRegistry.cleanup()
        classesVfs.visit(RemoveLocationsVisitor(result.outdated, settings.byteCodeSettings.prefixes))
    }

    override suspend fun rebuildFeatures() {
        awaitBackgroundJobs()
        featureRegistry.broadcast(JIRInternalSignal.Drop)

        withContext(Dispatchers.IO) {
            val locations = locationsRegistry.actualLocations
            val parentScope = this
            locations.map {
                async {
                    val addedClasses = persistence.findClassSources(this@JIRDatabaseImpl, it)
                    parentScope.ifActive { featureRegistry.index(it, addedClasses) }
                }
            }.joinAll()
        }
    }

    override fun watchFileSystemChanges(): JIRDatabase {
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
        backgroundJobs.values.joinAll()
    }

    override fun isInstalled(feature: JIRFeature<*, *>): Boolean {
        return featureRegistry.has(feature)
    }

    suspend fun afterStart() {
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
        backgroundJobs.clear()
        classesVfs.close()
        backgroundScope.cancel()
        persistence.close()
        hooks.forEach { it.afterStop() }
    }

    private fun assertNotClosed() {
        if (isClosed.get()) {
            throw IllegalStateException("Database is already closed")
        }
    }

    private inline fun CoroutineScope.ifActive(action: () -> Unit) {
        if (isActive) {
            action()
        }
    }

}