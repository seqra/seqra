package org.opentaint.ir.impl.types.substition

import org.opentaint.ir.impl.types.signature.JvmArrayType
import org.opentaint.ir.impl.types.signature.JvmBoundWildcard.JvmLowerBoundWildcard
import org.opentaint.ir.impl.types.signature.JvmBoundWildcard.JvmUpperBoundWildcard
import org.opentaint.ir.impl.types.signature.JvmClassRefType
import org.opentaint.ir.impl.types.signature.JvmParameterizedType
import org.opentaint.ir.impl.types.signature.JvmPrimitiveType
import org.opentaint.ir.impl.types.signature.JvmType
import org.opentaint.ir.impl.types.signature.JvmTypeParameterDeclaration
import org.opentaint.ir.impl.types.signature.JvmTypeParameterDeclarationImpl
import org.opentaint.ir.impl.types.signature.JvmTypeVariable
import org.opentaint.ir.impl.types.signature.JvmUnboundWildcard

internal interface JvmTypeVisitor {

    fun visitType(type: JvmType): JvmType {
        return when (type) {
            is JvmPrimitiveType -> type
            is JvmLowerBoundWildcard -> visitLowerBound(type)
            is JvmUpperBoundWildcard -> visitUpperBound(type)
            is JvmParameterizedType -> visitParameterizedType(type)
            is JvmArrayType -> visitArrayType(type)
            is JvmClassRefType -> visitClassRef(type)
            is JvmTypeVariable -> visitTypeVariable(type)
            is JvmUnboundWildcard -> type
            is JvmParameterizedType.JvmNestedType -> visitNested(type)
        }
    }


    fun visitUpperBound(type: JvmUpperBoundWildcard): JvmType {
        return JvmUpperBoundWildcard(visitType(type.bound))
    }

    fun visitLowerBound(type: JvmLowerBoundWildcard): JvmType {
        return JvmLowerBoundWildcard(visitType(type.bound))
    }

    fun visitArrayType(type: JvmArrayType): JvmType {
        return JvmArrayType(visitType(type.elementType))
    }

    fun visitTypeVariable(type: JvmTypeVariable): JvmType {
        return type
    }

    fun visitClassRef(type: JvmClassRefType): JvmType {
        return type
    }

    fun visitNested(type: JvmParameterizedType.JvmNestedType): JvmType {
        return JvmParameterizedType.JvmNestedType(
            type.name,
            type.parameterTypes.map { visitType(it) },
            visitType(type.ownerType)
        )
    }

    fun visitParameterizedType(type: JvmParameterizedType): JvmType {
        return JvmParameterizedType(type.name, type.parameterTypes.map { visitType(it) })
    }

    fun visitDeclaration(declaration: JvmTypeParameterDeclaration): JvmTypeParameterDeclaration {
        return JvmTypeParameterDeclarationImpl(
            declaration.symbol,
            declaration.owner,
            declaration.bounds?.map { visitType(it) }
        )
    }


}