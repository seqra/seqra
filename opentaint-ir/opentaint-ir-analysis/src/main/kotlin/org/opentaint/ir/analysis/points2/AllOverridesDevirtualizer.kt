package org.opentaint.ir.analysis.points2

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.analysis.Points2Engine
import org.opentaint.ir.analysis.points2.AllOverridesDevirtualizer.Companion.bannedPackagePrefixes
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRVirtualCallExpr
import org.opentaint.ir.api.ext.cfg.callExpr
import org.opentaint.ir.api.ext.isSubClassOf
import org.opentaint.ir.impl.features.hierarchyExt

/**
 * Simple devirtualizer that substitutes method with all of its overrides, but no more then [limit].
 * Also, it doesn't devirtualize methods matching [bannedPackagePrefixes]
 */
class AllOverridesDevirtualizer(
    private val initialGraph: JIRApplicationGraph,
    private val classpath: JIRClasspath,
    private val limit: Int? = null
) : Points2Engine, Devirtualizer {
    private val hierarchyExtension = runBlocking {
        classpath.hierarchyExt()
    }

    private val cache: MutableMap<JIRMethod, List<JIRMethod>> = mutableMapOf()

    private fun getOverrides(method: JIRMethod): List<JIRMethod> {
        return if (cache.containsKey(method)) {
            cache[method]!!
        } else {
            val res = hierarchyExtension.findOverrides(method).toList()
            cache[method] = res
            res
        }
    }

    override fun findPossibleCallees(sink: JIRInst): Collection<JIRMethod> {
        val methods = initialGraph.callees(sink).toList()
        val callExpr = sink.callExpr as? JIRVirtualCallExpr ?: return methods
        val instanceClass = (callExpr.instance.type as? JIRClassType)?.jIRClass ?: return methods

        return methods
            .flatMap { method ->
                if (bannedPackagePrefixes.any { method.enclosingClass.name.startsWith(it) })
                    listOf(method)
                else {
                    val allOverrides = getOverrides(method)
                        .filter {
                            it.enclosingClass isSubClassOf instanceClass ||
                            // TODO: use only down-most override here
                            instanceClass isSubClassOf it.enclosingClass
                        }

                    // TODO: maybe filter inaccessible methods here?
                    return if (limit != null) {
                        allOverrides.take(limit).toList() + listOf(method)
                    } else {
                        allOverrides.toList() + listOf(method)
                    }
                }
            }
    }

    companion object {
        private val bannedPackagePrefixes = listOf(
            "sun.",
            "jdk.internal.",
            "java.",
            "kotlin."
        )
    }

    override fun obtainDevirtualizer(): Devirtualizer {
        return this
    }
}