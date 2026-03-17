package org.opentaint.ir.impl.types

import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRArrayType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRRefType
import org.opentaint.ir.api.jvm.JIRType

class JIRArrayTypeImpl(
    override val elementType: JIRType,
    override val nullable: Boolean? = null,
    override val annotations: List<JIRAnnotation> = listOf()
) : JIRArrayType {

    override val typeName = elementType.typeName + "[]"

    override val dimensions: Int
        get() = 1 + when (elementType) {
            is JIRArrayType -> elementType.dimensions
            else -> 0
        }

    override fun copyWithNullability(nullability: Boolean?): JIRRefType {
        return JIRArrayTypeImpl(elementType, nullability, annotations)
    }

    override val classpath: JIRClasspath
        get() = elementType.classpath

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JIRArrayTypeImpl

        if (elementType != other.elementType) return false

        return true
    }

    override fun hashCode(): Int {
        return elementType.hashCode()
    }

    override fun toString(): String = typeName

    override fun copyWithAnnotations(annotations: List<JIRAnnotation>): JIRType {
        return JIRArrayTypeImpl(elementType, nullable, annotations)
    }
}
