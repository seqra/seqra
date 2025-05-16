package org.opentaint.ir.impl.features.classpaths

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClasspathExtFeature.JIRResolvedClassResult
import org.opentaint.ir.api.jvm.JIRClasspathExtFeature.JIRResolvedTypeResult
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRMethodExtFeature
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.cfg.JIRGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRRawInst

sealed class AbstractJIRResolvedResult(val name: String) {

    class JIRResolvedClassResultImpl(name: String, override val clazz: JIRClassOrInterface?) :
        AbstractJIRResolvedResult(name), JIRResolvedClassResult

    class JIRResolvedTypeResultImpl(name: String, override val type: JIRType?) : AbstractJIRResolvedResult(name),
        JIRResolvedTypeResult
}

sealed class AbstractJIRInstResult(val method: JIRMethod) {

    class JIRFlowGraphResultImpl(method: JIRMethod, override val flowGraph: JIRGraph) :
        AbstractJIRInstResult(method), JIRMethodExtFeature.JIRFlowGraphResult

    class JIRInstListResultImpl(method: JIRMethod, override val instList: JIRInstList<JIRInst>) :
        AbstractJIRInstResult(method), JIRMethodExtFeature.JIRInstListResult

    class JIRRawInstListResultImpl(method: JIRMethod, override val rawInstList: JIRInstList<JIRRawInst>) :
        AbstractJIRInstResult(method), JIRMethodExtFeature.JIRRawInstListResult
}
