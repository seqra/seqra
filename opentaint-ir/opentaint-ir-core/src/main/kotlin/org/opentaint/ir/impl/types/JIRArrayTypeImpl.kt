package org.opentaint.opentaint-ir.impl.types

import org.opentaint.opentaint-ir.api.JIRArrayType
import org.opentaint.opentaint-ir.api.JIRClasspath
import org.opentaint.opentaint-ir.api.JIRRefType
import org.opentaint.opentaint-ir.api.JIRType

class JIRArrayTypeImpl(
    override val elementType: JIRType,
    override val nullable: Boolean? = null
) : JIRArrayType {

    override val typeName = elementType.typeName + "[]"

    override fun copyWithNullability(nullability: Boolean?): JIRRefType {
        return JIRArrayTypeImpl(elementType, nullability)
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

}