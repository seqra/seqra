@file:JvmName("JIRBuilders")

package org.opentaint.ir.impl.features

import org.opentaint.ir.api.JIRArrayType
import org.opentaint.ir.api.JIRBoundedWildcard
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.ext.HierarchyExtension
import org.opentaint.ir.api.ext.toType

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
            val foundClass = classpath.toJIRClass(response.source)
            val type = foundClass.toType()
            foundClass.declaredMethods[response.methodOffset].takeIf { method ->
                type.declaredMethods.first { it.method == method }.parameters.all { param ->
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