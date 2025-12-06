@file:JvmName("JIRCommons")
package org.opentaint.ir.api.jvm.ext

import org.opentaint.ir.api.jvm.*
import java.io.Serializable
import java.lang.Cloneable

fun String.jvmName(): String {
    return when {
        this == PredefinedPrimitives.Boolean -> "Z"
        this == PredefinedPrimitives.Byte -> "B"
        this == PredefinedPrimitives.Char -> "C"
        this == PredefinedPrimitives.Short -> "S"
        this == PredefinedPrimitives.Int -> "I"
        this == PredefinedPrimitives.Float -> "F"
        this == PredefinedPrimitives.Long -> "J"
        this == PredefinedPrimitives.Double -> "D"
        this == PredefinedPrimitives.Void -> "V"
        endsWith("[]") -> {
            val elementName = substring(0, length - 2)
            "[" + elementName.jvmName()
        }

        else -> "L${this.replace('.', '/')};"
    }
}

val jvmPrimitiveNames = hashSetOf("Z", "B", "C", "S", "I", "F", "J", "D", "V")

fun String.jIRdbName(): String {
    return when {
        this == "Z" -> PredefinedPrimitives.Boolean
        this == "B" -> PredefinedPrimitives.Byte
        this == "C" -> PredefinedPrimitives.Char
        this == "S" -> PredefinedPrimitives.Short
        this == "I" -> PredefinedPrimitives.Int
        this == "F" -> PredefinedPrimitives.Float
        this == "J" -> PredefinedPrimitives.Long
        this == "D" -> PredefinedPrimitives.Double
        this == "V" -> PredefinedPrimitives.Void
        startsWith("[") -> {
            val elementName = substring(1, length)
            elementName.jIRdbName() + "[]"
        }

        startsWith("L") -> {
            substring(1, length - 1).replace('/', '.')
        }

        else -> error("Incorrect JVM name: $this")
    }
}

val JIRClasspath.objectType: JIRClassType
    get() = findTypeOrNull<Any>() as? JIRClassType ?: throwClassNotFound<Any>()

val JIRClasspath.objectClass: JIRClassOrInterface
    get() = findClass<Any>()

val JIRClasspath.cloneableClass: JIRClassOrInterface
    get() = findClass<Cloneable>()

val JIRClasspath.serializableClass: JIRClassOrInterface
    get() = findClass<Serializable>()

// call with SAFE. comparator works only on methods from one hierarchy
internal object UnsafeHierarchyMethodComparator : Comparator<JIRMethod> {

    override fun compare(o1: JIRMethod, o2: JIRMethod): Int {
        return (o1.name + o1.description).compareTo(o2.name + o2.description)
    }
}

fun JIRAnnotated.hasAnnotation(className: String): Boolean {
    return annotations.any { it.matches(className) }
}

fun JIRAnnotated.annotation(className: String): JIRAnnotation? {
    return annotations.firstOrNull { it.matches(className) }
}
