@file:JvmName("JIRClasspaths")
package org.opentaint.ir.api.jvm.ext

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRPrimitiveType
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.NoClassInClasspathException
import org.opentaint.ir.api.jvm.PredefinedPrimitive
import org.opentaint.ir.api.jvm.PredefinedPrimitives
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.throwClassNotFound

inline fun <reified T> JIRClasspath.findClassOrNull(): JIRClassOrInterface? {
    return findClassOrNull(T::class.java.name)
}

inline fun <reified T> JIRClasspath.findTypeOrNull(): JIRType? {
    return findTypeOrNull(T::class.java.name)
}

fun JIRClasspath.findTypeOrNull(typeName: TypeName): JIRType? {
    return findTypeOrNull(typeName.typeName)
}

/**
 * find class. Tf there are none then throws `NoClassInClasspathException`
 * @throws NoClassInClasspathException
 */
fun JIRClasspath.findClass(name: String): JIRClassOrInterface {
    return findClassOrNull(name) ?: name.throwClassNotFound()
}

/**
 * find class. Tf there are none then throws `NoClassInClasspathException`
 * @throws NoClassInClasspathException
 */
inline fun <reified T> JIRClasspath.findClass(): JIRClassOrInterface {
    return findClassOrNull<T>() ?: throwClassNotFound<T>()
}

val JIRClasspath.void: JIRPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Void)
val JIRClasspath.boolean: JIRPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Boolean)
val JIRClasspath.short: JIRPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Short)
val JIRClasspath.int: JIRPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Int)
val JIRClasspath.long: JIRPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Long)
val JIRClasspath.float: JIRPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Float)
val JIRClasspath.double: JIRPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Double)
val JIRClasspath.byte: JIRPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Byte)
val JIRClasspath.char: JIRPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Char)
val JIRClasspath.nullType: JIRPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Null)
