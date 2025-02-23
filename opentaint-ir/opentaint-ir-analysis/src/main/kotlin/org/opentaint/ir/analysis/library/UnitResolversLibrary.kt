@file:JvmName("UnitResolversLibrary")
package org.opentaint.ir.analysis.library

import org.opentaint.ir.analysis.engine.UnitResolver
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.ext.packageName

val MethodUnitResolver = UnitResolver { method -> method }
val PackageUnitResolver = UnitResolver { method -> method.enclosingClass.packageName }
val SingletonUnitResolver = UnitResolver { _ -> Unit }

fun getClassUnitResolver(includeNested: Boolean): UnitResolver<JIRClassOrInterface> {
    return ClassUnitResolver(includeNested)
}

private class ClassUnitResolver(private val includeNested: Boolean): UnitResolver<JIRClassOrInterface> {
    override fun resolve(method: JIRMethod): JIRClassOrInterface {
        return if (includeNested) {
            generateSequence(method.enclosingClass) { it.outerClass }.last()
        } else {
            method.enclosingClass
        }
    }
}