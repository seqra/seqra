@file:Suppress("PublicApiImplicitType")

package org.opentaint.ir.analysis.engine

import org.opentaint.ir.analysis.runAnalysis
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.ext.packageName

interface UnitType

data class MethodUnit(val method: JIRMethod) : UnitType

data class ClassUnit(val clazz: JIRClassOrInterface) : UnitType

data class PackageUnit(val packageName: String) : UnitType

object UnknownUnit : UnitType

object SingletonUnit : UnitType

/**
 * Sets a mapping from [JIRMethod] to abstract domain [UnitType].
 *
 * Therefore, it splits all methods into units, containing one or more method each
 * (unit is a set of methods with same value of [UnitType] returned by [resolve]).
 *
 * To get more info about how it is used in analysis, see [runAnalysis].
 */
fun interface UnitResolver {

    fun resolve(method: JIRMethod): UnitType

    companion object {
        fun getByName(name: String): UnitResolver = when (name) {
            "method" -> MethodUnitResolver
            "class" -> ClassUnitResolver(false)
            "package" -> PackageUnitResolver
            "singleton" -> SingletonUnitResolver
            else -> error("Unknown unit resolver '$name'")
        }
    }
}

val MethodUnitResolver = UnitResolver { method ->
    MethodUnit(method)
}

@Suppress("FunctionName")
fun ClassUnitResolver(includeNested: Boolean) = UnitResolver { method ->
    val clazz = if (includeNested) {
        generateSequence(method.enclosingClass) { it.outerClass }.last()
    } else {
        method.enclosingClass
    }
    ClassUnit(clazz)
}

val PackageUnitResolver = UnitResolver { method ->
    PackageUnit(method.enclosingClass.packageName)
}

val SingletonUnitResolver = UnitResolver { _ ->
    SingletonUnit
}
