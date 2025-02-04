package org.opentaint.ir.impl.features.classpaths

import org.opentaint.ir.api.JIRFeatureEvent
import org.opentaint.ir.api.JIRInstExtFeature
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRMethodExtFeature
import org.opentaint.ir.api.JIRMethodExtFeature.JIRInstListResult
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstList
import org.opentaint.ir.api.cfg.JIRRawInst
import org.opentaint.ir.impl.cfg.JIRGraphImpl
import org.opentaint.ir.impl.cfg.JIRInstListBuilder
import org.opentaint.ir.impl.cfg.RawInstListBuilder
import org.opentaint.ir.impl.features.JIRFeatureEventImpl
import org.opentaint.ir.impl.features.classpaths.AbstractJIRInstResult.JIRFlowGraphResultImpl
import org.opentaint.ir.impl.features.classpaths.AbstractJIRInstResult.JIRInstListResultImpl
import org.opentaint.ir.impl.features.classpaths.AbstractJIRInstResult.JIRRawInstListResultImpl

object MethodInstructionsFeature : JIRMethodExtFeature {

    private val JIRMethod.methodFeatures
        get() = enclosingClass.classpath.features?.filterIsInstance<JIRInstExtFeature>().orEmpty()

    override fun flowGraph(method: JIRMethod): JIRMethodExtFeature.JIRFlowGraphResult {
        return JIRFlowGraphResultImpl(method, JIRGraphImpl(method, method.instList.instructions))
    }

    override fun instList(method: JIRMethod): JIRInstListResult {
        val list: JIRInstList<JIRInst> = JIRInstListBuilder(method, method.rawInstList).buildInstList()
        return JIRInstListResultImpl(method, method.methodFeatures.fold(list) { value, feature ->
            feature.transformInstList(method, value)
        })
    }

    override fun rawInstList(method: JIRMethod): JIRMethodExtFeature.JIRRawInstListResult {
        val list: JIRInstList<JIRRawInst> = RawInstListBuilder(method, method.asmNode()).build()
        return JIRRawInstListResultImpl(method, method.methodFeatures.fold(list) { value, feature ->
            feature.transformRawInstList(method, value)
        })
    }

    override fun event(result: Any): JIRFeatureEvent {
        return JIRFeatureEventImpl(this, result)
    }

}