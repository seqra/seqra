package org.opentaint.ir.impl.storage

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import jetbrains.exodus.entitystore.PersistentEntityStores
import jetbrains.exodus.entitystore.StoreTransaction
import jetbrains.exodus.env.EnvironmentConfig
import jetbrains.exodus.env.Environments
import mu.KLogging
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.storage.scheme.DatabaseStore
import org.opentaint.ir.impl.storage.scheme.GlobalIdStore
import org.opentaint.ir.impl.storage.scheme.LocationEntity
import org.opentaint.ir.impl.storage.scheme.LocationStore
import java.io.Closeable
import java.io.File
import java.net.URL
import java.nio.file.Paths

class PersistentEnvironment(id: String, location: File? = null) : Closeable {

    companion object : KLogging()

    private val home = (location?.toPath() ?: Paths.get(System.getProperty("user.home"), ".jdbc")).let {
        val file = it.toFile()
        if (!file.exists() && !file.mkdirs()) {
            throw DataStorageException("can't create storage in ${file.absolutePath}")
        }
        file
    }

    private val env = Environments.newInstance(home, EnvironmentConfig().setEnvCloseForcedly(true))
    private val persistentEntityStore = PersistentEntityStores.newInstance(env, id)

    val locationStore = LocationStore(this)
    val databaseStore = DatabaseStore(this)
    val globalIds = GlobalIdStore(persistentEntityStore.environment)

    val allByteCodeLocations: List<Pair<LocationEntity, ByteCodeLocation>>
        get() {
            return transactional {
                locationStore.all.mapNotNull {
                    try {
                        it to Paths.get(URL(it.url).toURI()).toFile().asByteCodeLocation(isRuntime = it.isRuntime)
                    } catch (e: Exception) {
                        null
                    }
                }.toList()
            }
        }

    fun <T> transactional(action: StoreTransaction.() -> T): T {
        return persistentEntityStore.computeInTransaction { tx ->
            tx.action()
        }
    }

    override fun close() {
        persistentEntityStore.setCloseEnvironment(true)
        persistentEntityStore.close()
    }
}

val mapper = ObjectMapper().registerKotlinModule()
