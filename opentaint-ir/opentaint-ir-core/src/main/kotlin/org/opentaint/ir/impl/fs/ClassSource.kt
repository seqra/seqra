package org.opentaint.opentaint-ir.impl.fs

import org.opentaint.opentaint-ir.api.ClassSource
import org.opentaint.opentaint-ir.api.JIRClasspath
import org.opentaint.opentaint-ir.api.RegisteredLocation
import org.opentaint.opentaint-ir.api.throwClassNotFound
import org.opentaint.opentaint-ir.impl.vfs.PersistentByteCodeLocation

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
    private val classpath: JIRClasspath,
    override val className: String,
    val classId: Long,
    val locationId: Long,
    private val cachedByteCode: ByteArray? = null
) : ClassSource {

    private constructor(persistenceClassSource: PersistenceClassSource, byteCode: ByteArray) : this(
        persistenceClassSource.classpath,
        persistenceClassSource.className,
        persistenceClassSource.classId,
        persistenceClassSource.locationId,
        byteCode
    )

    override val location = PersistentByteCodeLocation(classpath, locationId)

    override val byteCode by lazy {
        cachedByteCode ?: classpath.db.persistence.findBytecode(classId)
    }

    fun bind(byteCode: ByteArray?) = when {
        byteCode != null -> PersistenceClassSource(this, byteCode)
        else -> this
    }
}
