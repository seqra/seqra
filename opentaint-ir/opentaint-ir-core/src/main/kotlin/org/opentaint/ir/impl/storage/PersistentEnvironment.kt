package org.opentaint.ir.impl.storage

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import jetbrains.exodus.entitystore.PersistentEntityStores
import jetbrains.exodus.entitystore.StoreTransaction
import jetbrains.exodus.env.Environments
import mu.KLogging
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ByteCodeLocationIndex
import org.opentaint.ir.api.Feature
import org.opentaint.ir.impl.FeaturesRegistry
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.storage.scheme.DatabaseStore
import org.opentaint.ir.impl.storage.scheme.GlobalIdStore
import org.opentaint.ir.impl.storage.scheme.LocationStore
import java.io.File
import java.net.URL
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

class PersistentEnvironment(id: String, location: File? = null) {

    companion object : KLogging()

    private val home = (location?.toPath() ?: Paths.get(System.getProperty("user.home"), ".jdbc")).let {
        val file = it.toFile()
        if (!file.exists() || !file.mkdirs()) {
            throw DataStorageException("can't create storage in ${file.absolutePath}")
        }
        file
    }

    private val env = Environments.newInstance(home)
    private val persistentEntityStore = PersistentEntityStores.newInstance(env, id)

    val locationStore = LocationStore(this)
    val databaseStore = DatabaseStore(this)
    val globalIds = GlobalIdStore(persistentEntityStore.environment)

    val allByteCodeLocations: Sequence<ByteCodeLocation>
        get() {
            return transactional {
                locationStore.all.mapNotNull {
                    try {
                        File(URL(it.url).file).asByteCodeLocation(isRuntime = it.isRuntime)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }


    fun restore(features: List<Feature<*, *>>, locations: List<ByteCodeLocation>): FeaturesRegistry {
        val indexes = ConcurrentHashMap<ByteCodeLocation, ConcurrentHashMap<String, ByteCodeLocationIndex<*>>>()

        transactional {
            locations.map {
                val locationEntity = locationStore.findOrNew(this, it)
                features.forEach { feature ->
                    val data = locationEntity.index(feature.key)
                    if (data != null) {

                        val index = try {
                            feature.deserialize(data)
                        } catch (e: Exception) {
                            logger.warn(e) { "can't parse location" }
                            null
                        }
                        if (index != null) {
                            indexes.getOrPut(it) { ConcurrentHashMap() }.put(feature.key, index)
                        } else {
                            logger.warn("index ${feature.key} is not restored for $it")
                        }
                    }
                }
            }
        }
        return FeaturesRegistry(this, features, indexes)
    }


    fun <T> transactional(action: StoreTransaction.() -> T): T {
        return persistentEntityStore.computeInTransaction { tx ->
            tx.action()
        }
    }

}

val mapper = ObjectMapper().registerKotlinModule()
