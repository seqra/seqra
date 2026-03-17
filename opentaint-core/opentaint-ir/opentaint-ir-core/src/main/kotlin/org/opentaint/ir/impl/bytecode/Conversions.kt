package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRMethodExtFeature
import org.opentaint.ir.impl.features.JIRFeaturesChain
import org.opentaint.ir.impl.types.MethodInfo

fun JIRClassOrInterface.toJIRMethod(
    methodInfo: MethodInfo,
    featuresChain: JIRFeaturesChain
): JIRMethod {
    return JIRMethodImpl(methodInfo, featuresChain, this)
}
