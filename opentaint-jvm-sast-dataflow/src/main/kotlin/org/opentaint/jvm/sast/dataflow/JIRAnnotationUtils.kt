package org.opentaint.jvm.sast.dataflow

import org.opentaint.ir.api.jvm.JIRAnnotated
import org.opentaint.ir.api.jvm.JIRAnnotation

fun JIRAnnotated.matchedAnnotations(predicate: (String) -> Boolean): List<JIRAnnotation> =
    annotations.matchedAnnotations(predicate)

fun List<JIRAnnotation>.matchedAnnotations(predicate: (String) -> Boolean): List<JIRAnnotation> =
    flatMap { it.matchedAnnotations(predicate) }

fun JIRAnnotation.matchedAnnotations(predicate: (String) -> Boolean): List<JIRAnnotation> {
    val result = mutableListOf<JIRAnnotation>()
    val unprocessed = mutableListOf(this)
    val visited = hashSetOf<String>()

    while (unprocessed.isNotEmpty()) {
        val annotation = unprocessed.removeLast()
        if (!visited.add(annotation.name)) continue

        if (predicate(annotation.name)) {
            result.add(annotation)
        }

        val cls = annotation.jIRClass ?: continue
        unprocessed += cls.annotations
    }

    return result
}
