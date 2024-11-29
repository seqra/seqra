package org.opentaint.opentaint-ir.impl.types

import org.opentaint.opentaint-ir.api.JIRAccessible
import org.opentaint.opentaint-ir.api.JIRClasspath
import org.opentaint.opentaint-ir.api.JIRRefType
import org.opentaint.opentaint-ir.api.JIRType
import org.opentaint.opentaint-ir.api.JIRTypeVariableDeclaration
import org.opentaint.opentaint-ir.api.PredefinedPrimitives
import org.opentaint.opentaint-ir.api.ext.anyType
import org.opentaint.opentaint-ir.api.ext.findClass
import org.opentaint.opentaint-ir.impl.types.signature.JvmArrayType
import org.opentaint.opentaint-ir.impl.types.signature.JvmBoundWildcard
import org.opentaint.opentaint-ir.impl.types.signature.JvmClassRefType
import org.opentaint.opentaint-ir.impl.types.signature.JvmParameterizedType
import org.opentaint.opentaint-ir.impl.types.signature.JvmPrimitiveType
import org.opentaint.opentaint-ir.impl.types.signature.JvmType
import org.opentaint.opentaint-ir.impl.types.signature.JvmTypeVariable
import org.opentaint.opentaint-ir.impl.types.signature.JvmUnboundWildcard

internal fun JIRClasspath.typeOf(jvmType: JvmType, parameters: List<JvmType>? = null): JIRType {
    return when (jvmType) {
        is JvmPrimitiveType -> {
            PredefinedPrimitives.of(jvmType.ref, this)
                ?: throw IllegalStateException("primitive type ${jvmType.ref} not found")
        }

        is JvmClassRefType -> typeOf(findClass(jvmType.name)).copyWithNullability(jvmType.isNullable)
        is JvmArrayType -> arrayTypeOf(typeOf(jvmType.elementType)).copyWithNullability(jvmType.isNullable)
        is JvmParameterizedType -> {
            val clazz = findClass(jvmType.name)
            JIRClassTypeImpl(
                clazz,
                null,
                parameters ?: jvmType.parameterTypes,
                nullable = jvmType.isNullable
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
                nullable = jvmType.isNullable
            )
        }

        is JvmTypeVariable -> {
            val declaration = jvmType.declaration
            if (declaration != null) {
                JIRTypeVariableImpl(this, declaration.asJIRDeclaration(declaration.owner), jvmType.isNullable)
            } else {
                anyType()
            }
        }

        is JvmUnboundWildcard -> JIRUnboundWildcardImpl(this)
        is JvmBoundWildcard.JvmUpperBoundWildcard -> JIRBoundedWildcardImpl(
            upperBounds = listOf(typeOf(jvmType.bound) as JIRRefType), lowerBounds = emptyList()
        )

        is JvmBoundWildcard.JvmLowerBoundWildcard -> JIRBoundedWildcardImpl(
            upperBounds = emptyList(), lowerBounds = listOf(typeOf(jvmType.bound) as JIRRefType)
        )
    }
}

class JIRTypeVariableDeclarationImpl(
    override val symbol: String,
    override val bounds: List<JIRRefType>,
    override val owner: JIRAccessible
) : JIRTypeVariableDeclaration
