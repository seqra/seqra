package org.opentaint.ir.approximation

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.impl.features.JIRFeaturesChain

object TransformerIntoVirtual {
    fun JIRClasspath.transformMethodIntoVirtual(
        to: JIRClassOrInterface,
        method: JIRMethod
    ): JIREnrichedVirtualMethod = with(method) {
        val parameters = parameters.map { param ->
            // TODO process annotations somehow to eliminate approximations
            with(param) {
                JIREnrichedVirtualParameter(index, type.eliminateApproximation(), name, annotations, access)
            }
        }

        val featuresChain = features?.let { JIRFeaturesChain(it) } ?: JIRFeaturesChain(emptyList())

        val exceptions = exceptions.map { it.eliminateApproximation() }

        val methodNode = withAsmNode { it } // Safe since used under synchronization in JIREnrichedVirtualMethod

        (EnrichedVirtualMethodBuilder()
            .name(name)
            .access(access)
            .returnType(returnType.eliminateApproximation().typeName) as EnrichedVirtualMethodBuilder)
            .enrichedParameters(parameters)
            .featuresChain(featuresChain)
            .exceptions(exceptions)
            .annotations(annotations)
            .asmNode(methodNode)
            .build()
            .also { it.bind(to) }
    }

    fun transformIntoVirtualField(
        to: JIRClassOrInterface,
        field: JIRField
    ): JIREnrichedVirtualField = with(field) {
        (EnrichedVirtualFieldBuilder()
            .name(name)
            .type(type.eliminateApproximation().typeName)
            .access(access) as EnrichedVirtualFieldBuilder)
            .annotations(annotations)
            .build()
            .also { it.bind(to) }
    }
}
