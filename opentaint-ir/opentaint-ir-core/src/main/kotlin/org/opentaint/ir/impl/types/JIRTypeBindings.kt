package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRAccessible
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypeVariableDeclaration
import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.ext.objectType
import org.opentaint.ir.impl.types.signature.JvmArrayType
import org.opentaint.ir.impl.types.signature.JvmBoundWildcard
import org.opentaint.ir.impl.types.signature.JvmClassRefType
import org.opentaint.ir.impl.types.signature.JvmParameterizedType
import org.opentaint.ir.impl.types.signature.JvmPrimitiveType
import org.opentaint.ir.impl.types.signature.JvmType
import org.opentaint.ir.impl.types.signature.JvmTypeVariable
import org.opentaint.ir.impl.types.signature.JvmUnboundWildcard

internal fun JIRClasspath.typeOf(jvmType: JvmType, parameters: List<JvmType>? = null): JIRType {
    return when (jvmType) {
        is JvmPrimitiveType -> {
            PredefinedPrimitives.of(jvmType.ref, this)
                ?: throw IllegalStateException("primitive type ${jvmType.ref} not found")
        }

        is JvmClassRefType -> typeOf(findClass(jvmType.name)).copyWithNullability(jvmType.isNullable)
        is JvmArrayType -> arrayTypeOf(typeOf(jvmType.elementType)).copyWithNullability(jvmType.isNullable)
        is JvmParameterizedType -> {
            JIRClassTypeImpl(
                this,
                jvmType.name,
                null,
                parameters ?: jvmType.parameterTypes,
                nullable = jvmType.isNullable
            )
        }

        is JvmParameterizedType.JvmNestedType -> {
            val outerParameters = (jvmType.ownerType as? JvmParameterizedType)?.parameterTypes
            val outerType = typeOf(jvmType.ownerType, parameters ?: outerParameters)
            JIRClassTypeImpl(
                this,
                jvmType.name,
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
                objectType
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
    private val classpath: JIRClasspath,
    private val jvmBounds: List<JvmType>,
    override val owner: JIRAccessible
) : JIRTypeVariableDeclaration {
    override val bounds: List<JIRRefType> get() = jvmBounds.map { classpath.typeOf(it) as JIRRefType }
}
