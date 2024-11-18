package org.opentaint.ir.impl.fs

import org.opentaint.ir.api.ClassSource
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.api.throwClassNotFound

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
