package org.opentaint.ir.impl.features.classpaths

import org.opentaint.ir.api.JIRFeatureEvent
import org.opentaint.ir.api.JIRInstExtFeature
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRMethodExtFeature
import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstList
import org.opentaint.ir.api.cfg.JIRRawInst
import org.opentaint.ir.impl.cfg.JIRGraphImpl
import org.opentaint.ir.impl.cfg.JIRInstListBuilder
import org.opentaint.ir.impl.cfg.RawInstListBuilder
import org.opentaint.ir.impl.features.JIRFeatureEventImpl

object MethodInstructionsFeature : JIRMethodExtFeature {

    private val JIRMethod.methodFeatures
        get() = enclosingClass.classpath.features?.filterIsInstance<JIRInstExtFeature>().orEmpty()

    override fun flowGraph(method: JIRMethod): JIRGraph {
        return JIRGraphImpl(method, method.instList.instructions)
    }

    override fun instList(method: JIRMethod): JIRInstList<JIRInst> {
        val list: JIRInstList<JIRInst> = JIRInstListBuilder(method, method.rawInstList).buildInstList()
        return method.methodFeatures.fold(list) { value, feature ->
            feature.transformInstList(method, value)
        }
    }

    override fun rawInstList(method: JIRMethod): JIRInstList<JIRRawInst> {
        val list: JIRInstList<JIRRawInst> = RawInstListBuilder(method, method.asmNode()).build()
        return method.methodFeatures.fold(list) { value, feature ->
            feature.transformRawInstList(method, value)
        }
    }

    override fun event(result: Any, input: Array<Any>): JIRFeatureEvent {
        return JIRFeatureEventImpl(this, result, input)
    }

}