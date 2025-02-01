package org.opentaint.ir.approximation

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRMethod
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

        (EnrichedVirtualMethodBuilder()
            .name(name)
            .access(access)
            .returnType(returnType.eliminateApproximation().typeName) as EnrichedVirtualMethodBuilder)
            .enrichedParameters(parameters)
            .featuresChain(featuresChain)
            .exceptions(exceptions)
            .annotations(annotations)
            .asmNode(asmNode())
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