package org.opentaint.jvm.sast.dataflow

import org.opentaint.ir.api.jvm.JIRAnnotated
import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRClassOrInterface

fun JIRAnnotated.matchedAnnotations(predicate: (String) -> Boolean): List<JIRAnnotation> =
    annotations.matchedAnnotations(predicate)

fun List<JIRAnnotation>.matchedAnnotations(predicate: (String) -> Boolean): List<JIRAnnotation> =
    flatMap { it.matchedAnnotations(predicate) }

private data class AnnotationChain(val annotation: JIRAnnotation, val parent: AnnotationChain?)

fun JIRAnnotation.matchedAnnotations(predicate: (String) -> Boolean): List<JIRAnnotation> {
    val result = mutableListOf<JIRAnnotation>()
    val unprocessed = mutableListOf(AnnotationChain(this, null))
    val visited = hashSetOf<String>()

    while (unprocessed.isNotEmpty()) {
        val currentChain = unprocessed.removeLast()

        val annotation = currentChain.annotation
        if (!visited.add(annotation.name)) continue

        if (predicate(annotation.name)) {
            result.add(resolveAnnotationValues(currentChain))
        }

        val cls = annotation.jIRClass ?: continue
        cls.annotations.mapTo(unprocessed) { AnnotationChain(it, currentChain) }
    }

    return result
}

const val SpringAliasFor = "org.springframework.core.annotation.AliasFor"

private fun resolveAnnotationValues(chain: AnnotationChain): JIRAnnotation {
    val mainAnnotation = chain.annotation
    val parent = chain.parent ?: return mainAnnotation

    val valuesOverride = hashMapOf<String, MutableMap<String, Any?>>()
    val parentAnnotations = parent.annotations()
    for (annotation in parentAnnotations) {
        val cls = annotation.jIRClass ?: continue
        for (method in cls.declaredMethods) {
            val currentValue = valuesOverride[annotation.name]?.get(method.name)
                ?: annotation.values[method.name]
                ?: continue

            val aliasFor = method.matchedAnnotations { it == SpringAliasFor }
            if (aliasFor.isEmpty()) continue

            for (af in aliasFor) {
                val aliasForAnnotation = af.values["annotation"] as? JIRClassOrInterface ?: continue
                valuesOverride.getOrPut(aliasForAnnotation.name, ::hashMapOf)[method.name] = currentValue
            }
        }
    }

    val annotationOverrides = valuesOverride[mainAnnotation.name]
    if (annotationOverrides.isNullOrEmpty()) return mainAnnotation

    val values = mainAnnotation.values.toMutableMap()
    values.putAll(annotationOverrides)
    return AnnotationWithValueOverrides(mainAnnotation, values)
}

private fun AnnotationChain.annotations(): List<JIRAnnotation> =
    parent?.annotations().orEmpty() + listOf(annotation)

private data class AnnotationWithValueOverrides(
    val annotation: JIRAnnotation,
    override val values: Map<String, Any?>
) : JIRAnnotation by annotation
