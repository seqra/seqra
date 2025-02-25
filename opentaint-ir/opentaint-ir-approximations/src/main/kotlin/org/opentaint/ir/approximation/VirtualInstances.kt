package org.opentaint.ir.approximation

import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRMethodExtFeature
import org.opentaint.ir.api.JIRMethodExtFeature.*
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstList
import org.opentaint.ir.api.cfg.JIRRawInst
import org.opentaint.ir.impl.features.JIRFeaturesChain
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualFieldImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualMethodImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualParameter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode

class JIREnrichedVirtualMethod(
    name: String,
    access: Int = Opcodes.ACC_PUBLIC,
    returnType: TypeName,
    parameters: List<JIREnrichedVirtualParameter>,
    description: String,
    private val featuresChain: JIRFeaturesChain,
    override val exceptions: List<TypeName>,
    private val asmNode: MethodNode,
    override val annotations: List<JIRAnnotation>
) : JIRVirtualMethodImpl(name, access, returnType, parameters, description) {

    override val rawInstList: JIRInstList<JIRRawInst>
        get() = featuresChain.call<JIRMethodExtFeature, JIRRawInstListResult> {
            it.rawInstList(this)
        }!!.rawInstList

    override val instList: JIRInstList<JIRInst>
        get() = featuresChain.call<JIRMethodExtFeature, JIRInstListResult> {
            it.instList(this)
        }!!.instList

    override fun asmNode(): MethodNode = asmNode

    override fun flowGraph(): JIRGraph = featuresChain.call<JIRMethodExtFeature, JIRFlowGraphResult> {
        it.flowGraph(this)
    }!!.flowGraph

    override val signature: String?
        get() = null
}

class JIREnrichedVirtualParameter(
    index: Int,
    type: TypeName,
    override val name: String?,
    override val annotations: List<JIRAnnotation>,
    override val access: Int
) : JIRVirtualParameter(index, type)

class JIREnrichedVirtualField(
    name: String,
    access: Int,
    type: TypeName,
    override val annotations: List<JIRAnnotation>
) : JIRVirtualFieldImpl(name, access, type) {
    override val signature: String?
        get() = null
}