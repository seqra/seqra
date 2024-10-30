package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRArrayType
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType

class JIRArrayClassTypesImpl(
    override val elementType: JIRType,
    override val nullable: Boolean = true,
    private val anyType: JIRClassType
) : JIRArrayType {

    override val typeName = elementType.typeName + "[]"

    override fun notNullable(): JIRRefType {
        return JIRArrayClassTypesImpl(elementType, false, anyType)
    }

    override val classpath: JIRClasspath
        get() = elementType.classpath

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JIRArrayClassTypesImpl

        if (elementType != other.elementType) return false

        return true
    }

    override fun hashCode(): Int {
        return elementType.hashCode()
    }

}