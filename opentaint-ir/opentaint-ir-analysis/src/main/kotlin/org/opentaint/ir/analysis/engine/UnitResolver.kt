package org.opentaint.ir.analysis.engine

import org.opentaint.ir.analysis.library.JIRPackageUnitResolver
import org.opentaint.ir.analysis.library.JIRSingletonUnitResolver
import org.opentaint.ir.analysis.library.getJIRClassUnitResolver
import org.opentaint.ir.analysis.library.methodUnitResolver
import org.opentaint.ir.analysis.runAnalysis
import org.opentaint.ir.api.jvm.JIRMethod

/**
 * Sets a mapping from a [Method] to abstract domain [UnitType].
 *
 * Therefore, it splits all methods into units, containing one or more method each
 * (unit is a set of methods with same value of [UnitType] returned by [resolve]).
 *
 * To get more info about how it is used in analysis, see [runAnalysis].
 */
fun interface UnitResolver<UnitType, Method> {
    fun resolve(method: Method): UnitType

    companion object {
        fun getJIRResolverByName(name: String): UnitResolver<*, JIRMethod> { // TODO can we use an asterisk here?
            return when (name) {
                "method"    -> methodUnitResolver()
                "class"     -> getJIRClassUnitResolver(false)
                "package"   -> JIRPackageUnitResolver
                "singleton" -> JIRSingletonUnitResolver
                else        -> error("Unknown unit resolver $name")
            }
        }
    }
}
