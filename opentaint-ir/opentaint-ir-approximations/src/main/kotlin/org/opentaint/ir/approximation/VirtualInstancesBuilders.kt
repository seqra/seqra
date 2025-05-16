package org.opentaint.ir.approximation

import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.impl.features.JIRFeaturesChain
import org.opentaint.ir.impl.features.classpaths.virtual.VirtualClassesBuilder
import org.objectweb.asm.tree.MethodNode

class EnrichedVirtualMethodBuilder(
    name: String = "_enriched_virtual_"
) : VirtualClassesBuilder.VirtualMethodBuilder(name) {
    private var exceptions: List<TypeName> = emptyList()
    private var featuresChain: JIRFeaturesChain = JIRFeaturesChain(emptyList())
    private var asmNode: MethodNode = MethodNode()
    private var enrichedParameters: List<JIREnrichedVirtualParameter> = emptyList()
    private var annotations: List<JIRAnnotation> = emptyList()

    fun exceptions(exceptions: List<TypeName>) = apply {
        this.exceptions = exceptions
    }

    fun featuresChain(featuresChain: JIRFeaturesChain) = apply {
        this.featuresChain = featuresChain
    }

    fun asmNode(asmNode: MethodNode) = apply {
        this.asmNode = asmNode
    }

    fun annotations(annotations: List<JIRAnnotation>) = apply {
        this.annotations = annotations
    }

    fun enrichedParameters(parameters: List<JIREnrichedVirtualParameter>) = apply {
        this.enrichedParameters = parameters
        this.params(*parameters.map { it.type.typeName }.toTypedArray())
    }

    override fun build(): JIREnrichedVirtualMethod {
        return JIREnrichedVirtualMethod(
            name,
            access,
            returnType,
            enrichedParameters,
            description,
            featuresChain,
            exceptions,
            asmNode,
            annotations
        )
    }
}

class EnrichedVirtualFieldBuilder(
    name: String = "_enriched_virtual_"
) : VirtualClassesBuilder.VirtualFieldBuilder(name) {
    private var annotations: List<JIRAnnotation> = emptyList()

    fun annotations(annotations: List<JIRAnnotation>) = apply {
        this.annotations = annotations
    }

    override fun build(): JIREnrichedVirtualField = JIREnrichedVirtualField(name, access, type, annotations)
}
