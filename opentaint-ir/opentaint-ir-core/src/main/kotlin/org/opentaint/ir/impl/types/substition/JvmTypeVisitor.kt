
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

internal class VisitorContext(private val processed: HashSet<Any> = HashSet()) {

    fun makeProcessed(type: Any): Boolean {
        return processed.add(type)
    }


    fun isProcessed(type: Any): Boolean {
        return processed.contains(type)
    }
}

internal interface JvmTypeVisitor {

    fun visitType(type: JvmType, context: VisitorContext = VisitorContext()): JvmType {
        return when (type) {
            is JvmPrimitiveType -> type
            is JvmLowerBoundWildcard -> visitLowerBound(type, context)
            is JvmUpperBoundWildcard -> visitUpperBound(type, context)
            is JvmParameterizedType -> visitParameterizedType(type, context)
            is JvmArrayType -> visitArrayType(type, context)
            is JvmClassRefType -> visitClassRef(type, context)
            is JvmTypeVariable -> visitTypeVariable(type, context)
            is JvmUnboundWildcard -> type
            is JvmParameterizedType.JvmNestedType -> visitNested(type, context)
        }
    }


    fun visitUpperBound(type: JvmUpperBoundWildcard, context: VisitorContext): JvmType {
        return JvmUpperBoundWildcard(visitType(type.bound, context))
    }

    fun visitLowerBound(type: JvmLowerBoundWildcard, context: VisitorContext): JvmType {
        return JvmLowerBoundWildcard(visitType(type.bound, context))
    }

    fun visitArrayType(type: JvmArrayType, context: VisitorContext): JvmType {
        return JvmArrayType(visitType(type.elementType, context))
    }

    fun visitTypeVariable(type: JvmTypeVariable, context: VisitorContext): JvmType {
        if (context.isProcessed(type)) {
            return type
        }
        val result = visitUnprocessedTypeVariable(type, context)
        context.makeProcessed(type)
        return result
    }

    fun visitUnprocessedTypeVariable(type: JvmTypeVariable, context: VisitorContext): JvmType {
        return type
    }

    fun visitClassRef(type: JvmClassRefType, context: VisitorContext): JvmType {
        return type
    }

    fun visitNested(type: JvmParameterizedType.JvmNestedType, context: VisitorContext): JvmType {
        return JvmParameterizedType.JvmNestedType(
            type.name,
            type.parameterTypes.map { visitType(it, context) },
            visitType(type.ownerType, context)
        )
    }

    fun visitParameterizedType(type: JvmParameterizedType, context: VisitorContext): JvmType {
        return JvmParameterizedType(type.name, type.parameterTypes.map { visitType(it, context) })
    }

    fun visitDeclaration(
        declaration: JvmTypeParameterDeclaration,
        context: VisitorContext = VisitorContext()
    ): JvmTypeParameterDeclaration {
        if (context.isProcessed(declaration)) {
            return declaration
        }
        context.makeProcessed(declaration)
        return JvmTypeParameterDeclarationImpl(
            declaration.symbol,
            declaration.owner,
            declaration.bounds?.map { visitType(it, context) }
        )
    }
}


internal val Map<String, JvmTypeParameterDeclaration>.fixDeclarationVisitor: JvmTypeVisitor get() {
    val declarations = this
    return object : JvmTypeVisitor {

        override fun visitTypeVariable(type: JvmTypeVariable, context: VisitorContext): JvmType {
            type.declaration = declarations[type.symbol]!!
            return type
        }
    }
}
