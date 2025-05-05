@file:JvmName("JIRClasspaths")

package org.opentaint.ir.api.jvm.ext

import org.opentaint.ir.api.core.TypeName
import org.opentaint.ir.api.jvm.JIRProject
import org.opentaint.ir.api.jvm.JIRPrimitiveType
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.NoClassInClasspathException
import org.opentaint.ir.api.jvm.PredefinedJIRPrimitive
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.throwClassNotFound

inline fun <reified T> JIRProject.findClassOrNull(): JIRClassOrInterface? {
    return findClassOrNull(T::class.java.name)
}

inline fun <reified T> JIRProject.findTypeOrNull(): JIRType? {
    return findClassOrNull(T::class.java.name)?.let {
        typeOf(it)
    }
}

fun JIRProject.findTypeOrNull(typeName: TypeName): JIRType? {
    return findTypeOrNull(typeName.typeName)
}

/**
 * find class. Tf there are none then throws `NoClassInClasspathException`
 * @throws NoClassInClasspathException
 */
fun JIRProject.findClass(name: String): JIRClassOrInterface {
    return findClassOrNull(name) ?: name.throwClassNotFound()
}

/**
 * find class. Tf there are none then throws `NoClassInClasspathException`
 * @throws NoClassInClasspathException
 */
inline fun <reified T> JIRProject.findClass(): JIRClassOrInterface {
    return findClassOrNull<T>() ?: throwClassNotFound<T>()
}

val JIRProject.void: JIRPrimitiveType get() = PredefinedJIRPrimitive(this, org.opentaint.ir.api.jvm.PredefinedJIRPrimitives.Void)
val JIRProject.boolean: JIRPrimitiveType get() = PredefinedJIRPrimitive(this, org.opentaint.ir.api.jvm.PredefinedJIRPrimitives.Boolean)
val JIRProject.short: JIRPrimitiveType get() = PredefinedJIRPrimitive(this, org.opentaint.ir.api.jvm.PredefinedJIRPrimitives.Short)
val JIRProject.int: JIRPrimitiveType get() = PredefinedJIRPrimitive(this, org.opentaint.ir.api.jvm.PredefinedJIRPrimitives.Int)
val JIRProject.long: JIRPrimitiveType get() = PredefinedJIRPrimitive(this, org.opentaint.ir.api.jvm.PredefinedJIRPrimitives.Long)
val JIRProject.float: JIRPrimitiveType get() = PredefinedJIRPrimitive(this, org.opentaint.ir.api.jvm.PredefinedJIRPrimitives.Float)
val JIRProject.double: JIRPrimitiveType get() = PredefinedJIRPrimitive(this, org.opentaint.ir.api.jvm.PredefinedJIRPrimitives.Double)
val JIRProject.byte: JIRPrimitiveType get() = PredefinedJIRPrimitive(this, org.opentaint.ir.api.jvm.PredefinedJIRPrimitives.Byte)
val JIRProject.char: JIRPrimitiveType get() = PredefinedJIRPrimitive(this, org.opentaint.ir.api.jvm.PredefinedJIRPrimitives.Char)
val JIRProject.nullType: JIRPrimitiveType get() = PredefinedJIRPrimitive(this, org.opentaint.ir.api.jvm.PredefinedJIRPrimitives.Null)