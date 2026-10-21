package org.opentaint.dataflow.python

import org.opentaint.dataflow.python.graph.PIRApplicationGraph
import org.opentaint.dataflow.python.graph.PIRQualifiedUnknownFunction
import org.opentaint.dataflow.python.graph.PIRSimpleNameUnknownFunction
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRClass
import org.opentaint.ir.api.python.PIRClasspath
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRInstruction
import org.opentaint.ir.api.python.PIRLoadAttr
import java.util.concurrent.ConcurrentHashMap

class PIRCallResolver(
    private val cp: PIRClasspath,
    private val applicationGraph: PIRApplicationGraph,
) {

    private val perMethodNames = ConcurrentHashMap<PIRFunction, Map<PIRInstruction, Set<String>>>()
    private val perMethodSimpleNames = ConcurrentHashMap<PIRFunction, Map<PIRCall, Set<String>>>()
    private val qualifiedSyntheticByName = ConcurrentHashMap<String, PIRQualifiedUnknownFunction>()
    private val simpleNameSyntheticByName = ConcurrentHashMap<String, PIRSimpleNameUnknownFunction>()

    private val projectMethodsByName: Map<String, List<PIRFunction>> by lazy {
        fun methods(cls: PIRClass): List<PIRFunction> = cls.methods + cls.nestedClasses.flatMap(::methods)
        cp.modules.flatMap { module -> module.classes.flatMap(::methods) }.groupBy { it.name }
    }

    private fun namesFor(method: PIRFunction): Map<PIRInstruction, Set<String>> =
        perMethodNames.computeIfAbsent(method) {
            PIRMethodQFNameReconstructor.compute(method, applicationGraph)
        }

    private fun simpleNamesFor(method: PIRFunction): Map<PIRCall, Set<String>> =
        perMethodSimpleNames.computeIfAbsent(method) {
            PIRMethodSimpleNameReconstructor.compute(method, applicationGraph)
        }

    private fun resolveNames(inst: PIRInstruction): Set<String> {
        val method = inst.location.method
        return namesFor(method).getOrDefault(inst, emptySet())
    }

    private fun resolveSimpleNames(call: PIRInstruction): Set<String> {
        val method = call.location.method
        return simpleNamesFor(method).getOrDefault(call, emptySet())
    }

    fun resolveAttribute(inst: PIRLoadAttr): Set<String> {
        val qfNames = resolveNames(inst)
        if (qfNames.isNotEmpty()) return qfNames

        return setOf(inst.attribute)
    }

    fun resolveCall(call: PIRCall): Set<PIRFunction> {
        val qfNames = resolveNames(call)
        if (qfNames.isNotEmpty()) {
            return qfNames.mapTo(hashSetOf()) {
                cp.findFunctionOrNull(it) ?: qualifiedSyntheticFor(it)
            }
        }
        return resolveSimpleNames(call).flatMapTo(hashSetOf()) {
            projectMethodsByName[it].orEmpty() + simpleNameSyntheticFor(it)
        }
    }

    private fun qualifiedSyntheticFor(qualifiedName: String): PIRQualifiedUnknownFunction =
        qualifiedSyntheticByName.computeIfAbsent(qualifiedName) {
            PIRQualifiedUnknownFunction(qualifiedName)
        }

    private fun simpleNameSyntheticFor(name: String): PIRSimpleNameUnknownFunction =
        simpleNameSyntheticByName.computeIfAbsent(name) { PIRSimpleNameUnknownFunction(name) }
}
