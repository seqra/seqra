package org.opentaint.ir.impl.vfs

import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.storage.jooq.tables.records.BytecodelocationsRecord
import org.opentaint.ir.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import java.io.File

class PersistentByteCodeLocation(
    private val jirdb: JIRDB,
    override val id: Long,
    private val cachedRecord: BytecodelocationsRecord? = null,
    private val cachedLocation: JIRByteCodeLocation? = null
) : RegisteredLocation {

    constructor(jirdb: JIRDB, record: BytecodelocationsRecord, location: JIRByteCodeLocation? = null) : this(
        jirdb,
        record.id!!,
        record,
        location
    )

    val record by lazy {
        cachedRecord ?: jirdb.persistence.read { jooq ->
            jooq.fetchOne(BYTECODELOCATIONS, BYTECODELOCATIONS.ID.eq(id))!!
        }
    }

    override val jirLocation: JIRByteCodeLocation?
        get() {
            return cachedLocation ?: record.toJcLocation()
        }

    override val path: String
        get() = record.path!!

    override val runtime: Boolean
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

    private fun BytecodelocationsRecord.toJcLocation(): JIRByteCodeLocation? {
        try {
            val newOne = File(path!!).asByteCodeLocation(jirdb.runtimeVersion, isRuntime = runtime!!)
            if (newOne.hash != hash!!) {
                return null
            }
            return newOne
        } catch (e: Exception) {
            return null
        }
    }
}

