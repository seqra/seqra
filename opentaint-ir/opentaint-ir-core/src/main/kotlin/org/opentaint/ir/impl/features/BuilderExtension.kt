
@file:JvmName("JIRBuilders")
package org.opentaint.ir.impl.features

import org.opentaint.ir.api.jvm.JIRArrayType
import org.opentaint.ir.api.jvm.JIRBoundedWildcard
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.ext.HierarchyExtension
import org.opentaint.ir.api.jvm.ext.toType


class BuildersExtension(private val classpath: JIRClasspath, private val hierarchyExtension: HierarchyExtension) {

    fun findBuildMethods(jIRClass: JIRClassOrInterface, includeSubclasses: Boolean = false): Sequence<JIRMethod> {
        val hierarchy = hierarchyExtension.findSubClasses(jIRClass, true).toMutableSet().also {
            it.add(jIRClass)
        }
        val names = when {
            includeSubclasses -> hierarchy.map { it.name }.toSet()
            else -> setOf(jIRClass.name)
        }
        val syncQuery = Builders.syncQuery(classpath, names)
        return syncQuery.mapNotNull { response ->
            val responseSource = response.source
            val foundClass = classpath.toJIRClass(responseSource)
            val type = foundClass.toType()
            foundClass.declaredMethods[response.methodOffset].takeIf { method ->
                val typedMethod = type.declaredMethods.firstOrNull {
                    it.method == method
                }
                typedMethod != null && typedMethod.parameters.all { param ->
                    !param.type.hasReferences(hierarchy)
                }
            }
        }
    }

    private fun JIRType.hasReferences(jIRClasses: Set<JIRClassOrInterface>): Boolean {
        return when (this) {
            is JIRClassType -> jIRClasses.contains(jIRClass) || typeArguments.any { it.hasReferences(jIRClasses) }
            is JIRBoundedWildcard -> (lowerBounds + upperBounds).any { it.hasReferences(jIRClasses) }
            is JIRArrayType -> elementType.hasReferences(jIRClasses)
            else -> false
        }
    }
}


suspend fun JIRClasspath.buildersExtension(): BuildersExtension {
    if (!db.isInstalled(Builders)) {
        throw IllegalStateException("This extension requires `Builders` feature to be installed")
    }
    return BuildersExtension(this, hierarchyExt())
}
