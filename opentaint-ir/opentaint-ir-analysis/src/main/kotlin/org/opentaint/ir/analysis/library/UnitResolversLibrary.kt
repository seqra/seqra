@file:JvmName("UnitResolversLibrary")
package org.opentaint.ir.analysis.library

import org.opentaint.ir.analysis.engine.UnitResolver
import org.opentaint.ir.api.core.CoreMethod
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.packageName

// TODO caelmbleidd add cache?????
fun <Method> methodUnitResolver() = UnitResolver<Method, Method> { method -> method }

// TODO caelmbleidd extract java
val JIRPackageUnitResolver = UnitResolver<String, JIRMethod> { method -> method.enclosingClass.packageName }
val JIRSingletonUnitResolver = UnitResolver<Unit, JIRMethod> { _ -> Unit }

fun getJIRClassUnitResolver(includeNested: Boolean): UnitResolver<JIRClassOrInterface, JIRMethod> {
    return JIRClassUnitResolver(includeNested)
}

private class JIRClassUnitResolver(private val includeNested: Boolean): UnitResolver<JIRClassOrInterface, JIRMethod> {
    override fun resolve(method: JIRMethod): JIRClassOrInterface {
        return if (includeNested) {
            generateSequence(method.enclosingClass) { it.outerClass }.last()
        } else {
            method.enclosingClass
        }
    }
}