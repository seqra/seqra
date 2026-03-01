package org.opentaint.ir.impl.cfg.util

import org.opentaint.ir.api.jvm.PredefinedPrimitives
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.ext.jvmName
import org.opentaint.ir.impl.types.TypeNameImpl
import org.objectweb.asm.Type

internal val NULL = TypeNameImpl.fromTypeName("null")
internal const val OBJECT_CLASS = "Ljava/lang/Object;"
internal const val STRING_CLASS = "Ljava/lang/String;"
internal const val THROWABLE_CLASS = "Ljava/lang/Throwable;"
internal const val CLASS_CLASS = "Ljava/lang/Class;"
internal const val METHOD_HANDLE_CLASS = "Ljava/lang/invoke/MethodHandle;"
internal const val METHOD_HANDLES_CLASS = "Ljava/lang/invoke/MethodHandles;"
internal const val METHOD_HANDLES_LOOKUP_CLASS = "Ljava/lang/invoke/MethodHandles\$Lookup;"
internal const val METHOD_TYPE_CLASS = "Ljava/lang/invoke/MethodType;"
internal const val LAMBDA_METAFACTORY_CLASS = "Ljava/lang/invoke/LambdaMetafactory;"
internal val TOP = TypeNameImpl.fromTypeName("TOP")
internal val UNINIT_THIS = TypeNameImpl.fromTypeName("UNINIT_THIS")

internal val TypeName.jvmTypeName get() = typeName.jvmName()
internal val TypeName.jvmClassName get() = jvmTypeName.removePrefix("L").removeSuffix(";")


val TypeName.internalDesc: String
    get() = when {
        isPrimitive -> jvmTypeName
        isArray -> {
            val element = elementType()
            when {
                element.isClass -> "[${element.jvmTypeName}"
                else -> "[${element.internalDesc}"
            }
        }

        else -> this.jvmClassName
    }

val TypeName.isPrimitive get() = PredefinedPrimitives.matches(typeName)
val TypeName.isArray get() = typeName.endsWith("[]")
val TypeName.isClass get() = !isPrimitive && !isArray

internal val TypeName.isDWord get() = typeName == PredefinedPrimitives.Long || typeName == PredefinedPrimitives.Double

internal fun String.typeNameFromJvmName(): TypeName = TypeNameImpl.fromJvmName(this)
internal fun String.typeName(): TypeName = TypeNameImpl.fromTypeName(this)
fun TypeName.asArray(dimensions: Int = 1) = "$typeName${"[]".repeat(dimensions)}".typeName()
internal fun TypeName.elementType() = elementTypeOrNull() ?: this

internal fun TypeName.elementTypeOrNull() = when {
    this == NULL -> NULL
    typeName.endsWith("[]") -> typeName.removeSuffix("[]").typeName()
    else -> null
}

fun TypeName.baseElementType(): Pair<TypeName, Int> {
    var current: TypeName? = this
    var dim = -1
    var next: TypeName? = current
    do {
        current = next
        next = current!!.elementTypeOrNull()
        dim++
    } while (next != null)
    check(dim >= 0)
    return Pair(current!!, dim)
}

val lambdaMetaFactory: TypeName  = LAMBDA_METAFACTORY_CLASS.typeNameFromJvmName()
val lambdaMetaFactoryMethodName: String = "metafactory"

internal fun String.typeNameFromAsmInternalName(): TypeName =
    Type.getObjectType(this).descriptor.typeNameFromJvmName()
