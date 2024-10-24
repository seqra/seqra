package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.enumValues
import org.opentaint.ir.impl.SuspendableLazy
import org.opentaint.ir.impl.suspendableLazy
import org.opentaint.ir.impl.types.AnnotationInfo
import org.opentaint.ir.impl.types.AnnotationValue
import org.opentaint.ir.impl.types.AnnotationValueList
import org.opentaint.ir.impl.types.ClassRef
import org.opentaint.ir.impl.types.EnumRef
import org.opentaint.ir.impl.types.PrimitiveValue

class JIRAnnotationImpl(
    private val info: AnnotationInfo,
    private val classpath: JIRClasspath
) : JIRAnnotation {

    private val lazyAnnotationClass = suspendableLazy {
        classpath.findClassOrNull(info.className)
    }

    private val lazyValues: SuspendableLazy<Map<String, Any?>> = suspendableLazy {
        val size = info.values.size
        if (size > 0) {
            info.values.map { it.first to fixValue(it.second) }.toMap()
        } else {
            emptyMap()
        }
    }

    override val visible: Boolean get() = info.visible
    override val name: String get() = info.className

    override suspend fun jirClass() = lazyAnnotationClass()

    override suspend fun values() = lazyValues()

    override fun matches(className: String): Boolean {
        return info.className == className
    }

    private suspend fun fixValue(value: AnnotationValue): Any? {
        return when (value) {
            is PrimitiveValue -> value.value
            is ClassRef -> classpath.findClassOrNull(value.className)
            is EnumRef -> classpath.findClassOrNull(value.className)?.enumValues()
                ?.firstOrNull { it.name == value.enumName }

            is AnnotationInfo -> JIRAnnotationImpl(value, classpath)
            is AnnotationValueList -> value.annotations.map { fixValue(it) }
        }
    }
}