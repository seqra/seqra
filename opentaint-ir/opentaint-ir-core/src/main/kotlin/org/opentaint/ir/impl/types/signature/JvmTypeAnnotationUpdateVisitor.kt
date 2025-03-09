package org.opentaint.ir.impl.types.signature

import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JvmType
import org.opentaint.ir.api.ext.isNotNullAnnotation
import org.opentaint.ir.api.ext.isNullableAnnotation
import org.opentaint.ir.impl.bytecode.JIRAnnotationImpl
import org.opentaint.ir.impl.types.AnnotationInfo
import org.objectweb.asm.TypePath

private data class AnnotationUpdateVisitorContext(val annotationInfo: AnnotationInfo) {
    var step: Int = 0

    /**
     * Denotes whether we have already applied annotation to some part of type, or not.
     * Used in parsing of nested types -- see docs for [JvmTypeAnnotationUpdateVisitor] for more info
     */
    var finished: Boolean = false

    private val typePath = TypePath.fromString(annotationInfo.typePath)

    val stepType: Int?
        get() {
            if (typePath == null || step >= typePath.length) {
                return null
            }
            return typePath.getStep(step)
        }

    val stepArgument: Int
        get() = typePath.getStepArgument(step)
}

/**
 * This is a visitor that applies type annotation to correct part of type.
 * On each call it looks at current step of [TypePath] (stored in [AnnotationUpdateVisitorContext]) and
 * makes a recursive call to the corresponding part of type. If none steps left,
 * annotation is applied directly to the given type.
 *
 * Note that nested types are handled in a special way. This is because in JvmTypes by default we look at the innermost
 * type, and we can access outer type via [JvmParameterizedType.JvmNestedType.ownerType].
 * However, in type annotations path is stored in the opposite way -- by default annotation is applied to the outermost
 * type, and a special symbol in typePath indicates that it should be applied to inner type -- see [TypePath.INNER_TYPE]
 *
 * So, when visiting [JvmParameterizedType.JvmNestedType] we should first visit its ownertype. This call
 * may either apply annotation to some part of ownertype, or it can meet directive to go to inner type.
 * In the latter case we should continue parsing annotation from current type.
 * These cases can be distinguished by looking at [AnnotationUpdateVisitorContext.finished] property
 * after visiting ownertype.
 *
 * @param cp [JIRClasspath] instance needed to instantiate [JIRAnnotationImpl]
 */
private class JvmTypeAnnotationUpdateVisitor(private val cp: JIRClasspath)
    : JvmTypeVisitor<AnnotationUpdateVisitorContext> {
    override fun visitUpperBound(
        type: JvmBoundWildcard.JvmUpperBoundWildcard,
        context: AnnotationUpdateVisitorContext
    ): JvmType {
        val stepType = context.stepType ?: return applyAnnotation(type, context)

        if (stepType != TypePath.WILDCARD_BOUND) {
            unexpectedStepType(stepType, "JvmUpperBound")
        }

        context.step++
        val newBound = visitType(type.bound, context)
        return JvmBoundWildcard.JvmUpperBoundWildcard(newBound)
    }

    override fun visitLowerBound(
        type: JvmBoundWildcard.JvmLowerBoundWildcard,
        context: AnnotationUpdateVisitorContext
    ): JvmType {
        val stepType = context.stepType ?: return applyAnnotation(type, context)

        if (context.stepType != TypePath.WILDCARD_BOUND) {
            unexpectedStepType(stepType, "JvmLowerBound")
        }

        context.step++
        val newBound = visitType(type.bound, context)
        return JvmBoundWildcard.JvmLowerBoundWildcard(newBound)
    }

    override fun visitArrayType(type: JvmArrayType, context: AnnotationUpdateVisitorContext): JvmType {
        val stepType = context.stepType ?: return applyAnnotation(type, context)

        if (context.stepType != TypePath.ARRAY_ELEMENT) {
            unexpectedStepType(stepType, "JvmArrayType")
        }

        context.step++
        val newElementType = visitType(type.elementType, context)
        return JvmArrayType(newElementType, type.isNullable, type.annotations)
    }

    override fun visitTypeVariable(type: JvmTypeVariable, context: AnnotationUpdateVisitorContext): JvmType {
        require(context.stepType == null)
        return applyAnnotation(type, context)
    }

    override fun visitClassRef(type: JvmClassRefType, context: AnnotationUpdateVisitorContext): JvmType {
        val stepType = context.stepType ?: return applyAnnotation(type, context)

        if (context.stepType != TypePath.INNER_TYPE) {
            unexpectedStepType(stepType, "JvmClassRefType")
        }
        return handleInnerType(type, context)
    }

    override fun visitNested(
        type: JvmParameterizedType.JvmNestedType,
        context: AnnotationUpdateVisitorContext
    ): JvmType {
        val newOwnerType = visitType(type.ownerType, context)

        if (context.finished) {
            return JvmParameterizedType.JvmNestedType(
                type.name,
                type.parameterTypes,
                newOwnerType,
                type.isNullable,
                type.annotations
            )
        }

        // Here newOwnerType == type.ownerType (it didn't change because otherwise context.finished would be true)
        val stepType = context.stepType ?: return applyAnnotation(type, context)

        return when (stepType) {
            TypePath.INNER_TYPE -> handleInnerType(type, context)
            TypePath.TYPE_ARGUMENT -> {
                val index = context.stepArgument
                val newParameterTypes = type.parameterTypes.toMutableList()
                context.step++
                newParameterTypes[index] = visitType(newParameterTypes[index], context)
                JvmParameterizedType.JvmNestedType(
                    type.name,
                    newParameterTypes,
                    type.ownerType,
                    type.isNullable,
                    type.annotations
                )
            }
            else -> unexpectedStepType(stepType, "JvmNested")
        }
    }

    override fun visitParameterizedType(type: JvmParameterizedType, context: AnnotationUpdateVisitorContext): JvmType {
        val stepType = context.stepType ?: return applyAnnotation(type, context)

        return when (stepType) {
            TypePath.INNER_TYPE -> handleInnerType(type, context)
            TypePath.TYPE_ARGUMENT -> {
                val index = context.stepArgument
                val newParameterTypes = type.parameterTypes.toMutableList()
                context.step++
                newParameterTypes[index] = visitType(newParameterTypes[index], context)
                JvmParameterizedType(type.name, newParameterTypes, type.isNullable, type.annotations)
            }
            else -> unexpectedStepType(stepType, "JvmParametrizedType")
        }
    }

    private fun applyAnnotation(type: JvmType, context: AnnotationUpdateVisitorContext): JvmType {
        val annotation = JIRAnnotationImpl(context.annotationInfo, cp)
        require(!context.finished)
        context.finished = true

        return when {
            annotation.isNotNullAnnotation -> type.copyWith(false, type.annotations.plus(annotation))
            annotation.isNullableAnnotation-> type.copyWith(true, type.annotations.plus(annotation))
            else -> type.copyWith(type.isNullable, type.annotations.plus(annotation))
        }
    }

    private fun unexpectedStepType(stepType: Int, kind: String): Nothing =
        error("Unexpected step type $stepType for $kind")

    private fun handleInnerType(type: AbstractJvmType, context: AnnotationUpdateVisitorContext): AbstractJvmType {
        context.step++
        return type
    }
}

/**
 * Adds all given type annotations to proper parts of type (which is given as receiver).
 * Also, for nullability annotations, changes the nullability of corresponding parts of type
 */
internal fun JvmType.withTypeAnnotations(annotationInfos: List<AnnotationInfo>, cp: JIRClasspath): JvmType {
    val visitor = JvmTypeAnnotationUpdateVisitor(cp)
    return annotationInfos.fold(this) { type, annotationInfo ->
        visitor.visitType(type, AnnotationUpdateVisitorContext(annotationInfo))
    }
}