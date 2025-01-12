package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.ext.enumValues
import org.opentaint.ir.impl.types.AnnotationInfo
import org.opentaint.ir.impl.types.AnnotationValue
import org.opentaint.ir.impl.types.AnnotationValueList
import org.opentaint.ir.impl.types.ClassRef
import org.opentaint.ir.impl.types.EnumRef
import org.opentaint.ir.impl.types.PrimitiveValue
import kotlin.LazyThreadSafetyMode.PUBLICATION

class JIRAnnotationImpl(
    private val info: AnnotationInfo,
    private val classpath: JIRClasspath
) : JIRAnnotation {

    override val jIRClass by lazy(PUBLICATION) {
        classpath.findClassOrNull(info.className)
    }

    override val values by lazy(PUBLICATION) {
        val size = info.values.size
        if (size > 0) {
            info.values.associate { it.first to fixValue(it.second) }
        } else {
            emptyMap()
        }
    }

    override val visible: Boolean get() = info.visible
    override val name: String get() = info.className

    override fun matches(className: String): Boolean {
        return info.className == className
    }

    private fun fixValue(value: AnnotationValue): Any? {
        return when (value) {
            is PrimitiveValue -> value.value
            is ClassRef -> classpath.findClassOrNull(value.className)
            is EnumRef -> classpath.findClassOrNull(value.className)?.enumValues
                ?.firstOrNull { it.name == value.enumName }

            is AnnotationInfo -> JIRAnnotationImpl(value, classpath)
            is AnnotationValueList -> value.annotations.map { fixValue(it) }
        }
    }

    override fun toString(): String {
        return "@${name}(${values.entries.joinToString { "${it.key}=${it.value})" }})"
    }
}