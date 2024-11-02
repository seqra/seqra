package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRTypedField
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.toType
import org.opentaint.ir.impl.signature.SType
import org.opentaint.ir.impl.signature.TypeResolutionImpl
import org.opentaint.ir.impl.signature.TypeSignature
import org.opentaint.ir.impl.suspendableLazy

open class JIRClassTypeImpl(
    override val jirClass: JIRClassOrInterface,
    private val outerType: JIRClassType? = null,
    private val typeBindings: JIRTypeBindings,
    override val nullable: Boolean
) : JIRClassType {

    private val resolutionImpl = TypeSignature.of(jirClass.signature) as? TypeResolutionImpl

    override val classpath get() = jirClass.classpath

    override val typeName: String
        get() {
            if (typeBindings.parametrization == null) {
                val generics = resolutionImpl?.typeVariables?.joinToString() ?: return jirClass.name
                return jirClass.name + ("<$generics>".takeIf { generics.isNotEmpty() } ?: "")
            }
            return jirClass.name + ("<${typeBindings.parametrization.joinToString { it.displayName }}>".takeIf { typeBindings.parametrization.isNotEmpty() }
                ?: "")
        }

    private val originParametrizationGetter = suspendableLazy {
        resolutionImpl?.let {
            classpath.typeDeclarations(it.typeVariables, JIRTypeBindings.empty)
        } ?: emptyList()
    }

    private val parametrizationGetter = suspendableLazy {
        originalParametrization().map { original ->
            val direct = typeBindings.findTypeBinding(original.symbol)
            direct?.apply(typeBindings, original.symbol)?.toJcRefType()
                ?: typeBindings.resolve(original.symbol).apply(typeBindings, null).toJcRefType()
        }
    }

    override suspend fun originalParametrization() = originParametrizationGetter()

    override suspend fun parametrization() = parametrizationGetter()

    override suspend fun superType(): JIRClassType? {
        return resolutionImpl?.let {
            classpath.typeOf(it.superClass, typeBindings) as? JIRClassType
        } ?: jirClass.superclass()?.toType()
    }

    override suspend fun interfaces(): List<JIRRefType> {
        return resolutionImpl?.let {
            it.interfaceType.map { classpath.typeOf(it, typeBindings) as JIRRefType }
        } ?: jirClass.interfaces().map { it.toType() } ?: emptyList()
    }

    override suspend fun outerType(): JIRClassType? {
        return jirClass.outerClass()?.toType()
    }

    override suspend fun outerMethod(): JIRTypedMethod? {
        return jirClass.outerMethod()?.let {
            JIRTypedMethodImpl(enclosingType = it.enclosingClass.toType(), it, JIRTypeBindings.empty)
        }
    }

    override suspend fun innerTypes(): List<JIRClassType> {
        return jirClass.innerClasses().map {
            JIRClassTypeImpl(it, this, JIRTypeBindings.ofClass(it, typeBindings), true)
        }
    }

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

    override fun notNullable() = JIRClassTypeImpl(jirClass, outerType, typeBindings, false)

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

    private suspend fun SType.toJcRefType(): JIRRefType {
        return typeBindings.toJcRefType(this, classpath)
    }

}