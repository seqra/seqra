package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRTypedField
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.TypeResolution
import org.opentaint.ir.api.toType
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

    private val typeBindings: JIRTypeBindings

    init {
        if (parametrization != null && resolution is TypeResolutionImpl && resolution.typeVariables.size != parametrization.size) {
            val msg = "Expected ${resolution.typeVariables.joinToString()} but was ${parametrization.joinToString()}"
            throw IllegalStateException(msg)
        }

        val bindings = ifSyncSignature {
            it.typeVariables.mapIndexed { index, declaration ->
                declaration.symbol to (parametrization?.get(index) ?: STypeVariable(declaration.symbol))
            }.toMap()
        } ?: emptyMap()

        val declarations = ifSyncSignature {
            it.typeVariables.associateBy { it.symbol }
        } ?: emptyMap()

        typeBindings = JIRTypeBindings(bindings, declarations)
    }

    override val classpath get() = jirClass.classpath

    override val typeName: String
        get() {
            if (parametrization == null) {
                val generics = ifSyncSignature { it.typeVariables.joinToString() } ?: return jirClass.name
                return jirClass.name + ("<$generics>".takeIf { generics.isNotEmpty() } ?: "")
            }
            return jirClass.name + ("<${parametrization.joinToString { it.displayName }}>".takeIf { parametrization.isNotEmpty() } ?: "")
        }

    private val originParametrizationGetter = suspendableLazy {
        ifSignature {
            classpath.typeDeclarations(it.typeVariables, JIRTypeBindings.empty)
        } ?: emptyList()
    }

    private val parametrizationGetter = suspendableLazy {
        originalParametrization().associate { original ->
            val direct = typeBindings.findDirectBinding(original.symbol)
            if (direct != null) {
                original.symbol to direct.apply(typeBindings, original.symbol).toJcRefType()
            } else {
                original.symbol to typeBindings.resolve(original.symbol).apply(typeBindings, null).toJcRefType()
            }
        }
    }

    override suspend fun originalParametrization() = originParametrizationGetter()

    override suspend fun parametrization() = parametrizationGetter()

    override suspend fun superType(): JIRRefType? {
        return ifSignature {
            classpath.typeOf(it.superClass, typeBindings) as? JIRRefType
        } ?: jirClass.superclass()?.toType()
    }

    override suspend fun interfaces(): List<JIRRefType> {
        return ifSignature {
            jirClass.interfaces().map { it.toType() }
        } ?: emptyList()
    }

    override suspend fun outerType(): JIRRefType? {
        return jirClass.outerClass()?.toType()
    }

    override suspend fun outerMethod(): JIRTypedMethod? {
        return jirClass.outerMethod()?.let {
            JIRTypedMethodImpl(enclosingType = it.enclosingClass.toType(), it, JIRTypeBindings.empty)
        }
    }

    override suspend fun innerTypes(): List<JIRRefType> = TODO("Not yet implemented")

    override suspend fun methods(): List<JIRTypedMethod> {
        return jirClass.methods.map {
            JIRTypedMethodImpl(enclosingType = this, it, typeBindings)
        }
    }

    override suspend fun fields(): List<JIRTypedField> {
        return jirClass.fields.map {
            JIRTypedFieldImpl(enclosingType = this, it, typeBindings = typeBindings)
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

    private suspend fun SType.toJcRefType(): JIRRefType {
        return classpath.typeOf(this, typeBindings) as JIRRefType
    }

}