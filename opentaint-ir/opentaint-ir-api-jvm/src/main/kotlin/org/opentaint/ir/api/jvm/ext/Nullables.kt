@file:JvmName("Nullables")

package org.opentaint.ir.api.jvm.ext

import org.opentaint.ir.api.core.TypeName
import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRParameter
import org.opentaint.ir.api.jvm.JIRAnnotated

val JIRAnnotation.isNotNullAnnotation: Boolean
    get() = NullabilityAnnotations.notNullAnnotations.any { matches(it) }

val JIRAnnotation.isNullableAnnotation: Boolean
    get() = NullabilityAnnotations.nullableAnnotations.any { matches(it) }

private object NullabilityAnnotations {
    val notNullAnnotations = listOf(
        "org.jetbrains.annotations.NotNull",
        "lombok.NonNull"
    )

    val nullableAnnotations = listOf(
        "org.jetbrains.annotations.Nullable"
    )
}

private fun JIRAnnotated.isNullable(type: TypeName): Boolean? =
    when {
        org.opentaint.ir.api.jvm.PredefinedJIRPrimitives.matches(type.typeName) -> false
        annotations.any { it.isNotNullAnnotation } -> false
        annotations.any { it.isNullableAnnotation } -> true
        else -> null
    }

// TODO: maybe move these methods from ext into class definitions?
//  We already have many nullability-related methods there, furthermore this way we can use opentaint-ir-core in implementation
val JIRField.isNullable: Boolean?
    get() = isNullable(type)

val JIRParameter.isNullable: Boolean?
    get() = isNullable(type)

val JIRMethod.isNullable: Boolean?
    get() = isNullable(returnType)
