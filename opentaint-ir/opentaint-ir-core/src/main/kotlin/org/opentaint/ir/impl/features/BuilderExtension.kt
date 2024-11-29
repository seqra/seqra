
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

    fun findBuildMethods(jirClass: JIRClassOrInterface, includeSubclasses: Boolean = false): Sequence<JIRMethod> {
        val hierarchy = hierarchyExtension.findSubClasses(jirClass, true).toMutableSet().also {
            it.add(jirClass)
        }
        val names = when {
            includeSubclasses -> hierarchy.map { it.name }.toSet()
            else -> setOf(jirClass.name)
        }
        val syncQuery = Builders.syncQuery(classpath, names)
        return syncQuery.mapNotNull { response ->
            val foundClass = classpath.toJcClass(response.source)
            val type = foundClass.toType()
            foundClass.declaredMethods[response.methodOffset].takeIf { method ->
                type.declaredMethods.first { it.method == method }.parameters.all { param ->
                    !param.type.hasReferences(hierarchy)
                }
            }
        }
    }

    private fun JIRType.hasReferences(jirClasses: Set<JIRClassOrInterface>): Boolean {
        return when (this) {
            is JIRClassType -> jirClasses.contains(jirClass) || typeArguments.any { it.hasReferences(jirClasses) }
            is JIRBoundedWildcard -> (lowerBounds + upperBounds).any { it.hasReferences(jirClasses) }
            is JIRArrayType -> elementType.hasReferences(jirClasses)
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