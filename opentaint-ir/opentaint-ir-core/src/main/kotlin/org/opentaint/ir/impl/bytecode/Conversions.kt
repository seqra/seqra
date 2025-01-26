package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRMethodExtFeature
import org.opentaint.ir.impl.types.MethodInfo

fun JIRClassOrInterface.toJIRMethod(
    methodInfo: MethodInfo,
    cache: JIRMethodExtFeature
): JIRMethod {
    return JIRMethodImpl(methodInfo, cache, this)
}