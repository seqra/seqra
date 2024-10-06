package org.opentaint.ir.impl.types

import org.opentaint.ir.api.AnnotationId
import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.api.enumValues
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

    private suspend fun fixValue(value: AnnotationValue): Any? {
        return when (value) {
            is PrimitiveValue -> value.value
            is ClassRef -> classpath.findClassOrNull(value.name)
            is EnumRef -> classpath.findClassOrNull(value.name)?.enumValues()?.firstOrNull { it.name == value.name }
            is AnnotationInfo -> AnnotationIdImpl(value, classpath)
            is AnnotationValues -> value.annotations.map { fixValue(it) }
        }
    }
}