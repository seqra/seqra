package org.opentaint.ir.approximation

import org.opentaint.ir.api.JIRClassExtFeature
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRInstExtFeature
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.cfg.JIRInstList
import org.opentaint.ir.api.cfg.JIRRawInst
import org.opentaint.ir.approximation.ApproximationsMappingFeature.findApproximationByOriginOrNull
import org.opentaint.ir.approximation.TransformerIntoVirtual.transformIntoVirtualField
import org.opentaint.ir.approximation.TransformerIntoVirtual.transformMethodIntoVirtual
import org.opentaint.ir.impl.cfg.JIRInstListImpl

/**
 * A feature allowing to retrieve fields and methods from an approximation for a specified class.
 */
object ClassContentApproximationFeature : JIRClassExtFeature {
    /**
     * Returns a list of [JIREnrichedVirtualField] if there is an approximation for [clazz] and null otherwise.
     */
    override fun fieldsOf(clazz: JIRClassOrInterface): List<JIRField>? {
        val approximationName = findApproximationByOriginOrNull(clazz.name.toOriginalName()) ?: return null
        val approximationClass = clazz.classpath.findClassOrNull(approximationName) ?: return null

        return approximationClass.declaredFields.map { transformIntoVirtualField(clazz, it) }
    }

    /**
     * Returns a list of [JIREnrichedVirtualMethod] if there is an approximation for [clazz] and null otherwise.
     */
    override fun methodsOf(clazz: JIRClassOrInterface): List<JIRMethod>? {
        val approximationName = findApproximationByOriginOrNull(clazz.name.toOriginalName()) ?: return null
        val approximationClass = clazz.classpath.findClassOrNull(approximationName) ?: return null

        return approximationClass.declaredMethods.map {
            approximationClass.classpath.transformMethodIntoVirtual(clazz, it)
        }
    }
}

/**
 * A feature replacing all occurrences of approximations classes names with their targets names.
 */
object ApproximationsInstructionsFeature : JIRInstExtFeature {
    override fun transformRawInstList(method: JIRMethod, list: JIRInstList<JIRRawInst>): JIRInstList<JIRRawInst> {
        return JIRInstListImpl(list.map { it.accept(InstSubstitutorForApproximations) })
    }
}