@file:Suppress("DEPRECATION")

package org.opentaint.jvm.util

import org.opentaint.ir.api.jvm.JIRArrayType
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRRefType
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.ext.boolean
import org.opentaint.ir.api.jvm.ext.byte
import org.opentaint.ir.api.jvm.ext.char
import org.opentaint.ir.api.jvm.ext.double
import org.opentaint.ir.api.jvm.ext.float
import org.opentaint.ir.api.jvm.ext.ifArrayGetElementType
import org.opentaint.ir.api.jvm.ext.int
import org.opentaint.ir.api.jvm.ext.long
import org.opentaint.ir.api.jvm.ext.short
import org.opentaint.ir.api.jvm.ext.void
import sun.misc.Unsafe
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier


object ReflectionUtils {

    var UNSAFE: Unsafe

    init {
        try {
            val uns = Unsafe::class.java.getDeclaredField("theUnsafe")
            uns.isAccessible = true
            UNSAFE = uns[null] as Unsafe
        } catch (e: Throwable) {
            throw RuntimeException()
        }
    }

}

fun Field.getFieldValue(instance: Any?): Any? = with(ReflectionUtils.UNSAFE) {
    val (fixedInstance, fieldOffset) = getInstanceAndOffset(instance)
    return when (type) {
        Boolean::class.javaPrimitiveType -> getBoolean(fixedInstance, fieldOffset)
        Byte::class.javaPrimitiveType -> getByte(fixedInstance, fieldOffset)
        Char::class.javaPrimitiveType -> getChar(fixedInstance, fieldOffset)
        Short::class.javaPrimitiveType -> getShort(fixedInstance, fieldOffset)
        Int::class.javaPrimitiveType -> getInt(fixedInstance, fieldOffset)
        Long::class.javaPrimitiveType -> getLong(fixedInstance, fieldOffset)
        Float::class.javaPrimitiveType -> getFloat(fixedInstance, fieldOffset)
        Double::class.javaPrimitiveType -> getDouble(fixedInstance, fieldOffset)
        else -> getObject(fixedInstance, fieldOffset)
    }

}

fun Field.setFieldValue(instance: Any?, fieldValue: Any?) = with(ReflectionUtils.UNSAFE) {
    val (fixedInstance, fieldOffset) = getInstanceAndOffset(instance)
    when (type) {
        Boolean::class.javaPrimitiveType -> putBoolean(fixedInstance, fieldOffset, fieldValue as? Boolean ?: false)
        Byte::class.javaPrimitiveType -> putByte(fixedInstance, fieldOffset, fieldValue as? Byte ?: 0)
        Char::class.javaPrimitiveType -> putChar(fixedInstance, fieldOffset, fieldValue as? Char ?: '\u0000')
        Short::class.javaPrimitiveType -> putShort(fixedInstance, fieldOffset, fieldValue as? Short ?: 0)
        Int::class.javaPrimitiveType -> putInt(fixedInstance, fieldOffset, fieldValue as? Int ?: 0)
        Long::class.javaPrimitiveType -> putLong(fixedInstance, fieldOffset, fieldValue as? Long ?: 0L)
        Float::class.javaPrimitiveType -> putFloat(fixedInstance, fieldOffset, fieldValue as? Float ?: 0.0f)
        Double::class.javaPrimitiveType -> putDouble(fixedInstance, fieldOffset, fieldValue as? Double ?: 0.0)
        else -> putObject(fixedInstance, fieldOffset, fieldValue)
    }
}

fun Field.getInstanceAndOffset(instance: Any?) = with(ReflectionUtils.UNSAFE) {
    if (isStatic) {
        staticFieldBase(this@getInstanceAndOffset) to staticFieldOffset(this@getInstanceAndOffset)
    } else {
        instance to objectFieldOffset(this@getInstanceAndOffset)
    }
}

val Class<*>.allFields
    get(): List<Field> {
        val result = mutableListOf<Field>()
        var current: Class<*>? = this
        do {
            try {
                result += current!!.declaredFields
            } catch (_: Throwable) {}
            current = current!!.superclass
        } while (current != null)
        return result
    }

val Class<*>.allMethods
    get(): List<Method> {
        val result = mutableListOf<Method>()
        var current: Class<*>? = this
        do {
            result += current!!.declaredMethods
            current = current!!.superclass
        } while (current != null)
        return result
    }

fun Class<*>.getFieldByName(name: String): Field? {
    var result: Field?
    var current: Class<*> = this
    do {
        result = runCatching { current.getDeclaredField(name) }.getOrNull()
        current = current.superclass ?: break
    } while (result == null)
    return result
}

inline fun <reified R> Method.withAccessibility(block: () -> R): R {
    val prevAccessibility = isAccessible

    isAccessible = true

    try {
        return block()
    } finally {
        isAccessible = prevAccessibility
    }
}

inline fun <reified R> Constructor<*>.withAccessibility(block: () -> R): R {
    val prevAccessibility = isAccessible

    isAccessible = true

    try {
        return block()
    } finally {
        isAccessible = prevAccessibility
    }
}

inline fun <reified R> Field.withAccessibility(block: () -> R): R {
    val prevAccessibility = isAccessible

    isAccessible = true

    try {
        return block()
    } finally {
        isAccessible = prevAccessibility
    }
}

val Field.isStatic: Boolean
    get() = modifiers.and(Modifier.STATIC) > 0

val Field.isFinal: Boolean
    get() = (this.modifiers and Modifier.FINAL) == Modifier.FINAL

fun JIRClassType.allocateInstance(classLoader: ClassLoader): Any =
    ReflectionUtils.UNSAFE.allocateInstance(toJavaClass(classLoader))

fun JIRArrayType.allocateInstance(classLoader: ClassLoader, length: Int): Any =
    when (elementType) {
        classpath.boolean -> BooleanArray(length)
        classpath.short -> ShortArray(length)
        classpath.int -> IntArray(length)
        classpath.long -> LongArray(length)
        classpath.float -> FloatArray(length)
        classpath.double -> DoubleArray(length)
        classpath.byte -> ByteArray(length)
        classpath.char -> CharArray(length)
        is JIRRefType -> {
            // TODO: works incorrectly for inner array
            val clazz = elementType.toJavaClass(classLoader)
            java.lang.reflect.Array.newInstance(clazz, length)
        }

        else -> error("Unexpected type: $this")
    }

fun JIRType.toJavaClass(classLoader: ClassLoader, initialize: Boolean = true): Class<*> =
    when (this) {
        classpath.boolean -> Boolean::class.javaPrimitiveType!!
        classpath.short -> Short::class.javaPrimitiveType!!
        classpath.int -> Int::class.javaPrimitiveType!!
        classpath.long -> Long::class.javaPrimitiveType!!
        classpath.float -> Float::class.javaPrimitiveType!!
        classpath.double -> Double::class.javaPrimitiveType!!
        classpath.byte -> Byte::class.javaPrimitiveType!!
        classpath.char -> Char::class.javaPrimitiveType!!
        classpath.void -> Void::class.javaPrimitiveType!!
        is JIRRefType -> toJavaClass(classLoader, initialize)
        else -> error("Unexpected type: $this")
    }

fun JIRRefType.toJavaClass(classLoader: ClassLoader, initialize: Boolean = true): Class<*> =
    ifArrayGetElementType?.let { elementType ->
        when (elementType) {
            classpath.boolean -> BooleanArray::class.java
            classpath.short -> ShortArray::class.java
            classpath.int -> IntArray::class.java
            classpath.long -> LongArray::class.java
            classpath.float -> FloatArray::class.java
            classpath.double -> DoubleArray::class.java
            classpath.byte -> ByteArray::class.java
            classpath.char -> CharArray::class.java
            is JIRRefType -> {
                val clazz = elementType.toJavaClass(classLoader)
                java.lang.reflect.Array.newInstance(clazz, 0).javaClass
            }

            else -> error("Unexpected type: $elementType")
        }
    } ?: jIRClass.toJavaClass(classLoader, initialize)

fun JIRClassOrInterface.toJavaClass(classLoader: ClassLoader, initialize: Boolean = true): Class<*> =
    classLoader.loadClass(this, initialize)

private fun ClassLoader.loadClass(jIRClass: JIRClassOrInterface, initialize: Boolean): Class<*> {
    if (this is JIRlassLoaderExt)
        return loadClass(jIRClass, initialize)

    return Class.forName(jIRClass.name, initialize, this)
}

fun getArrayLength(instance: Any?): Int =
    java.lang.reflect.Array.getLength(instance)

fun getArrayIndex(instance: Any, index: Int): Any? =
    when (instance::class.java) {
        BooleanArray::class.java -> java.lang.reflect.Array.getBoolean(instance, index)
        ByteArray::class.java -> java.lang.reflect.Array.getByte(instance, index)
        ShortArray::class.java -> java.lang.reflect.Array.getShort(instance, index)
        IntArray::class.java -> java.lang.reflect.Array.getInt(instance, index)
        LongArray::class.java -> java.lang.reflect.Array.getLong(instance, index)
        FloatArray::class.java -> java.lang.reflect.Array.getFloat(instance, index)
        DoubleArray::class.java -> java.lang.reflect.Array.getDouble(instance, index)
        CharArray::class.java -> java.lang.reflect.Array.getChar(instance, index)
        else -> java.lang.reflect.Array.get(instance, index)
    }

fun setArrayIndex(instance: Any, index: Int, value: Any?) =
    when (instance::class.java) {
        BooleanArray::class.java -> java.lang.reflect.Array.setBoolean(instance, index, value as Boolean)
        ByteArray::class.java -> java.lang.reflect.Array.setByte(instance, index, value as Byte)
        ShortArray::class.java -> java.lang.reflect.Array.setShort(instance, index, value as Short)
        IntArray::class.java -> java.lang.reflect.Array.setInt(instance, index, value as Int)
        LongArray::class.java -> java.lang.reflect.Array.setLong(instance, index, value as Long)
        FloatArray::class.java -> java.lang.reflect.Array.setFloat(instance, index, value as Float)
        DoubleArray::class.java -> java.lang.reflect.Array.setDouble(instance, index, value as Double)
        CharArray::class.java -> java.lang.reflect.Array.setChar(instance, index, value as Char)
        else -> java.lang.reflect.Array.set(instance, index, value)
    }

fun JIRField.getFieldValue(classLoader: ClassLoader, instance: Any?): Any? {
    val javaField = toJavaField(classLoader) ?: error("Class ${enclosingClass.name} has no `$name` field")
    return javaField.withAccessibility {
        javaField.get(instance)
    }
}

fun JIRField.setFieldValue(classLoader: ClassLoader, instance: Any?, value: Any?) {
    val javaField = toJavaField(classLoader) ?: error("Class ${enclosingClass.name} has no `$name` field")
    return javaField.withAccessibility {
        javaField.set(instance, value)
    }
}

fun JIRMethod.invoke(classLoader: ClassLoader, instance: Any?, args: List<Any?>): Any? =
    if (isConstructor) {
        val javaCtor = toJavaConstructor(classLoader)
        javaCtor.withAccessibility {
            javaCtor.newInstance(*args.toTypedArray())
        }
    } else {
        val javaMethod = toJavaMethod(classLoader)
        javaMethod.withAccessibility {
            javaMethod.invoke(instance, *args.toTypedArray())
        }
    }

private val Class<*>.safeDeclaredFields: List<Field> get() {
    return try {
        declaredFields.toList()
    } catch (e: Throwable) {
        emptyList()
    }
}

val Class<*>.allInstanceFields: List<Field>
    get() = allFields.filter { !Modifier.isStatic(it.modifiers) }

val Class<*>.staticFields: List<Field>
    get() = safeDeclaredFields.filter { Modifier.isStatic(it.modifiers) }
