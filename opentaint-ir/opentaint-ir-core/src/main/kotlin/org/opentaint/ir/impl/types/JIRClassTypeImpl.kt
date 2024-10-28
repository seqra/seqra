package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRTypedField
import org.opentaint.ir.api.JIRTypedMethod

class JIRClassTypeImpl(override val jirClass: JIRClassOrInterface, override val nullable: Boolean) : JIRClassType {

    override val classpath: JIRClasspath
        get() = jirClass.classpath

    override val typeName: String
        get() = jirClass.name

    override suspend fun superType(): JIRRefType = TODO("Not yet implemented")

    override suspend fun interfaces(): JIRRefType = TODO("Not yet implemented")

    override suspend fun outerType(): JIRRefType? = TODO("Not yet implemented")

    override suspend fun outerMethod(): JIRTypedMethod? = TODO("Not yet implemented")

    override suspend fun innerTypes(): List<JIRRefType> = TODO("Not yet implemented")

    override val methods: List<JIRTypedMethod>
        get() = TODO("Not yet implemented")
    override val fields: List<JIRTypedField>
        get() = TODO("Not yet implemented")

    override fun notNullable() = JIRClassTypeImpl(jirClass, false)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JIRClassTypeImpl

        if (nullable != other.nullable) return false
        if (typeName != other.typeName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nullable.hashCode()
        result = 31 * result + typeName.hashCode()
        return result
    }

}