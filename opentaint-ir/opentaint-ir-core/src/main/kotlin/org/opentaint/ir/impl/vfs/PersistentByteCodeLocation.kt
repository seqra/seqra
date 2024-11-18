package org.opentaint.ir.impl.vfs

import org.opentaint.ir.api.JIRDBPersistence
import org.opentaint.ir.api.JavaVersion
import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.LocationType
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.storage.jooq.tables.records.BytecodelocationsRecord
import org.opentaint.ir.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import java.io.File

class PersistentByteCodeLocation(
    override val id: Long,
    override val jirLocation: JIRByteCodeLocation
) : RegisteredLocation {

    constructor(entity: BytecodelocationsRecord, javaVersion: JavaVersion) : this(entity.id!!, entity.toJcLocation(javaVersion))

}

class LazyPersistentByteCodeLocation(
    private val persistence: JIRDBPersistence,
    override val id: Long,
    private val runtimeVersion: JavaVersion
) :
    RegisteredLocation {

    override val jirLocation: JIRByteCodeLocation
        get() {
            return persistence.read {
                it.fetchOne(BYTECODELOCATIONS, BYTECODELOCATIONS.ID.eq(id))!!.toJcLocation(runtimeVersion)
            }
        }

}


class RestoredJcByteCodeLocation(
    override val path: String,
    override val type: LocationType,
    override val hash: String,
    private val runtimeVersion: JavaVersion
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
        return jarOrFolder.asByteCodeLocation(runtimeVersion, type == LocationType.RUNTIME)
    }

    override fun resolve(classFullName: String) = null

    override val classNames: Set<String>
        get() = emptySet()

    override val classes: Map<String, ByteArray>?
        get() = null
}


fun BytecodelocationsRecord.toJcLocation(runtimeVersion: JavaVersion) = RestoredJcByteCodeLocation(
    path!!,
    LocationType.RUNTIME.takeIf { runtime!! } ?: LocationType.APP,
    hash!!,
    runtimeVersion)