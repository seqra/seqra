package org.opentaint.ir.approximation

import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.approximation.Approximations.findOriginalByApproximationOrNull
import org.opentaint.ir.impl.types.TypeNameImpl

fun String.toApproximationName() = ApproximationClassName(this)
fun String.toOriginalName() = OriginalClassName(this)

fun TypeName.eliminateApproximation(): TypeName {
    val originalClassName = findOriginalByApproximationOrNull(typeName.toApproximationName()) ?: return this
    return TypeNameImpl(originalClassName)
}
