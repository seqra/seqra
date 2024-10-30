package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRTypedField
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.TypeResolution
import org.opentaint.ir.impl.signature.TypeResolutionImpl
import org.opentaint.ir.impl.signature.TypeSignature

class JIRClassTypeImpl(
    override val jirClass: JIRClassOrInterface,
    private val resolution: TypeResolution = TypeSignature.of(jirClass.signature),
    override val nullable: Boolean
) : JIRClassType {

    override val classpath: JIRClasspath
        get() = jirClass.classpath

    override val typeName: String
        get() = jirClass.name

    override suspend fun superType(): JIRRefType? {
        return ifSignature {
            classpath.typeOf(it.superClass) as? JIRRefType
        } ?: jirClass.superclass()?.let { classpath.typeOf(it) }
    }

    override suspend fun interfaces(): List<JIRRefType> {
        return ifSignature {
            jirClass.interfaces().map { classpath.typeOf(it) }
        } ?: emptyList()
    }

    override suspend fun outerType(): JIRRefType? = TODO("Not yet implemented")

    override suspend fun outerMethod(): JIRTypedMethod? = TODO("Not yet implemented")

    override suspend fun innerTypes(): List<JIRRefType> = TODO("Not yet implemented")

    override val methods: List<JIRTypedMethod>
        get() = jirClass.methods.map {
            JIRTypedMethodImpl(ownerType = this, it)
        }

    override val fields: List<JIRTypedField>
        get() = TODO("Not yet implemented")

    override fun notNullable() = JIRClassTypeImpl(jirClass, resolution, false)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JIRClassTypeImpl

        if (nullable != other.nullable) return false
        if (typeName != other.typeName) return false

        return true
    }

    override fun hashCode(): Int {
        val result = nullable.hashCode()
        return 31 * result + typeName.hashCode()
    }

    private suspend fun <T> ifSignature(map: suspend (TypeResolutionImpl) -> T?): T? {
        return when (resolution) {
            is TypeResolutionImpl -> map(resolution)
            else -> null
        }
    }


}