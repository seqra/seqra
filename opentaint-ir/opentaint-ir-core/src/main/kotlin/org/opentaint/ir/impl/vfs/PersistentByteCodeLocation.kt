package org.opentaint.ir.impl.vfs

import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.LocationType
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.storage.jooq.tables.records.BytecodelocationsRecord
import java.io.File

class PersistentByteCodeLocation(
    internal val entity: BytecodelocationsRecord,
    override val jirLocation: JIRByteCodeLocation
) : RegisteredLocation {

    constructor(entity: BytecodelocationsRecord) : this(entity, entity.toJcLocation())

    override val id: Long
        get() = entity.id!!
}


class RestoredJcByteCodeLocation(
    override val path: String,
    override val type: LocationType,
    override val hash: String
) : JIRByteCodeLocation {

    override val jarOrFolder: File
        get() = File(path)

    override fun isChanged(): Boolean {
        val actual = createRefreshed() ?: return true
        return actual.hash != hash
    }

    override fun createRefreshed(): JIRByteCodeLocation? {
        if (!jarOrFolder.exists()) {
            return null
        }
        return jarOrFolder.asByteCodeLocation(type == LocationType.RUNTIME)
    }

    override fun resolve(classFullName: String) = null

    override val classNames: Set<String>
        get() = emptySet()

    override val classes: Map<String, ByteArray>?
        get() = null
}


fun BytecodelocationsRecord.toJcLocation() = RestoredJcByteCodeLocation(
    path!!,
    LocationType.RUNTIME.takeIf { runtime!! } ?: LocationType.APP,
    hash!!)