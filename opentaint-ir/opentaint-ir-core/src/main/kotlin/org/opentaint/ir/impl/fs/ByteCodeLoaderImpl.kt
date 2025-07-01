package org.opentaint.ir.impl.fs

import org.opentaint.ir.api.jvm.ClassSource
import org.opentaint.ir.api.jvm.RegisteredLocation

val RegisteredLocation.sources: List<ClassSource>
    get() {
        return jIRLocation?.classes?.map {
            ClassSourceImpl(this, it.key, it.value)
        }.orEmpty()
    }

val RegisteredLocation.lazySources: List<ClassSource>
    get() {
        return (jIRLocation?.classNames ?: return emptyList()).map {
            LazyClassSourceImpl(this, it)
        }
    }
