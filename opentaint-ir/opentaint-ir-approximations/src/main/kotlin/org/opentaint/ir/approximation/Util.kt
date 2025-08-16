package org.opentaint.ir.approximation

import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.approximation.Approximations.findOriginalByApproximationOrNull
import org.opentaint.ir.impl.cfg.util.asArray
import org.opentaint.ir.impl.cfg.util.baseElementType
import org.opentaint.ir.impl.cfg.util.isArray
import org.opentaint.ir.impl.types.TypeNameImpl

fun String.toApproximationName() = ApproximationClassName(this)
fun String.toOriginalName() = OriginalClassName(this)

fun TypeName.eliminateApproximation(): TypeName {
    if (this.isArray) {
        val (elemType, dim) = this.baseElementType()
        val resultElemType = elemType.eliminateApproximation()
        return resultElemType.asArray(dim)
    }
    val originalClassName = findOriginalByApproximationOrNull(typeName.toApproximationName()) ?: return this
    return TypeNameImpl(originalClassName)
}
