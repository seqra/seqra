package org.opentaint.ir.impl.storage

import org.opentaint.ir.api.jvm.JavaVersion
import org.opentaint.ir.api.jvm.JIRByteCodeLocation
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRDatabasePersistence
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.storage.ers.Entity
import org.opentaint.ir.api.storage.ers.getEntityOrNull
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.storage.jooq.tables.records.BytecodelocationsRecord
import org.opentaint.ir.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import java.io.File

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

    constructor(db: JIRDatabase, locationId: Long) : this(
        db.persistence,
        db.runtimeVersion,
        locationId,
        null,
        null
    )

    val data by lazy {
        cachedData ?: persistence.read { context ->
            context.execute(
                sqlAction = { jooq ->
                    val record = jooq.fetchOne(BYTECODELOCATIONS, BYTECODELOCATIONS.ID.eq(id))!!
                    PersistentByteCodeLocationData.fromSqlRecord(record)
                },
                noSqlAction = { txn ->
                    val entity = txn.getEntityOrNull(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE, id)!!
                    PersistentByteCodeLocationData.fromErsEntity(entity)
                }
            )
        }
    }

    override val jIRLocation: JIRByteCodeLocation?
        get() {
            return cachedLocation ?: data.toJIRLocation()
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
        try {
            val newOne = File(path).asByteCodeLocation(runtimeVersion, isRuntime = runtime).singleOrNull()
            if (newOne?.fileSystemId != fileSystemId) {
                return null
            }
            return newOne
        } catch (e: Exception) {
            return null
        }
    }
}

