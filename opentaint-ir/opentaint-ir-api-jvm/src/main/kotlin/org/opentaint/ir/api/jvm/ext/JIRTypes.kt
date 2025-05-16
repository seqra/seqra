@file:JvmName("JIRTypes")

package org.opentaint.ir.api.jvm.ext

import org.opentaint.ir.api.jvm.*
import java.lang.Boolean
import java.lang.Byte
import java.lang.Double
import java.lang.Float
import java.lang.Long
import java.lang.Short

val JIRClassType.constructors get() = declaredMethods.filter { it.method.isConstructor }

/**
 * @return element class in case of `this` is ArrayClass
 */
val JIRType.ifArrayGetElementType: JIRType?
    get() {
        return when (this) {
            is JIRArrayType -> elementType
            else -> null
        }
    }

/**
 * unboxes `this` class. That means that for 'java.lang.Integet' will be returned `PredefinedPrimitive.int`
 * and for `java.lang.String` will be returned `java.lang.String`
 */
fun JIRType.unboxIfNeeded(): JIRType {
    return when (typeName) {
        "java.lang.Boolean" -> classpath.boolean
        "java.lang.Byte" -> classpath.byte
        "java.lang.Char" -> classpath.char
        "java.lang.Short" -> classpath.short
        "java.lang.Integer" -> classpath.int
        "java.lang.Long" -> classpath.long
        "java.lang.Float" -> classpath.float
        "java.lang.Double" -> classpath.double
        else -> this
    }
}

/**
 * unboxes `this` class. That means that for 'java.lang.Integet' will be returned `PredefinedPrimitive.int`
 * and for `java.lang.String` will be returned `java.lang.String`
 */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
fun JIRType.autoboxIfNeeded(): JIRType {
    return when (this) {
        classpath.boolean -> classpath.findTypeOrNull<java.lang.Boolean>() ?: throwClassNotFound<Boolean>()
        classpath.byte -> classpath.findTypeOrNull<java.lang.Byte>() ?: throwClassNotFound<Byte>()
        classpath.char -> classpath.findTypeOrNull<Character>() ?: throwClassNotFound<Character>()
        classpath.short -> classpath.findTypeOrNull<java.lang.Short>() ?: throwClassNotFound<Short>()
        classpath.int -> classpath.findTypeOrNull<Integer>() ?: throwClassNotFound<Integer>()
        classpath.long -> classpath.findTypeOrNull<java.lang.Long>() ?: throwClassNotFound<Long>()
        classpath.float -> classpath.findTypeOrNull<java.lang.Float>() ?: throwClassNotFound<Float>()
        classpath.double -> classpath.findTypeOrNull<java.lang.Double>() ?: throwClassNotFound<Double>()
        else -> this
    }
}

val JIRArrayType.deepestElementType: JIRType
    get() {
        var type = elementType
        while (type is JIRArrayType) {
            val et = elementType.ifArrayGetElementType ?: return type
            type = et
        }
        return type
    }

fun JIRType.isAssignable(declaration: JIRType): kotlin.Boolean {
    val nullType = classpath.nullType
    if (this == declaration) {
        return true
    }
    return when {
        declaration == nullType -> false
        this == nullType -> declaration is JIRRefType
        this is JIRClassType ->
            when (declaration) {
                classpath.objectType -> true
                is JIRClassType -> jIRClass.isSubClassOf(declaration.jIRClass)
                is JIRPrimitiveType -> unboxIfNeeded() == declaration
                else -> false
            }

        this is JIRPrimitiveType -> {
            when (declaration) {
                classpath.objectType -> true
                classpath.short -> this == classpath.short || this == classpath.byte
                classpath.int -> this == classpath.int || this == classpath.short || this == classpath.byte
                classpath.long -> this == classpath.long || this == classpath.int || this == classpath.short || this == classpath.byte
                classpath.float -> this == classpath.float || this == classpath.long || this == classpath.int || this == classpath.short || this == classpath.byte
                classpath.double -> this == classpath.double || this == classpath.float || this == classpath.long || this == classpath.int || this == classpath.short || this == classpath.byte
                !is JIRPrimitiveType -> declaration.unboxIfNeeded() == this
                else -> false
            }
        }

        this is JIRArrayType -> {
            when (declaration) {
                // From Java Language Spec 2nd ed., Chapter 10, Arrays
                classpath.objectType -> true
                classpath.serializableClass.toType() -> true
                classpath.cloneableClass.toType() -> true
                is JIRArrayType -> {
                    // boolean[][] can be stored in a Object[].
                    // Interface[] can be stored in a Object[]
                    if (dimensions == declaration.dimensions) {
                        val thisElement = deepestElementType
                        val declarationElement = declaration.deepestElementType
                        when {
                            thisElement is JIRRefType && declarationElement is JIRRefType -> thisElement.jIRClass.isSubClassOf(
                                declarationElement.jIRClass
                            )

                            else -> false
                        }
                    } else if (dimensions > declaration.dimensions) {
                        val type = declaration.deepestElementType
                        if (type is JIRRefType) {
                            // From Java Language Spec 2nd ed., Chapter 10, Arrays
                            type == classpath.objectType || type == classpath.serializableClass.toType() || type == classpath.cloneableClass.toType()
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }

                else -> false
            }
        }

        else -> false
    }
}

/**
 * find field by name
 *
 * @param name field name
 */
fun JIRClassType.findFieldOrNull(name: String): JIRTypedField? {
    return lookup.field(name)
}

/**
 * find method by name and description
 *
 * @param name method name
 * @param desc method description
 */
fun JIRClassType.findMethodOrNull(name: String, desc: String): JIRTypedMethod? {
    return lookup.method(name, desc)
}

/**
 * find method by name and description
 *
 * This method doesn't support [org.opentaint.ir.impl.features.classpaths.UnknownClasses] feature.
 */
fun JIRClassType.findMethodOrNull(predicate: (JIRTypedMethod) -> kotlin.Boolean): JIRTypedMethod? {
    // let's find method based on strict hierarchy
    // if method is not found then it's defined in interfaces
    return methods.firstOrNull(predicate)
}

val JIRTypedMethod.humanReadableSignature: String
    get() {
        val params = parameters.joinToString(",") { it.type.typeName }
        val generics = typeParameters.takeIf { it.isNotEmpty() }?.let {
            it.joinToString(prefix = "<", separator = ",", postfix = ">") { it.symbol }
        } ?: ""
        return "${enclosingType.typeName}#$generics$name($params):${returnType.typeName}"
    }

fun JIRClasspath.findType(name: String): JIRType {
    return findTypeOrNull(name) ?: throw TypeNotFoundException(name)
}
