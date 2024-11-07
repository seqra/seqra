package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRAccessible
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypeVariableDeclaration
import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.api.anyType
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.types.signature.JvmArrayType
import org.opentaint.ir.impl.types.signature.JvmBoundWildcard
import org.opentaint.ir.impl.types.signature.JvmClassRefType
import org.opentaint.ir.impl.types.signature.JvmParameterizedType
import org.opentaint.ir.impl.types.signature.JvmPrimitiveType
import org.opentaint.ir.impl.types.signature.JvmType
import org.opentaint.ir.impl.types.signature.JvmTypeVariable
import org.opentaint.ir.impl.types.signature.JvmUnboundWildcard

internal suspend fun JIRClasspath.typeOf(jvmType: JvmType, parameters: List<JvmType>? = null): JIRType {
    return when (jvmType) {
        is JvmPrimitiveType -> {
            PredefinedPrimitives.of(jvmType.ref, this)
                ?: throw IllegalStateException("primitive type ${jvmType.ref} not found")
        }

        is JvmClassRefType -> typeOf(findClass(jvmType.name))
        is JvmArrayType -> arrayTypeOf(typeOf(jvmType.elementType))
        is JvmParameterizedType -> {
            val clazz = findClass(jvmType.name)
            JIRClassTypeImpl(
                clazz,
                null,
                parameters ?: jvmType.parameterTypes,
                nullable = true
            )
        }

        is JvmParameterizedType.JvmNestedType -> {
            val clazz = findClass(jvmType.name)
            val outerParameters = (jvmType.ownerType as? JvmParameterizedType)?.parameterTypes
            val outerType = typeOf(jvmType.ownerType, parameters ?: outerParameters)
            JIRClassTypeImpl(
                clazz,
                outerType as JIRClassTypeImpl,
                jvmType.parameterTypes,
                nullable = true
            )
        }

        is JvmTypeVariable -> {
            val declaration = jvmType.declaration
            if (declaration != null) {
                JIRTypeVariableImpl(this, declaration.asJcDeclaration(declaration.owner), true)
            } else {
                anyType()
            }
        }

        is JvmUnboundWildcard -> JIRUnboundWildcardImpl(this)
        is JvmBoundWildcard.JvmUpperBoundWildcard -> JIRBoundedWildcardImpl(
            upperBounds = listOf(typeOf(jvmType.bound) as JIRRefType), lowerBounds = emptyList(), true
        )

        is JvmBoundWildcard.JvmLowerBoundWildcard -> JIRBoundedWildcardImpl(
            upperBounds = emptyList(), lowerBounds = listOf(typeOf(jvmType.bound) as JIRRefType), true
        )
    }
}

class JIRTypeVariableDeclarationImpl(
    override val symbol: String,
    override val bounds: List<JIRRefType>,
    override val owner: JIRAccessible
) : JIRTypeVariableDeclaration
