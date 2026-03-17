package org.opentaint.ir.impl.types.substition

import org.opentaint.ir.api.jvm.JvmType
import org.opentaint.ir.api.jvm.JvmTypeParameterDeclaration
import org.opentaint.ir.impl.types.signature.*
import org.opentaint.ir.impl.types.signature.JvmBoundWildcard.JvmLowerBoundWildcard
import org.opentaint.ir.impl.types.signature.JvmBoundWildcard.JvmUpperBoundWildcard

internal class VisitorContext(private val processed: HashSet<Any> = HashSet()) {

    fun makeProcessed(type: Any): Boolean {
        return processed.add(type)
    }


    fun isProcessed(type: Any): Boolean {
        return processed.contains(type)
    }
}

internal interface RecursiveJvmTypeVisitor : JvmTypeVisitor<VisitorContext> {
    fun visitType(type: JvmType): JvmType {
        return visitType(type, VisitorContext())
    }

    override fun visitUpperBound(type: JvmUpperBoundWildcard, context: VisitorContext): JvmType {
        return JvmUpperBoundWildcard(visitType(type.bound, context))
    }

    override fun visitLowerBound(type: JvmLowerBoundWildcard, context: VisitorContext): JvmType {
        return JvmLowerBoundWildcard(visitType(type.bound, context))
    }

    override fun visitArrayType(type: JvmArrayType, context: VisitorContext): JvmType {
        return JvmArrayType(visitType(type.elementType, context), type.isNullable, type.annotations)
    }

    override fun visitTypeVariable(type: JvmTypeVariable, context: VisitorContext): JvmType {
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

    override fun visitClassRef(type: JvmClassRefType, context: VisitorContext): JvmType {
        return type
    }

    override fun visitNested(type: JvmParameterizedType.JvmNestedType, context: VisitorContext): JvmType {
        return JvmParameterizedType.JvmNestedType(
            type.name,
            type.parameterTypes.map { visitType(it, context) },
            visitType(type.ownerType, context),
            type.isNullable,
            type.annotations
        )
    }

    override fun visitParameterizedType(type: JvmParameterizedType, context: VisitorContext): JvmType {
        return JvmParameterizedType(type.name, type.parameterTypes.map { visitType(it, context) }, type.isNullable, type.annotations)
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


internal val Map<String, JvmTypeParameterDeclaration>.fixDeclarationVisitor: RecursiveJvmTypeVisitor
    get() {
        val declarations = this
        return object : RecursiveJvmTypeVisitor {

            override fun visitTypeVariable(type: JvmTypeVariable, context: VisitorContext): JvmType {
                type.declaration = declarations[type.symbol]
                return type
            }
        }
    }
