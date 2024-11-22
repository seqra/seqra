
package org.opentaint.ir.impl.fs

import org.opentaint.ir.api.ClassSource
import org.opentaint.ir.api.RegisteredLocation

val RegisteredLocation.sources: List<ClassSource>
    get() {
        return jirLocation?.classes?.map {
            ClassSourceImpl(this, it.key, it.value)
        }.orEmpty()
    }

val RegisteredLocation.lazySources: List<ClassSource>
    get() {
        val classNames = jirLocation?.classNames ?: return emptyList()
        if (classNames.any { it.startsWith("java.") }) {
            return sources
        }
        return classNames.map {
            LazyClassSourceImpl(this, it)
        }
    }