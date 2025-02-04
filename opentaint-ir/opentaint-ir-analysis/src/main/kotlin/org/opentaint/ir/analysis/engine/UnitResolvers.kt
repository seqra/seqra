package org.opentaint.ir.analysis.engine

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.ext.packageName

interface UnitResolver<UnitType> {
    fun resolve(method: JIRMethod): UnitType
}

object MethodUnitResolver: UnitResolver<JIRMethod> {
    override fun resolve(method: JIRMethod): JIRMethod {
        return method
    }
}

class ClassUnitResolver(private val includeNested: Boolean): UnitResolver<JIRClassOrInterface> {
    override fun resolve(method: JIRMethod): JIRClassOrInterface {
        return if (includeNested) {
            generateSequence(method.enclosingClass) { it.outerClass }.last()
        } else {
            method.enclosingClass
        }
    }
}

object PackageUnitResolver: UnitResolver<String> {
    override fun resolve(method: JIRMethod): String {
        return method.enclosingClass.packageName
    }
}

object SingletonUnitResolver: UnitResolver<Unit> {
    override fun resolve(method: JIRMethod) = Unit
}