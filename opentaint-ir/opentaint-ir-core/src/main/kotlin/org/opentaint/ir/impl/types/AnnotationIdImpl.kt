package org.opentaint.ir.impl.types

import org.objectweb.asm.Type
import org.opentaint.ir.api.AnnotationId
import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.impl.SuspendableLazy
import org.opentaint.ir.impl.suspendableLazy

class AnnotationIdImpl(
    private val info: AnnotationInfo,
    private val classpath: ClasspathSet
) : AnnotationId {

    private val lazyAnnotationClass = suspendableLazy {
        classpath.findClassOrNull(info.className)
    }

    private val lazyValues: SuspendableLazy<Map<String, Any?>> = suspendableLazy {
        val size = info.values.size
        if (size > 0) {
            (0..size / 2).map { (info.values[it] as String) to fixValue(info.values[it + 1]) }.toMap()
        } else {
            emptyMap()
        }
    }

    override val visible: Boolean get() = info.visible

    override suspend fun annotationClassId() = lazyAnnotationClass()

    override suspend fun values() = lazyValues()

    override suspend fun matches(annotationClass: String): Boolean {
        return info.className == annotationClass
    }

    private suspend fun fixValue(value: Any): Any? {
        return when (value) {
            is Type -> classpath.findClassOrNull(value.className)
            is String, is Short, is Byte, is Boolean, is Long, is Double, is Float -> value
            else -> throw IllegalStateException("Unsupported type $value")
        }
    }
}