package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRTypedField
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.TypeResolution
import org.opentaint.ir.api.anyType
import org.opentaint.ir.impl.signature.SType
import org.opentaint.ir.impl.signature.STypeVariable
import org.opentaint.ir.impl.signature.TypeResolutionImpl
import org.opentaint.ir.impl.signature.TypeSignature
import org.opentaint.ir.impl.suspendableLazy

open class JIRClassTypeImpl(
    override val jirClass: JIRClassOrInterface,
    private val resolution: TypeResolution = TypeSignature.of(jirClass.signature),
    private val parametrization: List<SType>? = null,
    override val nullable: Boolean
) : JIRClassType {

    val classBindings: JIRTypeBindings

    init {
        if (parametrization != null && resolution is TypeResolutionImpl && resolution.typeVariable.size != parametrization.size) {
            val msg = "Expected ${resolution.typeVariable.joinToString()} but was ${parametrization.joinToString()}"
            throw IllegalStateException(msg)
        }
        val bindings = ifSyncSignature {
            it.typeVariable.mapIndexed { index, declaration ->
                declaration.symbol to (parametrization?.get(index) ?: STypeVariable(declaration.symbol))
            }.toMap()
        } ?: emptyMap()
        classBindings = JIRTypeBindings(bindings)
    }

    override val classpath get() = jirClass.classpath

    override val typeName: String
        get() = jirClass.name

    private val originParametrizationGetter = suspendableLazy {
        ifSignature {
            classpath.typeDeclarations(it.typeVariable)
        } ?: emptyList()
    }

    private val parametrizationGetter = suspendableLazy {
        originalParametrization().mapIndexed { index, declaration ->
            declaration.symbol to (parametrization?.get(index)?.let { classpath.typeOf(it) as JIRRefType} ?: JIRTypeVariableImpl(declaration.symbol, true, classpath.anyType()))
        }.toMap()
    }

    override suspend fun originalParametrization() = originParametrizationGetter()

    override suspend fun parametrization() = parametrizationGetter()

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

    override suspend fun methods(): List<JIRTypedMethod> {
        return jirClass.methods.map {
            JIRTypedMethodImpl(enclosingType = this, it, classBindings)
        }
    }

    override suspend fun fields(): List<JIRTypedField> {
        return jirClass.fields.map {
            JIRTypedFieldImpl(enclosingType = this, it, classBindings = classBindings)
        }
    }

    override fun notNullable() = JIRClassTypeImpl(jirClass, resolution, parametrization, false)

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

    private fun <T> ifSyncSignature(map: (TypeResolutionImpl) -> T?): T? {
        return when (resolution) {
            is TypeResolutionImpl -> map(resolution)
            else -> null
        }
    }


}