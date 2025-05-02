package org.opentaint.ir.impl.vfs

import org.opentaint.ir.api.jvm.JavaVersion
import org.opentaint.ir.api.jvm.JIRByteCodeLocation
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRDatabasePersistence
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.storage.jooq.tables.records.BytecodelocationsRecord
import org.opentaint.ir.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import java.io.File

class PersistentByteCodeLocation(
    private val persistence: JIRDatabasePersistence,
    private val runtimeVersion: JavaVersion,
    override val id: Long,
    private val cachedRecord: BytecodelocationsRecord? = null,
    private val cachedLocation: JIRByteCodeLocation? = null
) : RegisteredLocation {

    constructor(db: JIRDatabase, record: BytecodelocationsRecord, location: JIRByteCodeLocation? = null) : this(
        db.persistence,
        db.runtimeVersion,
        record.id!!,
        record,
        location
    )

    constructor(db: JIRDatabase, locationId: Long) : this(
        db.persistence,
        db.runtimeVersion,
        locationId,
        null,
        null
    )

    val record by lazy {
        cachedRecord ?: persistence.read { jooq ->
            jooq.fetchOne(BYTECODELOCATIONS, BYTECODELOCATIONS.ID.eq(id))!!
        }
    }

    override val jIRLocation: JIRByteCodeLocation?
        get() {
            return cachedLocation ?: record.toJIRLocation()
        }

    override val path: String
        get() = record.path!!

    override val isRuntime: Boolean
        get() = record.runtime!!

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

    private fun BytecodelocationsRecord.toJIRLocation(): JIRByteCodeLocation? {
        try {
            val newOne = File(path!!).asByteCodeLocation(runtimeVersion, isRuntime = runtime!!)
            if (newOne.fileSystemId != uniqueid!!) {
                return null
            }
            return newOne
        } catch (e: Exception) {
            return null
        }
    }
}

