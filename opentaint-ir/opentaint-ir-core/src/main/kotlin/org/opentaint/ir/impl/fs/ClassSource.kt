package org.opentaint.ir.impl.fs

import org.opentaint.ir.api.jvm.ClassSource
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.throwClassNotFound

class ClassSourceImpl(
    override val location: RegisteredLocation,
    override val className: String,
    override val byteCode: ByteArray
) : ClassSource

class LazyClassSourceImpl(
    override val location: RegisteredLocation,
    override val className: String
) : ClassSource {

    override val byteCode by lazy {
        location.jIRLocation?.resolve(className) ?: className.throwClassNotFound()
    }
}

class PersistenceClassSource(
    private val db: JIRDatabase,
    override val className: String,
    val classId: Long,
    val locationId: Long,
    private val cachedByteCode: ByteArray? = null
) : ClassSource {

    private constructor(persistenceClassSource: PersistenceClassSource, byteCode: ByteArray) : this(
        persistenceClassSource.db,
        persistenceClassSource.className,
        persistenceClassSource.classId,
        persistenceClassSource.locationId,
        byteCode
    )

    override val location = db.persistence.findLocation(locationId)

    override val byteCode by lazy {
        cachedByteCode ?: db.persistence.findBytecode(classId)
    }

    fun bind(byteCode: ByteArray?) = when {
        byteCode != null -> PersistenceClassSource(this, byteCode)
        else -> this
    }
}
