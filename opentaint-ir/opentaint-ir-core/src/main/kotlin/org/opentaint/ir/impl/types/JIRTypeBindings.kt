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
            PredefinedPrimitives.of(jvmType.ref, this, jvmType.annotations)
                ?: throw IllegalStateException("primitive type ${jvmType.ref} not found")
        }

        is JvmClassRefType -> typeOf(findClass(jvmType.name), jvmType.isNullable, jvmType.annotations)
        is JvmArrayType -> arrayTypeOf(typeOf(jvmType.elementType), jvmType.isNullable, jvmType.annotations)
        is JvmParameterizedType -> {
            val params = parameters ?: jvmType.parameterTypes
            when {
                params.isNotEmpty() -> JIRClassTypeImpl(
                        this,
                        jvmType.name,
                        null,
                        params,
                        nullable = jvmType.isNullable,
                        jvmType.annotations
                    )
                // raw types
                else -> typeOf(findClass(jvmType.name)).copyWithNullability(jvmType.isNullable)
            }
        }

        is JvmParameterizedType.JvmNestedType -> {
            val outerParameters = (jvmType.ownerType as? JvmParameterizedType)?.parameterTypes
            val outerType = typeOf(jvmType.ownerType, parameters ?: outerParameters)
            JIRClassTypeImpl(
                this,
                jvmType.name,
                outerType as JIRClassTypeImpl,
                jvmType.parameterTypes,
                nullable = jvmType.isNullable,
                jvmType.annotations
            )
        }

        is JvmTypeVariable -> {
            val declaration = jvmType.declaration
            if (declaration != null) {
                JIRTypeVariableImpl(this, declaration.asJIRDeclaration(declaration.owner), jvmType.isNullable, jvmType.annotations)
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
