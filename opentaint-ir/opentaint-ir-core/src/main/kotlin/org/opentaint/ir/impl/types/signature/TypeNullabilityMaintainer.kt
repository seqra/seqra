
package org.opentaint.ir.impl.types.signature

import kotlinx.metadata.KmType
import kotlinx.metadata.KmTypeParameter
import org.opentaint.ir.impl.bytecode.isNullable

fun JvmType.copyWithNullability(nullability: Boolean?): JvmType =
    when (this) {
        is JvmArrayType -> JvmArrayType(elementType, nullability)
        is JvmClassRefType -> JvmClassRefType(name, nullability)
        is JvmParameterizedType.JvmNestedType -> JvmParameterizedType.JvmNestedType(name, parameterTypes, ownerType, nullability)
        is JvmParameterizedType -> JvmParameterizedType(name, parameterTypes, nullability)

        is JvmTypeVariable -> JvmTypeVariable(symbol, nullability).also {
            it.declaration = declaration
        }

        is JvmWildcard -> {
            if (nullability != true)
                error("Attempting to make wildcard not-nullable, which are always nullable by convention")
            this
        }

        is JvmPrimitiveType -> {
            if (nullability != false)
                error("Attempting to make a nullable primitive")
            this
        }
    }

internal fun JvmType.relaxWithKmType(kmType: KmType): JvmType =
    when (this) {
        is JvmArrayType -> {
            // NB: kmType may have zero (for primitive arrays) or one (for object arrays) argument
            val updatedElementType = kmType.arguments.singleOrNull()?.type?.let {
                elementType.relaxWithKmType(it)
            } ?: elementType

            JvmArrayType(updatedElementType, kmType.isNullable)
        }

        is JvmParameterizedType.JvmNestedType -> {
            val relaxedParameterTypes = parameterTypes.relaxAll(kmType.arguments.map { it.type })
            JvmParameterizedType.JvmNestedType(name, relaxedParameterTypes, ownerType, kmType.isNullable)
        }

        is JvmParameterizedType -> {
            val relaxedParameterTypes = parameterTypes.relaxAll(kmType.arguments.map { it.type })
            JvmParameterizedType(name, relaxedParameterTypes, kmType.isNullable)
        }

        is JvmBoundWildcard.JvmUpperBoundWildcard -> {
            // Kotlin metadata is constructed in terms of projections => there is no explicit type for wildcard.
            // Therefore, we don't look for kmType.arguments and relax bound with kmType directly, not with kmType.arguments.single()
            // Same applies to JvmLowerBoundWildcard.relaxWithKmType
            JvmBoundWildcard.JvmUpperBoundWildcard(bound.relaxWithKmType(kmType))
        }

        is JvmBoundWildcard.JvmLowerBoundWildcard ->
            JvmBoundWildcard.JvmLowerBoundWildcard(bound.relaxWithKmType(kmType))

        else -> copyWithNullability(kmType.isNullable) // default implementation for many of JvmTypes
    }

internal fun JvmTypeParameterDeclarationImpl.relaxWithKmTypeParameter(kmTypeParameter: KmTypeParameter): JvmTypeParameterDeclaration {
    val newBounds = bounds?.zip(kmTypeParameter.upperBounds) { bound, kmType ->
        bound.relaxWithKmType(kmType)
    }
    return JvmTypeParameterDeclarationImpl(symbol, owner, newBounds)
}

private fun Iterable<JvmType>.relaxAll(kmTypes: List<KmType?>): List<JvmType> =
    zip(kmTypes) { type, kmType ->
        kmType?.let {
            type.relaxWithKmType(it)
        } ?: type
    }