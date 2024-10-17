package org.opentaint.ir.impl.storage

import mu.KLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.LocationScope
import org.opentaint.ir.impl.JIRDBImpl
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.storage.scheme.LocationStore
import java.io.Closeable
import java.io.File

class PersistentEnvironment(id: String, location: File? = null, clearOnStart: Boolean) : Closeable {

    companion object : KLogging() {
        val ds = SQLiteDataSource(SQLiteConfig().also {
            it.setSynchronous(SQLiteConfig.SynchronousMode.OFF)
            it.setPageSize(32_768)
            it.setCacheSize(-8_000)
        }).also {
            it.url = "jdbc:sqlite:jirdb"
        }
    }

//    private val home = (location?.toPath() ?: Paths.get(System.getProperty("user.home"), ".jdbc")).let {
//        val file = it.toFile()
//        if (!file.exists() && !file.mkdirs()) {
//            throw DataStorageException("can't create storage in ${file.absolutePath}")
//        }
//        file
//    }

//    private val env = Environments.newInstance(home, EnvironmentConfig().setEnvCloseForcedly(true))
//    private val persistentEntityStore = PersistentEntityStores.newInstance(env, id)

    init {
        Database.connect(ds)
        transaction {
            if (clearOnStart) {
                SchemaUtils.drop(
                    Classpaths, ClasspathLocations, BytecodeLocations,
                    Classes, Symbols, ClassInterfaces, ClassInnerClasses, OuterClasses,
                    Methods, MethodParameters,
                    Fields
                )
            }
            SchemaUtils.create(
                Classpaths, ClasspathLocations,
                BytecodeLocations,
                Classes, Symbols, ClassInterfaces, ClassInnerClasses, OuterClasses,
                Methods, MethodParameters,
                Fields
            )
        }
    }

    val locationStore = LocationStore(this)
//    val globalIds = GlobalIdStore(persistentEntityStore.environment)

    val allByteCodeLocations: List<Pair<BytecodeLocationEntity, ByteCodeLocation>>
        get() {
            return transaction {
                locationStore.all.mapNotNull {
                    try {
                        it to File(it.path).asByteCodeLocation(isRuntime = it.runtime)
                    } catch (e: Exception) {
                        null
                    }
                }.toList()
            }
        }

    fun save(db: JIRDBImpl, clearOnStart: Boolean) {
        transaction {
            db.locationsRegistry.locations.forEach { location ->
                BytecodeLocationEntity.new {
                    path = location.path
                    runtime = location.scope == LocationScope.RUNTIME
                }
            }
        }
    }

    override fun close() {
    }
}

