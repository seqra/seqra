
package org.opentaint.ir.impl.fs

import org.opentaint.ir.api.ClassSource
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.vfs.PersistentByteCodeLocation

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
        location.jirLocation?.resolve(className) ?: className.throwClassNotFound()
    }
}

class PersistenceClassSource(
    private val classpath: JIRClasspath,
    override val className: String,
    val classId: Long,
    val locationId: Long
) : ClassSource {

    override val location = PersistentByteCodeLocation(classpath, locationId)

    override val byteCode by lazy {
        classpath.db.persistence.findBytecode(classId)
    }
}
