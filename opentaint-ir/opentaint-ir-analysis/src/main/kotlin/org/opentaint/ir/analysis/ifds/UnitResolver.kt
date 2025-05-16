@file:Suppress("FunctionName")

package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.packageName

interface UnitType

data class MethodUnit(val method: JIRMethod) : UnitType {
    override fun toString(): String {
        return "MethodUnit(${method.name})"
    }
}

data class ClassUnit(val clazz: JIRClassOrInterface) : UnitType {
    override fun toString(): String {
        return "ClassUnit(${clazz.simpleName})"
    }
}

data class PackageUnit(val packageName: String) : UnitType {
    override fun toString(): String {
        return "PackageUnit($packageName)"
    }
}

object SingletonUnit : UnitType {
    override fun toString(): String = javaClass.simpleName
}

object UnknownUnit : UnitType {
    override fun toString(): String = javaClass.simpleName
}

/**
 * Sets a mapping from a [Method] to abstract domain [UnitType].
 *
 * Therefore, it splits all methods into units, containing one or more method each
 * (unit is a set of methods with same value of [UnitType] returned by [resolve]).
 *
 * To get more info about how it is used in analysis, see [runAnalysis].
 */
fun interface UnitResolver<Method> {
    fun resolve(method: Method): UnitType

    companion object {
        fun getByName(name: String): UnitResolver<JIRMethod> = when (name) {
            "method" -> MethodUnitResolver
            "class" -> ClassUnitResolver(false)
            "package" -> PackageUnitResolver
            "singleton" -> SingletonUnitResolver
            else -> error("Unknown unit resolver '$name'")
        }
    }
}

fun interface JIRUnitResolver : UnitResolver<JIRMethod>

val MethodUnitResolver = JIRUnitResolver { method ->
    MethodUnit(method)
}

private val ClassUnitResolverWithNested = JIRUnitResolver { method ->
    val clazz = generateSequence(method.enclosingClass) { it.outerClass }.last()
    ClassUnit(clazz)
}
private val ClassUnitResolverWithoutNested = JIRUnitResolver { method ->
    val clazz = method.enclosingClass
    ClassUnit(clazz)
}

fun ClassUnitResolver(includeNested: Boolean) =
    if (includeNested) {
        ClassUnitResolverWithNested
    } else {
        ClassUnitResolverWithoutNested
    }

val PackageUnitResolver = JIRUnitResolver { method ->
    PackageUnit(method.enclosingClass.packageName)
}

val SingletonUnitResolver = JIRUnitResolver {
    SingletonUnit
}
