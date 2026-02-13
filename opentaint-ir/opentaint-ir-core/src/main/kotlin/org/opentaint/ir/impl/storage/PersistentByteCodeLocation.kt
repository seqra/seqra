package org.opentaint.ir.impl.storage

import org.opentaint.ir.api.jvm.JIRByteCodeLocation
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRDatabasePersistence
import org.opentaint.ir.api.jvm.JavaVersion
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.storage.ers.Entity
import org.opentaint.ir.api.storage.ers.getEntityOrNull
import org.opentaint.ir.impl.fs.BuildFolderLocation
import org.opentaint.ir.impl.fs.JarLocation
import org.opentaint.ir.impl.fs.JavaRuntimeModuleLocation
import org.opentaint.ir.impl.fs.isJar
import org.opentaint.ir.impl.storage.jooq.tables.records.BytecodelocationsRecord
import org.opentaint.ir.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import java.io.File
import java.math.BigInteger

data class PersistentByteCodeLocationData(
    val id: Long,
    val runtime: Boolean,
    val path: String,
    val fileSystemId: String
) {
    companion object {
        fun fromSqlRecord(record: BytecodelocationsRecord) =
            PersistentByteCodeLocationData(record.id!!, record.runtime!!, record.path!!, record.uniqueid!!)

        fun fromErsEntity(entity: Entity) = PersistentByteCodeLocationData(
            id = entity.id.instanceId,
            runtime = (entity.get<Boolean>(BytecodeLocationEntity.IS_RUNTIME) == true),
            path = entity[BytecodeLocationEntity.PATH]!!,
            fileSystemId = entity[BytecodeLocationEntity.FILE_SYSTEM_ID]!!
        )
    }
}

class PersistentByteCodeLocation(
    private val persistence: JIRDatabasePersistence,
    private val runtimeVersion: JavaVersion,
    override val id: Long,
    private val cachedData: PersistentByteCodeLocationData? = null,
    private val cachedLocation: JIRByteCodeLocation? = null
) : RegisteredLocation {

    constructor(
        db: JIRDatabase,
        data: PersistentByteCodeLocationData,
        location: JIRByteCodeLocation? = null
    ) : this(
        db.persistence,
        db.runtimeVersion,
        data.id,
        data,
        location
    )

    val data by lazy {
        cachedData ?: persistence.read { context ->
            context.execute(
                sqlAction = {
                    val jooq = context.dslContext
                    val record = jooq.fetchOne(BYTECODELOCATIONS, BYTECODELOCATIONS.ID.eq(id))!!
                    PersistentByteCodeLocationData.fromSqlRecord(record)
                },
                noSqlAction = {
                    val txn = context.txn
                    val entity = txn.getEntityOrNull(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE, id)!!
                    PersistentByteCodeLocationData.fromErsEntity(entity)
                }
            )
        }
    }

    override val jIRLocation: JIRByteCodeLocation? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        cachedLocation ?: data.toJIRLocation()
    }

    override val path: String
        get() = data.path

    override val isRuntime: Boolean
        get() = data.runtime

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RegisteredLocation

        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    private fun PersistentByteCodeLocationData.toJIRLocation(): JIRByteCodeLocation? {
        return try {
            if (isRuntime && JavaRuntimeModuleLocation.isModuleLocation(path)) {
                val location = JavaRuntimeModuleLocation.fromPath(path)

                val fsId = fileSystemId
                return object : JavaRuntimeModuleLocation(location.javaHome, location.module) {
                    override val fileSystemIdHash: BigInteger
                        get() = BigInteger(fsId, Character.MAX_RADIX)
                }
            }

            val file = File(path)
            if (!file.exists()) return null

            if (file.isJar()) {
                // NB! This JarLocation inheritor is necessary for hacking PersistentLocationsRegistry
                // so that isChanged() would work properly in PersistentLocationsRegistry.refresh()
                val fsId = fileSystemId
                return object : JarLocation(file, isRuntime, runtimeVersion) {
                    override val fileSystemIdHash: BigInteger
                        get() = BigInteger(fsId, Character.MAX_RADIX)
                }
            }

            if (file.isDirectory) {
                return BuildFolderLocation(file)
            }

            return null
        } catch (e: Exception) {
            null
        }
    }
}
