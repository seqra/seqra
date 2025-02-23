package org.opentaint.ir.analysis.engine

import org.opentaint.ir.analysis.library.MethodUnitResolver
import org.opentaint.ir.analysis.library.PackageUnitResolver
import org.opentaint.ir.analysis.library.SingletonUnitResolver
import org.opentaint.ir.analysis.library.getClassUnitResolver
import org.opentaint.ir.analysis.runAnalysis
import org.opentaint.ir.api.JIRMethod

/**
 * Sets a mapping from [JIRMethod] to abstract domain [UnitType].
 *
 * Therefore, it splits all methods into units, containing one or more method each
 * (unit is a set of methods with same value of [UnitType] returned by [resolve]).
 *
 * To get more info about how it is used in analysis, see [runAnalysis].
 */
fun interface UnitResolver<UnitType> {
    fun resolve(method: JIRMethod): UnitType

    companion object {
        fun getByName(name: String): UnitResolver<*> {
            return when (name) {
                "method"    -> MethodUnitResolver
                "class"     -> getClassUnitResolver(false)
                "package"   -> PackageUnitResolver
                "singleton" -> SingletonUnitResolver
                else        -> error("Unknown unit resolver $name")
            }
        }
    }
}