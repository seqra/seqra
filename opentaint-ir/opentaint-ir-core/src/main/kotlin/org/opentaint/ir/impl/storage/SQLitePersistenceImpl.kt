package org.opentaint.ir.impl.storage

import mu.KLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import org.opentaint.ir.api.ByteCodeContainer
import org.opentaint.ir.api.JIRDBPersistence
import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.FeaturesRegistry
import org.opentaint.ir.impl.bytecode.JIRClassOrInterfaceImpl
import org.opentaint.ir.impl.fs.ByteCodeConverter
import org.opentaint.ir.impl.fs.ClassSourceImpl
import org.opentaint.ir.impl.fs.asByteCodeLocation
import java.io.Closeable
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SQLitePersistenceImpl(
    private val featuresRegistry: FeaturesRegistry,
    location: String? = null,
    private val clearOnStart: Boolean
) : JIRDBPersistence, Closeable, ByteCodeConverter {

    companion object : KLogging()

    private val lock = ReentrantLock()

    internal val db: Database
    private var keepAliveConnection: Connection? = null
    private val persistenceService = PersistenceService(this)

    init {
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        if (location == null) {
            val sqlitePath = "jdbc:sqlite:file:jirdb?mode=memory&cache=shared"
            keepAliveConnection = DriverManager.getConnection(sqlitePath)
            db = Database.connect(sqlitePath, "org.sqlite.JDBC")
        } else {
            val url = "jdbc:sqlite:$location"
            val dataSource = SQLiteDataSource(SQLiteConfig().also {
                it.setSynchronous(SQLiteConfig.SynchronousMode.OFF)
                it.setPageSize(32_768)
                it.setCacheSize(-8_000)
            }).also {
                it.url = url
            }
            //, databaseConfig = DatabaseConfig.invoke { sqlLogger = StdOutSqlLogger })
            db = Database.connect(dataSource)
        }


        write {
            if (clearOnStart) {
                SchemaUtils.drop(
//                    Classpaths,
                    BytecodeLocations,
                    Classes, Symbols, ClassHierarchies, ClassInnerClasses, OuterClasses,
                    Methods, MethodParameters,
                    Fields,
                    Annotations, AnnotationValues
                )
            }
            SchemaUtils.create(
//                Classpaths,
                BytecodeLocations,
                Classes, Symbols, ClassHierarchies, ClassInnerClasses, OuterClasses,
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

    override val locations: List<JIRByteCodeLocation>
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

    override fun <T> read(newTx: Boolean, action: () -> T): T {
        return if (newTx) {
            transaction(db) {
                action()
            }
        } else {
            action()
        }
    }

    override fun findClassByName(
        cp: JIRClasspath,
        locations: List<RegisteredLocation>,
        fullName: String
    ): JIRClassOrInterface? {
        val ids = locations.map { it.id }
        return transaction(db) {
            val symbolId = SymbolEntity.find(Symbols.name eq fullName)
                .firstOrNull()?.id?.value ?: return@transaction null
            val found = Classes.slice(Classes.locationId, Classes.bytecode)
                .select(Classes.name eq symbolId and (Classes.locationId inList ids))
                .firstOrNull() ?: return@transaction null
            val locationId = found[Classes.locationId].value
            val byteCode = found[Classes.bytecode]
            JIRClassOrInterfaceImpl(
                cp, ClassSourceImpl(
                    location = locations.first { it.id == locationId },
                    className = fullName,
                    byteCode = byteCode
                )
            )

        }
    }

    override fun persist(location: RegisteredLocation, classes: List<ByteCodeContainer>) {
        val allClasses = classes.map {
            it.asmNode.asClassInfo(it.binary)
        }
        write {
            persistenceService.persist(location, allClasses)
        }
    }

    override fun close() {
        keepAliveConnection?.close()
    }

}

