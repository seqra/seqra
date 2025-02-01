package org.opentaint.ir.impl.types.signature

import kotlinx.metadata.KmType
import kotlinx.metadata.KmTypeParameter
import org.opentaint.ir.impl.bytecode.isNullable

/**
 * Recursively visits type and take all info about nullability from given kmType
 */
internal object JvmTypeKMetadataUpdateVisitor : JvmTypeVisitor<KmType> {
    override fun visitUpperBound(type: JvmBoundWildcard.JvmUpperBoundWildcard, context: KmType): JvmType {
        return JvmBoundWildcard.JvmUpperBoundWildcard(visitType(type.bound, context))
    }

    override fun visitLowerBound(type: JvmBoundWildcard.JvmLowerBoundWildcard, context: KmType): JvmType {
        return JvmBoundWildcard.JvmLowerBoundWildcard(visitType(type.bound, context))
    }

    override fun visitArrayType(type: JvmArrayType, context: KmType): JvmType {
        // NB: kmType may have zero (for primitive arrays) or one (for object arrays) argument
        val updatedElementType = context.arguments.singleOrNull()?.type?.let {
            visitType(type.elementType, it)
        } ?: type.elementType

        return JvmArrayType(updatedElementType, context.isNullable, type.annotations)
    }

    override fun visitTypeVariable(type: JvmTypeVariable, context: KmType): JvmType {
        return visitFinal(type, context)
    }

    override fun visitClassRef(type: JvmClassRefType, context: KmType): JvmType {
        return visitFinal(type, context)
    }

    override fun visitNested(type: JvmParameterizedType.JvmNestedType, context: KmType): JvmType {
        val relaxedParameterTypes = visitList(type.parameterTypes, context.arguments.map { it.type })
        return JvmParameterizedType.JvmNestedType(
            type.name,
            relaxedParameterTypes,
            type.ownerType,
            context.isNullable,
            type.annotations
        )
    }

    override fun visitParameterizedType(type: JvmParameterizedType, context: KmType): JvmType {
        val relaxedParameterTypes = visitList(type.parameterTypes, context.arguments.map { it.type })
        return JvmParameterizedType(type.name, relaxedParameterTypes, context.isNullable, type.annotations)
    }

    fun visitDeclaration(
        declaration: JvmTypeParameterDeclaration,
        context: KmTypeParameter
    ): JvmTypeParameterDeclaration {
        val newBounds = declaration.bounds?.zip(context.upperBounds) { bound, kmType ->
            visitType(bound, kmType)
        }
        return JvmTypeParameterDeclarationImpl(declaration.symbol, declaration.owner, newBounds)
    }

    private fun visitList(types: List<JvmType>, kmTypes: List<KmType?>): List<JvmType> {
        return types.zip(kmTypes) { type, kmType ->
            if (kmType != null) {
                visitType(type, kmType)
            } else {
                type
            }
        }
    }

    private fun visitFinal(type: JvmType, context: KmType): JvmType {
        return type.copyWith(context.isNullable)
    }
}
