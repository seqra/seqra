package org.opentaint.ir.api.jvm

import kotlinx.collections.immutable.persistentListOf

object PredefinedJIRPrimitives {

    const val Boolean = "boolean"

    const val Byte = "byte"
    const val Char = "char"
    const val Short = "short"
    const val Int = "int"
    const val Long = "long"
    const val Float = "float"
    const val Double = "double"
    const val Void = "void"
    const val Null = "null"

    private val values = persistentListOf(Boolean, Byte, Char, Short, Int, Long, Float, Double, Void, Null)
    private val valueSet = values.toHashSet()

    @JvmStatic
    fun of(name: String, cp: JIRProject, annotations: List<JIRAnnotation> = listOf()): JIRPrimitiveType? {
        if (valueSet.contains(name)) {
            return PredefinedJIRPrimitive(cp, name, annotations)
        }
        return null
    }

    @JvmStatic
    fun matches(name: String): Boolean {
        return valueSet.contains(name)
    }
}

/**
 * Predefined primitive types
 */
class PredefinedJIRPrimitive(override val classpath: JIRProject, override val typeName: String,
                            override val annotations: List<JIRAnnotation> = listOf()) : JIRPrimitiveType {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PredefinedJIRPrimitive

        if (typeName != other.typeName) return false

        return true
    }

    override fun hashCode(): Int {
        return typeName.hashCode()
    }

    override fun copyWithAnnotations(annotations: List<JIRAnnotation>): JIRType =
        PredefinedJIRPrimitive(classpath, typeName, annotations)
}
