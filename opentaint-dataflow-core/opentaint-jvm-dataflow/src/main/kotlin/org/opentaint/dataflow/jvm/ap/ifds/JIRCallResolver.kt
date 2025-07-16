package org.opentaint.dataflow.jvm.ap.ifds

import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRRefType
import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRCatchInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.cfg.JIRVirtualCallExpr
import org.opentaint.ir.api.jvm.ext.isSubClassOf
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import java.util.concurrent.ConcurrentHashMap

class JIRCallResolver(
    private val graph: JIRApplicationGraph,
    private val unitResolver: JIRUnitResolver
) {
    private val hierarchy = runBlocking { graph.project.hierarchyExt() }

    private val methodOverridesCache = ConcurrentHashMap<JIRMethod, List<JIRMethod>>()

    private fun methodOverrides(method: JIRMethod): List<JIRMethod> =
        methodOverridesCache.computeIfAbsent(method) {
            hierarchy.findOverrides(method, includeAbstract = false)
                .filterTo(mutableListOf()) { unitResolver.resolve(it) != UnknownUnit }
        }

    fun resolve(call: JIRCallExpr, location: JIRInst, context: MethodContext): List<MethodWithContext> {
        val method = call.method.method
        val methodIgnored = unitResolver.resolve(method) == UnknownUnit

        // todo: is it ok?
        // note: also ignore all possible method overrides
        if (methodIgnored) return emptyList()

        // todo: resolve lambda calls?
        if (call !is JIRVirtualCallExpr) {
            return attachContext(method, context, call, location)
        }

        return resolveVirtualMethod(method, call, location, context)
    }

    // note: we can't have more than one method at inst
    private val methodOverridesAtLocation = ConcurrentHashMap<Pair<JIRInst, MethodContext>, List<MethodWithContext>>()

    private fun resolveVirtualMethod(
        method: JIRMethod,
        call: JIRVirtualCallExpr,
        location: JIRInst,
        context: MethodContext
    ): List<MethodWithContext> {
        val overrides = methodOverrides(method)
        if (overrides.isEmpty()) {
            return attachContext(method, context, call, location)
        }

        return methodOverridesAtLocation.computeIfAbsent(location to context) {
            resolveVirtualMethodAtLocation(method, overrides, call, location, context)
        }
    }

    private fun attachContext(
        method: JIRMethod,
        context: MethodContext,
        call: JIRCallExpr,
        location: JIRInst
    ): List<MethodWithContext> {
        if (call !is JIRInstanceCallExpr) return listOf(MethodWithContext(method, EmptyMethodContext))

        val instanceTypes = resolveValueClass(call.instance, location, context, hashSetOf())
        return attachContext(call, location, method, instanceTypes)
    }

    private fun attachContext(
        call: JIRCallExpr,
        location: JIRInst,
        method: JIRMethod,
        instanceTypes: Set<JIRClassOrInterface>?
    ): List<MethodWithContext> {
        if (instanceTypes.isNullOrEmpty()) {
            logger.warn { "No instance type for $call at ${location.method}" }
            return listOf(MethodWithContext(method, EmptyMethodContext))
        }

        // Method instance type is concrete enough
        if (instanceTypes.any { method.enclosingClass.isSubClassOf(it) }) {
            return listOf(MethodWithContext(method, EmptyMethodContext))
        }

        return instanceTypes.map {
            val ctx = InstanceTypeMethodContext(it)
            MethodWithContext(method, ctx)
        }
    }

    private fun resolveVirtualMethodAtLocation(
        baseMethod: JIRMethod,
        overrides: List<JIRMethod>,
        call: JIRVirtualCallExpr,
        location: JIRInst,
        context: MethodContext
    ): List<MethodWithContext> {
        val methods = if (baseMethod.isAbstract) overrides else overrides + baseMethod

        val instanceTypes = resolveValueClass(call.instance, location, context, hashSetOf())
        if (instanceTypes.isNullOrEmpty()) {
            return methods.flatMap { attachContext(call, location, baseMethod, instanceTypes) }
        }

        return methods.filter { method ->
            instanceTypes.any { type ->
                method.enclosingClass.isSubClassOf(type) || type.isSubClassOf(method.enclosingClass)
            }
        }.flatMap { attachContext(call, location, it, instanceTypes) }
    }

    private fun resolveValueClass(
        value: JIRValue,
        location: JIRInst,
        context: MethodContext,
        visitedValues: MutableSet<JIRValue>
    ): Set<JIRClassOrInterface>? {
        val valueCls = (value.type as? JIRRefType)?.jirClass ?: return null

        if (value !is JIRLocalVar || value in visitedValues) {
            return when (context) {
                EmptyMethodContext -> setOf(valueCls)
                is InstanceTypeMethodContext -> {
                    val type = if (value is JIRThis) selectClass(valueCls, context.type) else valueCls
                    setOf(type)
                }
            }
        }

        val (assignments, catchers) = findAllAssignmentsToValue(value, location)
        if (assignments.isEmpty() && catchers.isEmpty()) {
            logger.warn { "No assignments to $value in ${location.method}" }
            return setOf(valueCls)
        }

        val resolvedTypes = hashSetOf<JIRClassOrInterface>()

        visitedValues.add(value)
        for (assignment in assignments) {
            val expr = assignment.rhv
            val exprCls = (expr.type as? JIRRefType)?.jirClass ?: return null

            when (expr) {
                is JIRLocalVar -> {
                    val varTypes = resolveValueClass(expr, assignment, context, visitedValues) ?: return null
                    varTypes.mapTo(resolvedTypes) { selectClass(valueCls, it) }
                }

                is JIRCastExpr -> {
                    val operandTypes = resolveValueClass(expr.operand, assignment, context, visitedValues) ?: return null
                    operandTypes.mapTo(resolvedTypes) {
                        selectClass(valueCls, selectClass(it, exprCls))
                    }
                }

                else -> {
                    resolvedTypes.add(selectClass(valueCls, exprCls))
                }
            }
        }
        visitedValues.remove(value)

        for (catcher in catchers) {
            catcher.throwableTypes.mapNotNullTo(resolvedTypes) {
                (it as? JIRRefType)?.jirClass
            }
        }

        return resolvedTypes
    }

    private fun selectClass(base: JIRClassOrInterface, other: JIRClassOrInterface): JIRClassOrInterface {
        if (base == other) return base

        // other is more concrete
        if (other.isSubClassOf(base)) {
            return other
        }

        // todo
        return base
    }

    private fun findAllAssignmentsToValue(
        value: JIRValue,
        initialLocation: JIRInst
    ): Pair<List<JIRAssignInst>, List<JIRCatchInst>> {
        val visitedStatements = hashSetOf<JIRInst>()
        val unprocessedStatements = mutableListOf<JIRInst>()
        unprocessedStatements.addAll(graph.predecessors(initialLocation))

        val assignments = mutableListOf<JIRAssignInst>()
        val catchers = mutableListOf<JIRCatchInst>()

        while (unprocessedStatements.isNotEmpty()) {
            val stmt = unprocessedStatements.removeLast()
            if (!visitedStatements.add(stmt)) continue

            if (stmt is JIRCatchInst && stmt.throwable == value) {
                catchers.add(stmt)
                continue
            }

            if (stmt is JIRAssignInst && stmt.lhv == value) {
                assignments.add(stmt)
                continue
            }

            unprocessedStatements.addAll(graph.predecessors(stmt))
        }

        return assignments to catchers
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
