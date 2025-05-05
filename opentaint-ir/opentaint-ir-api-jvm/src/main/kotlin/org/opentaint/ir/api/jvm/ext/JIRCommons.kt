@file:JvmName("JIRCommons")

package org.opentaint.ir.api.jvm.ext

import org.opentaint.ir.api.jvm.throwClassNotFound
import org.opentaint.ir.api.jvm.JIRAnnotated
import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRProject
import org.opentaint.ir.api.jvm.PredefinedJIRPrimitives
import java.io.Serializable

fun String.jvmName(): String {
    return when {
        this == PredefinedJIRPrimitives.Boolean -> "Z"
        this == PredefinedJIRPrimitives.Byte -> "B"
        this == PredefinedJIRPrimitives.Char -> "C"
        this == PredefinedJIRPrimitives.Short -> "S"
        this == PredefinedJIRPrimitives.Int -> "I"
        this == PredefinedJIRPrimitives.Float -> "F"
        this == PredefinedJIRPrimitives.Long -> "J"
        this == PredefinedJIRPrimitives.Double -> "D"
        this == PredefinedJIRPrimitives.Void -> "V"
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
        this == "Z" -> PredefinedJIRPrimitives.Boolean
        this == "B" -> PredefinedJIRPrimitives.Byte
        this == "C" -> PredefinedJIRPrimitives.Char
        this == "S" -> PredefinedJIRPrimitives.Short
        this == "I" -> PredefinedJIRPrimitives.Int
        this == "F" -> PredefinedJIRPrimitives.Float
        this == "J" -> PredefinedJIRPrimitives.Long
        this == "D" -> PredefinedJIRPrimitives.Double
        this == "V" -> PredefinedJIRPrimitives.Void
        startsWith("[") -> {
            val elementName = substring(1, length)
            elementName.jIRdbName() + "[]"
        }

        startsWith("L") -> {
            substring(1, length - 1).replace('/', '.')
        }

        else -> this.replace('/', '.')
    }
}

val JIRProject.objectType: JIRClassType
    get() = findTypeOrNull<Any>() as? JIRClassType ?: throwClassNotFound<Any>()

val JIRProject.objectClass: JIRClassOrInterface
    get() = findClass<Any>()

val JIRProject.cloneableClass: JIRClassOrInterface
    get() = findClass<Cloneable>()

val JIRProject.serializableClass: JIRClassOrInterface
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