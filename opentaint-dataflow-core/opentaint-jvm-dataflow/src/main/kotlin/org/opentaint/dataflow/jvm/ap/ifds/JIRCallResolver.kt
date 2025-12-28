package org.opentaint.dataflow.jvm.ap.ifds

import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRRefType
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRCatchInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRLambdaExpr
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.cfg.JIRVirtualCallExpr
import org.opentaint.ir.api.jvm.ext.isSubClassOf
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.jvm.graph.JApplicationGraph
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature.JIRLambdaClass
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

class JIRCallResolver(
    private val cp: JIRClasspath,
    private val graph: JApplicationGraph,
    private val unitResolver: JIRUnitResolver
) {
    private val hierarchy = runBlocking { cp.hierarchyExt() }

    private val methodOverridesCache = ConcurrentHashMap<JIRMethod, List<JIRMethod>>()

    private fun methodOverrides(method: JIRMethod): List<JIRMethod> =
        methodOverridesCache.computeIfAbsent(method) {
            hierarchy.findOverrides(method, includeAbstract = false)
                .filterTo(mutableListOf()) {
                    unitResolver.resolve(it) != UnknownUnit
                            && it.enclosingClass !is JIRLambdaClass // Lambdas handled by JIRLambdaTracker
                }
        }

    sealed interface MethodResolutionResult {
        object MethodResolutionFailed : MethodResolutionResult
        data class ConcreteMethod(val method: MethodWithContext) : MethodResolutionResult
        data class Lambda(val instance: JIRVirtualCallExpr, val method: JIRMethod) : MethodResolutionResult
    }

    fun resolve(call: JIRCallExpr, location: JIRInst, context: MethodContext): List<MethodResolutionResult> {
        val method = call.method.method
        val methodIgnored = unitResolver.resolve(method) == UnknownUnit

        // todo: is it ok?
        // note: also ignore all possible method overrides
        if (methodIgnored) return listOf(MethodResolutionResult.MethodResolutionFailed)

        if (call is JIRLambdaExpr) {
            // lambda expr is an allocation site. lambda calls resolved as virtual calls
            return emptyList()
        }

        if (call !is JIRVirtualCallExpr) {
            return attachContext(method, context, call, location)
        }

        return resolveVirtualMethod(method, call, location, context)
    }

    private fun resolveVirtualMethod(
        method: JIRMethod,
        call: JIRVirtualCallExpr,
        location: JIRInst,
        context: MethodContext
    ): List<MethodResolutionResult> = buildList {
        if (methodMayBeLambda(method)) {
            this += MethodResolutionResult.Lambda(call, method)
        }

        val overrides = methodOverrides(method)
        if (overrides.isEmpty()) {
            if (!method.isAbstract) {
                this += attachContext(method, context, call, location)
            } else {
                this += MethodResolutionResult.MethodResolutionFailed
            }
            return@buildList
        }

        resolveVirtualMethodAtLocation(method, overrides, call, location, context)
            .mapTo(this) { MethodResolutionResult.ConcreteMethod(it) }
    }

    private fun methodMayBeLambda(method: JIRMethod): Boolean {
        if (!method.isAbstract) return false
        if (!method.enclosingClass.isInterface) return false
        // class is SAM
        return method.enclosingClass.declaredMethods.count { it.isAbstract } == 1
    }

    private fun attachContext(
        method: JIRMethod,
        context: MethodContext,
        call: JIRCallExpr,
        location: JIRInst
    ): List<MethodResolutionResult.ConcreteMethod> {
        if (call !is JIRInstanceCallExpr) {
            return listOf(MethodResolutionResult.ConcreteMethod(MethodWithContext(method, EmptyMethodContext)))
        }

        val instanceTypes = resolveValueClass(call.instance, location, context)
        return attachContext(call, location, method, instanceTypes)
            .map { MethodResolutionResult.ConcreteMethod(it) }
    }

    private fun attachContext(
        call: JIRCallExpr,
        location: JIRInst,
        method: JIRMethod,
        instanceTypes: Set<JIRClassOrInterface>?
    ): List<MethodWithContext> {
        if (instanceTypes.isNullOrEmpty()) {
            logger.warn { "No instance type for $call at ${location.location.method}" }
            return listOf(MethodWithContext(method, EmptyMethodContext))
        }

        // Method instance type is concrete enough
        if (instanceTypes.any { method.enclosingClass.isSubClassOf(it) }) {
            return listOf(MethodWithContext(method, EmptyMethodContext))
        }

        return instanceTypes.map {
            val ctx = JIRInstanceTypeMethodContext(it)
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

        val instanceTypes = resolveValueClass(call.instance, location, context)
        if (instanceTypes.isNullOrEmpty()) {
            return methods.flatMap { attachContext(call, location, baseMethod, instanceTypes) }
        }

        return methods.filter { method ->
            instanceTypes.any { type ->
                method.enclosingClass.isSubClassOf(type) || type.isSubClassOf(method.enclosingClass)
            }
        }.flatMap { attachContext(call, location, it, instanceTypes) }
    }

    private val valueCache = ConcurrentHashMap<Triple<JIRValue, JIRInst, MethodContext>, Optional<Set<JIRClassOrInterface>>>()

    private fun resolveValueClass(
        value: JIRValue,
        location: JIRInst,
        context: MethodContext,
    ): Set<JIRClassOrInterface>? = valueCache.computeIfAbsent(Triple(value, location, context)) {
        resolveValueClass(value, location, context, hashSetOf())
            ?.filterNotTo(hashSetOf()) { it.isInterface }
            .let { Optional.ofNullable(it) }
    }.getOrNull()

    private fun resolveValueClass(
        value: JIRValue,
        location: JIRInst,
        context: MethodContext,
        visitedValues: MutableSet<JIRValue>
    ): Set<JIRClassOrInterface>? {
        val valueCls = (value.type as? JIRRefType)?.jIRClass ?: return null

        if (value !is JIRLocalVar || value in visitedValues) {
            return when (context) {
                EmptyMethodContext -> setOf(valueCls)
                is JIRInstanceTypeMethodContext -> {
                    val type = if (value is JIRThis) selectClass(valueCls, context.type) else valueCls
                    setOf(type)
                }
                else -> error("Unexpected value for $context")
            }
        }

        val (assignments, catchers) = findAllAssignmentsToValue(value, location)
        if (assignments.isEmpty() && catchers.isEmpty()) {
            logger.warn { "No assignments to $value in ${location.location.method}" }
            return setOf(valueCls)
        }

        val resolvedTypes = hashSetOf<JIRClassOrInterface>()

        visitedValues.add(value)
        for (assignment in assignments) {
            val expr = assignment.rhv
            val exprCls = (expr.type as? JIRRefType)?.jIRClass ?: return null

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
                (it as? JIRRefType)?.jIRClass
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
