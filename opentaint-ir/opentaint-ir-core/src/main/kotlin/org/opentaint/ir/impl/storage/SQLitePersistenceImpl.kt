package org.opentaint.ir.impl.storage

import mu.KLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import org.opentaint.ir.api.ByteCodeContainer
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.JIRDBPersistence
import org.opentaint.ir.impl.FeaturesRegistry
import org.opentaint.ir.impl.fs.ByteCodeConverter
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.storage.BytecodeLocationEntity.Companion.findOrNew
import java.io.Closeable
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SQLitePersistenceImpl(
    private val featuresRegistry: FeaturesRegistry,
    private val location: File? = null,
    private val clearOnStart: Boolean
) : JIRDBPersistence, Closeable, ByteCodeConverter {

    companion object : KLogging()

    private val lock = ReentrantLock()

    private val dataSource = SQLiteDataSource(SQLiteConfig().also {
        it.setSynchronous(SQLiteConfig.SynchronousMode.OFF)
        it.setPageSize(32_768)
        it.setCacheSize(-8_000)
    }).also {
        it.url = "jdbc:sqlite:$location"
    }

    internal val db: Database = Database.connect(dataSource)
    private val persistenceService = PersistenceService(this)

    init {
        write {
            if (clearOnStart) {
                SchemaUtils.drop(
                    Classpaths, ClasspathLocations, BytecodeLocations,
                    Classes, Symbols, ClassInterfaces, ClassInnerClasses, OuterClasses,
                    Methods, MethodParameters,
                    Fields,
                    Annotations, AnnotationValues
                )
            }
            SchemaUtils.create(
                Classpaths, ClasspathLocations,
                BytecodeLocations,
                Classes, Symbols, ClassInterfaces, ClassInnerClasses, OuterClasses,
                Methods, MethodParameters,
                Fields,
                Annotations, AnnotationValues,
            )
        }
    }

    override fun setup() {
        write {
            featuresRegistry.jirdbFeatures.forEach {
                it.persistence?.beforeIndexing(clearOnStart)
            }
        }

        persistenceService.setup()
    }

    override val locations: List<ByteCodeLocation>
        get() {
            return transaction(db) {
                BytecodeLocationEntity.all().toList().mapNotNull {
                    try {
                        File(it.path).asByteCodeLocation(isRuntime = it.runtime)
                    } catch (e: Exception) {
                        null
                    }
                }.toList()
            }
        }

    override fun save(jirdb: JIRDB) {
        transaction(db) {
            jirdb.locations.forEach { location ->
                location.findOrNew()
            }
        }
    }

    override fun <T> write(newTx: Boolean, action: () -> T): T {
        return lock.withLock {
            if (newTx) {
                transaction(db) {
                    action()
                }
            } else {
                action()
            }
        }
    }

    override fun persist(location: ByteCodeLocation, classes: List<ByteCodeContainer>) {
        persistenceService.saveClasses(location, classes.map {
            it.classNode.asClassInfo(it.binary)
        })
    }

    override fun close() {
    }


}

