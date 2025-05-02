package org.opentaint.ir.analysis.engine

import org.opentaint.ir.analysis.library.MethodUnitResolver
import org.opentaint.ir.analysis.library.PackageUnitResolver
import org.opentaint.ir.analysis.library.SingletonUnitResolver
import org.opentaint.ir.analysis.library.getClassUnitResolver
import org.opentaint.ir.analysis.runAnalysis

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
        fun <Method> getByName(name: String): UnitResolver<*, Method> {
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