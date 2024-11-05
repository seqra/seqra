package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRTypedField
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.isProtected
import org.opentaint.ir.api.isPublic
import org.opentaint.ir.api.toType
import org.opentaint.ir.impl.suspendableLazy
import org.opentaint.ir.impl.types.signature.JvmClassRefType
import org.opentaint.ir.impl.types.signature.JvmParameterizedType
import org.opentaint.ir.impl.types.signature.JvmType
import org.opentaint.ir.impl.types.signature.TypeResolutionImpl
import org.opentaint.ir.impl.types.signature.TypeSignature
import org.opentaint.ir.impl.types.substition.JIRSubstitutor
import org.opentaint.ir.impl.types.substition.substitute

open class JIRClassTypeImpl(
    override val jirClass: JIRClassOrInterface,
    private val outerType: JIRClassTypeImpl? = null,
    internal val substitutor: JIRSubstitutor = JIRSubstitutor.empty,
    override val nullable: Boolean
) : JIRClassType {

    constructor(
        jirClass: JIRClassOrInterface,
        outerType: JIRClassTypeImpl? = null,
        parameters: List<JvmType>,
        nullable: Boolean
    ) : this(jirClass, outerType, jirClass.substitute(parameters), nullable)

    private val resolutionImpl by lazy(LazyThreadSafetyMode.NONE) { TypeSignature.of(jirClass) as? TypeResolutionImpl }
    private val declaredTypeParameters by lazy(LazyThreadSafetyMode.NONE) { jirClass.typeParameters }

    override val classpath get() = jirClass.classpath

    override val typeName: String
        get() {
            val generics = if (substitutor.substitutions.isEmpty()) {
                declaredTypeParameters.joinToString() { it.symbol }
            } else {
                declaredTypeParameters.joinToString() {
                    substitutor.substitution(it)?.displayName ?: it.symbol
                }
            }
            return jirClass.name + ("<${generics}>".takeIf { generics.isNotEmpty() } ?: "")
        }

    private val originParametrizationGetter = suspendableLazy {
        declaredTypeParameters.map { it.asJcDeclaration(jirClass) }
    }

    private val parametrizationGetter = suspendableLazy {
        declaredTypeParameters.map { declaration ->
            val jvmType = substitutor.substitution(declaration)
            if (jvmType != null) {
                classpath.typeOf(jvmType) as JIRRefType
            } else {
                JIRTypeVariableImpl(classpath, declaration.asJcDeclaration(jirClass), true)
            }
        }
    }

    override suspend fun typeParameters() = originParametrizationGetter()

    override suspend fun typeArguments() = parametrizationGetter()

    override suspend fun superType(): JIRClassType? {
        val superClass = jirClass.superclass() ?: return null
        return resolutionImpl?.let {
            val newSubstitutor = superSubstitutor(superClass, it.superClass)
            JIRClassTypeImpl(superClass, outerType, newSubstitutor, nullable)
        } ?: superClass.toType()
    }

    override suspend fun interfaces(): List<JIRClassType> {
        return jirClass.interfaces().map { iface ->
            val ifaceType = resolutionImpl?.interfaceType?.firstOrNull { it.isReferencesClass(iface.name) }
            if (ifaceType != null) {
                val newSubstitutor = superSubstitutor(iface, ifaceType)
                JIRClassTypeImpl(iface, null, newSubstitutor, nullable)
            } else {
                iface.toType()
            }
        }
    }

    override suspend fun innerTypes(): List<JIRClassType> {
        return jirClass.innerClasses().map {
            JIRClassTypeImpl(it, this, substitutor, true)
        }
    }

    override suspend fun methods(): List<JIRTypedMethod> {
        //let's calculate visible methods from super types
        return jirClass.typedMethods(true)
    }

    override suspend fun fields(): List<JIRTypedField> {
        return jirClass.typedFields(true)
    }

    override fun notNullable() = JIRClassTypeImpl(jirClass, outerType, substitutor, false)

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

    private suspend fun JIRClassOrInterface.typedMethods(all: Boolean): List<JIRTypedMethod> {
        val methodSet = if (all) {
            methods
        } else {
            methods.filter { it.isPublic || it.isProtected } // add package check
        }
        return methodSet.map {
            JIRTypedMethodImpl(this@JIRClassTypeImpl, it, substitutor)
        }
    }

    private suspend fun JIRClassOrInterface.typedFields(all: Boolean): List<JIRTypedField> {
        val fieldSet = if (all) {
            fields
        } else {
            fields.filter { it.isPublic || it.isProtected } // add package check
        }
        val directSet = fieldSet.map {
            JIRTypedFieldImpl(this@JIRClassTypeImpl, it, substitutor)
        }
        return directSet
    }


    private fun superSubstitutor(superClass: JIRClassOrInterface, superType: JvmType): JIRSubstitutor {
        val superParameters = superClass.typeParameters
        val substitutions = (superType as? JvmParameterizedType)?.parameterTypes
        if (substitutions == null || superParameters.size != substitutions.size) {
            return JIRSubstitutor.empty
        }
        return substitutor.fork(superParameters.mapIndexed { index, declaration -> declaration to substitutions[index] }
            .toMap())

    }

}

fun JvmType.isReferencesClass(name: String): Boolean {
    return when (val type = this) {
        is JvmClassRefType -> type.name == name
        is JvmParameterizedType -> type.name == name
        is JvmParameterizedType.JvmNestedType -> type.name == name
        else -> false
    }
}