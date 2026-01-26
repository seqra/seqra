package org.opentaint.jvm.sast.project.spring

import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.allSuperHierarchySequence
import org.opentaint.ir.api.jvm.ext.findMethodOrNull

fun JIRMethod.isSpringControllerMethod(): Boolean {
    if (annotations.any { it.isSpringControllerAnnotation() }) return true

    return enclosingClass.allSuperHierarchySequence
        .mapNotNull { it.findMethodOrNull(name, description) }
        .any { m -> m.annotations.any { it.isSpringControllerAnnotation() } }
}

fun JIRAnnotation.isSpringMethodAnnotation(): Boolean =
    matchAnnotationType { it in springControllerMethodMappingAnnotations }

fun JIRAnnotation.isSpringRequestMappingAnnotation(): Boolean =
    matchAnnotationType { it == springControllerRequestMapping }

fun JIRAnnotation.isSpringControllerAnnotation(): Boolean =
    isSpringRequestMappingAnnotation() || isSpringMethodAnnotation()

fun JIRAnnotation.isSpringControllerClassAnnotation(): Boolean =
    matchAnnotationType { it in springControllerClassAnnotations }

fun JIRAnnotation.isSpringAutowiredAnnotation(): Boolean =
    matchAnnotationType { it == SpringAutowired }

fun JIRAnnotation.isSpringValidated(): Boolean =
    matchAnnotationType { it == JakartaValid }

fun JIRAnnotation.isSpringPathVariable(): Boolean =
    matchAnnotationType { it == SpringPathVariable }

fun JIRAnnotation.isSpringModelAttribute(): Boolean =
    matchAnnotationType { it == SpringModelAttribute }

fun JIRAnnotation.isJakartaConstraint(): Boolean =
    matchAnnotationType { it == JakartaConstraint }

fun JIRClassOrInterface.collectSpringRequestMappingAnnotation(): List<JIRAnnotation>? {
    classSpringRequestMappingAnnotation()?.let { return it }
    return allSuperHierarchySequence.firstNotNullOfOrNull { it.classSpringRequestMappingAnnotation()  }
}

fun JIRMethod.collectSpringControllerAnnotations(): List<JIRAnnotation>? {
    methodSpringControllerAnnotations()?.let { return it }

    return enclosingClass.allSuperHierarchySequence
        .mapNotNull { it.findMethodOrNull(name, description) }
        .firstNotNullOfOrNull { m -> m.methodSpringControllerAnnotations()  }
}

fun JIRClassOrInterface.classSpringRequestMappingAnnotation(): List<JIRAnnotation>? {
    val thisAnnotations = annotations.filter { it.isSpringRequestMappingAnnotation() }
    if (thisAnnotations.isNotEmpty()) return thisAnnotations
    return null
}

fun JIRMethod.methodSpringControllerAnnotations(): List<JIRAnnotation>? {
    val thisAnnotations = annotations.filter { it.isSpringControllerAnnotation() }
    if (thisAnnotations.isNotEmpty()) return thisAnnotations
    return null
}

private fun JIRAnnotation.matchAnnotationType(predicate: (String) -> Boolean): Boolean {
    val unprocessed = mutableListOf(this)
    val visited = hashSetOf<String>()

    while (unprocessed.isNotEmpty()) {
        val annotation = unprocessed.removeLast()
        val cls = annotation.jIRClass ?: continue

        val annotationType = cls.name
        if (!visited.add(annotationType)) continue

        if (predicate(annotationType)) return true

        unprocessed += cls.annotations
    }

    return false
}
