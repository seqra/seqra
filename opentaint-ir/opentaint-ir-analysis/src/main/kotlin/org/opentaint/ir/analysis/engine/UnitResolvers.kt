package org.opentaint.ir.analysis.engine

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.ext.packageName

fun interface UnitResolver<UnitType> {
    fun resolve(method: JIRMethod): UnitType

    companion object {
        fun getByName(name: String): UnitResolver<*> {
            return when (name) {
                "method"    -> MethodUnitResolver
                "class"     -> ClassUnitResolver(false)
                "package"   -> PackageUnitResolver
                "singleton" -> SingletonUnitResolver
                else        -> error("Unknown unit resolver $name")
            }
        }
    }
}

val MethodUnitResolver = UnitResolver { method -> method }

val PackageUnitResolver = UnitResolver { method -> method.enclosingClass.packageName }

val SingletonUnitResolver = UnitResolver { _ -> Unit }

class ClassUnitResolver(private val includeNested: Boolean): UnitResolver<JIRClassOrInterface> {
    override fun resolve(method: JIRMethod): JIRClassOrInterface {
        return if (includeNested) {
            generateSequence(method.enclosingClass) { it.outerClass }.last()
        } else {
            method.enclosingClass
        }
    }
}