package org.opentaint.ir.impl.cfg.util

import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.api.ext.jIRdbName
import org.opentaint.ir.api.ext.jvmName
import org.opentaint.ir.impl.types.TypeNameImpl

internal val NULL = "null".typeName()
internal const val OBJECT_CLASS = "Ljava.lang.Object;"
internal const val STRING_CLASS = "Ljava.lang.String;"
internal const val THROWABLE_CLASS = "Ljava.lang.Throwable;"
internal const val CLASS_CLASS = "Ljava.lang.Class;"
internal const val METHOD_HANDLE_CLASS = "Ljava.lang.invoke.MethodHandle;"

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

internal val TypeName.isDWord
    get() = when (typeName) {
        PredefinedPrimitives.Long -> true
        PredefinedPrimitives.Double -> true
        else -> false
    }

internal fun String.typeName(): TypeName = TypeNameImpl(this.jIRdbName())
internal fun TypeName.asArray(dimensions: Int = 1) = "$typeName${"[]".repeat(dimensions)}".typeName()
internal fun TypeName.elementType() = elementTypeOrNull()
    ?: error("Attempting to get element type of non-array type $this")

internal fun TypeName.elementTypeOrNull() = when {
    this == NULL -> NULL
    typeName.endsWith("[]") -> typeName.removeSuffix("[]").typeName()
    else -> null
}
internal fun TypeName.baseElementType(): TypeName {
    var current: TypeName? = this
    var next: TypeName? = current
    do {
        current = next
        next = current!!.elementTypeOrNull()
    } while (next != null)
    return current!!
}
