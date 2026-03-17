package org.opentaint.ir.impl.types.signature

import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JvmType

private data class JvmTypeUpdate(val newNullability: Boolean?, val newAnnotations: List<JIRAnnotation>)

/**
 * Returns given type with nullability and annotations set according to given [JvmTypeUpdate] instance
 */
private object JvmTypeUpdateVisitor : JvmTypeVisitor<JvmTypeUpdate> {

    override fun visitUpperBound(type: JvmBoundWildcard.JvmUpperBoundWildcard, context: JvmTypeUpdate): JvmType {
        return visitWildcard(type, context)
    }

    override fun visitLowerBound(type: JvmBoundWildcard.JvmLowerBoundWildcard, context: JvmTypeUpdate): JvmType {
        return visitWildcard(type, context)
    }

    override fun visitArrayType(type: JvmArrayType, context: JvmTypeUpdate): JvmType {
        return JvmArrayType(type.elementType, context.newNullability, context.newAnnotations)
    }

    override fun visitTypeVariable(type: JvmTypeVariable, context: JvmTypeUpdate): JvmType {
        return JvmTypeVariable(type.symbol, context.newNullability, context.newAnnotations).also {
            it.declaration = type.declaration
        }
    }

    override fun visitClassRef(type: JvmClassRefType, context: JvmTypeUpdate): JvmType {
        return JvmClassRefType(type.name, context.newNullability, context.newAnnotations)
    }

    override fun visitNested(type: JvmParameterizedType.JvmNestedType, context: JvmTypeUpdate): JvmType {
        return JvmParameterizedType.JvmNestedType(
            type.name,
            type.parameterTypes,
            type.ownerType,
            context.newNullability,
            context.newAnnotations
        )
    }

    override fun visitParameterizedType(type: JvmParameterizedType, context: JvmTypeUpdate): JvmType {
        return JvmParameterizedType(type.name, type.parameterTypes, context.newNullability, context.newAnnotations)
    }

    private fun visitWildcard(type: JvmWildcard, context: JvmTypeUpdate): JvmType {
        if (context.newNullability != true)
            error("Attempting to make wildcard not-nullable, which are always nullable by convention")
        if (context.newAnnotations.isNotEmpty())
            error("Annotations on wildcards are not supported")
        return type
    }
}

internal fun JvmType.copyWith(nullability: Boolean?, annotations: List<JIRAnnotation> = this.annotations): JvmType =
    JvmTypeUpdateVisitor.visitType(this, JvmTypeUpdate(nullability, annotations))
