package org.opentaint.ir.impl.storage.ers

import org.opentaint.ir.api.jvm.ClassSource
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRDatabasePersistence
import org.opentaint.ir.api.storage.ers.Entity
import org.opentaint.ir.api.storage.ers.compressed
import org.opentaint.ir.api.storage.ers.getEntityOrNull
import org.opentaint.ir.impl.storage.txn

fun Entity.toClassSource(
    persistence: JIRDatabasePersistence,
    className: String,
    nameId: Long,
    cachedByteCode: ByteArray? = null
): ClassSource =
    ErsClassSource(
        persistence = persistence as ErsPersistenceImpl,
        className = className,
        nameId = nameId,
        classId = id.instanceId,
        locationId = requireNotNull(getCompressed<Long>("locationId")) { "locationId property isn't set" },
        cachedByteCode = cachedByteCode
    )

fun Sequence<Entity>.toClassSourceSequence(db: JIRDatabase): Sequence<ClassSource> {
    val persistence = db.persistence
    return map { clazz ->
        val nameId = requireNotNull(clazz.getCompressed<Long>("nameId")) { "nameId property isn't set" }
        clazz.toClassSource(persistence, persistence.findSymbolName(nameId), nameId)
    }
}

fun Sequence<Entity>.filterDeleted(): Sequence<Entity> = filter { it.get<Boolean>("isDeleted") != true }

fun Sequence<Entity>.filterLocations(locationIds: Set<Long>): Sequence<Entity> = filter {
    it.getCompressed<Long>("locationId") in locationIds
}

fun Sequence<Entity>.filterLocations(locationId: Long): Sequence<Entity> = filter {
    it.getCompressed<Long>("locationId") == locationId
}

fun <T> Sequence<T>.exactSingleOrNull(): T? {
    val it = iterator()
    if (!it.hasNext()) return null
    return it.next().apply {
        check(!it.hasNext()) {
            "Sequence should have exactly one element or no elements"
        }
    }
}

val Entity?.bytecode: LazyBytecode get() = LazyBytecode(this)

class LazyBytecode(private val clazz: Entity?) {

    private val blobName = "bytecode${notNullClass().id.instanceId and 0xff}"

    operator fun invoke(): ByteArray? = clazz?.getRawBlob(blobName)

    operator fun invoke(bytecode: ByteArray?) = notNullClass().setRawBlob(blobName, bytecode)

    private fun notNullClass(): Entity = requireNotNull(clazz) { "Class entity cannot be null" }
}

private class ErsClassSource(
    private val persistence: ErsPersistenceImpl,
    override val className: String,
    private val nameId: Long,
    private var classId: Long,
    private val locationId: Long,
    private var cachedByteCode: ByteArray?
) : ClassSource {

    override val byteCode: ByteArray
        get() {
            val prevClassId = classId
            val checkedClassId = checkClassId()
            return if (prevClassId == checkedClassId && cachedByteCode != null) {
                cachedByteCode!!
            } else {
                persistence.findBytecode(checkedClassId).also {
                    cachedByteCode?.let {
                        cachedByteCode = it
                    }
                }
            }
        }

    override val location = persistence.findLocation(locationId)

    private fun checkClassId(): Long = persistence.read { context ->
        val txn = context.txn
        var result = txn.getEntityOrNull("Class", classId)
        // Since location is mutable, class entity can become expired (deleted)
        // In that case, we have to re-evaluate it
        if (result == null || result.get<Boolean>("isDeleted") == true) {
            result = txn.find("Class", "nameId", nameId.compressed)
                .filterDeleted()
                .filterLocations(locationId)
                .single()
            classId = result.id.instanceId
        }
        classId
    }
}