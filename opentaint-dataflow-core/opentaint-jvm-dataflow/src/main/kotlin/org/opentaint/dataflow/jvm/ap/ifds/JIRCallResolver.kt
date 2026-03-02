package org.opentaint.dataflow.jvm.ap.ifds

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.CombinedMethodContext
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.combine
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAllocInfo
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasApInfo
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature.JIRLambdaClass
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodAnalysisContext
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.jvm.util.JIRHierarchyInfo
import org.opentaint.dataflow.util.cartesianProductMapTo
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRRefType
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRLambdaExpr
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRNewExpr
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.cfg.JIRVirtualCallExpr
import org.opentaint.ir.api.jvm.ext.findMethodOrNull
import org.opentaint.ir.api.jvm.ext.isSubClassOf
import org.opentaint.ir.impl.cfg.util.isClass
import org.opentaint.jvm.util.toJIRClassOrInterface
import java.util.concurrent.ConcurrentHashMap

class JIRCallResolver(
    cp: JIRClasspath,
    private val unitResolver: JIRUnitResolver
) {
    private val hierarchy = JIRHierarchyInfo(cp)
    private val knownLocationIds = LongOpenHashSet()

    init {
        cp.registeredLocations
            .filterNot { unitResolver.locationIsUnknown(it) }
            .forEach { knownLocationIds.add(it.id) }
    }

    private val methodOverridesCache = ConcurrentHashMap<JIRMethod, List<JIRMethod>>()

    private fun methodOverrides(method: JIRMethod, baseClass: JIRClassOrInterface): List<JIRMethod> {
        if (method.isFinal || method.isConstructor || method.isStatic || method.isClassInitializer) {
            return emptyList()
        }

        return methodOverridesCache.computeIfAbsent(method) {
            val overrides = hierarchy.findOverrides(method, baseClass, knownLocationIds)
            val knownOverrides = overrides.filter { unitResolver.resolve(it) != UnknownUnit }
            knownOverrides.ifEmpty { emptyList() }
        }
    }

    sealed interface MethodResolutionResult {
        data object MethodResolutionFailed : MethodResolutionResult
        data class ConcreteMethod(val method: MethodWithContext) : MethodResolutionResult
        data class Lambda(val instance: JIRVirtualCallExpr, val method: JIRMethod) : MethodResolutionResult
    }

    fun allKnownOverridesOrNull(method: JIRMethod): List<JIRMethod>? {
        val methods = mutableListOf<JIRMethod>()

        if (unitResolver.resolve(method) != UnknownUnit) {
            if (!method.isAbstract) {
                methods.add(method)
            }
        }

        if (!method.isObjectMethod()) {
            methods += methodOverrides(method, method.enclosingClass)
        }

        methods.removeAll { unitResolver.resolve(it) == UnknownUnit }

        return methods.ifEmpty { null }
    }

    fun resolve(call: JIRCallExpr, location: JIRInst, context: JIRMethodAnalysisContext): List<MethodResolutionResult> {
        val method = call.method.method
        val methodIgnored = unitResolver.resolve(method) == UnknownUnit

        if (methodIgnored && method.isObjectMethod()) {
            return listOf(MethodResolutionResult.MethodResolutionFailed)
        }

        if (call is JIRLambdaExpr) {
            // lambda expr is an allocation site. lambda calls resolved as virtual calls
            return emptyList()
        }

        if (call !is JIRVirtualCallExpr) {
            if (methodIgnored) {
                return listOf(MethodResolutionResult.MethodResolutionFailed)
            }

            val ctxBuilder = MethodContextCreator(context, call, location, instanceTypeConstraints = null)
            return ctxBuilder.attachContext(method)
                .map { MethodResolutionResult.ConcreteMethod(it) }
        }

        return resolveVirtualMethod(method, call, location, context)
    }

    private fun resolveVirtualMethod(
        baseMethod: JIRMethod,
        call: JIRVirtualCallExpr,
        location: JIRInst,
        context: JIRMethodAnalysisContext
    ): List<MethodResolutionResult> {
        val instanceTypeConstraints = resolveValueTypeConstraints(call.instance, location, context)
        val classMethods = instanceTypeConstraints.orEmpty().mapNotNullTo(hashSetOf()) { constraint ->
            val classMethod = constraint.type.findMethodOrNull(baseMethod.name, baseMethod.description)
                ?: return@mapNotNullTo null

            if (constraint.exactType) {
                if (!classMethod.isValidConcreteMethod()) return@mapNotNullTo null
            }

            classMethod to constraint
        }

        if (classMethods.isEmpty()) {
            classMethods.add(baseMethod to TypeConstraintInfo(baseMethod.enclosingClass, exactType = false))
        }

        val result = mutableListOf<MethodResolutionResult>()

        val methods = hashSetOf<Pair<JIRMethod, TypeConstraintInfo>>()
        for ((method, constraint) in classMethods) {
            if (constraint.exactType) {
                methods += method to constraint
                continue
            }

            if (methodMayBeLambda(method)) {
                result += MethodResolutionResult.Lambda(call, method)
            }

            val overrides = methodOverrides(method, constraint.type).filter {
                it.enclosingClass !is JIRLambdaClass // Lambdas handled by JIRLambdaTracker
            }

            overrides.mapTo(methods) { it to constraint }

            if (method.isValidConcreteMethod()) {
                methods += method to constraint
            }
        }

        if (methods.isEmpty()) {
            result += MethodResolutionResult.MethodResolutionFailed
            return result
        }

        val ctxBuilder = MethodContextCreator(context, call, location, instanceTypeConstraints = null)
        val methodsWithContext = methods.flatMapTo(hashSetOf()) { (m, constraint) ->
            ctxBuilder.withInstanceTypeConstraint(constraint).attachContext(m)
        }

        methodsWithContext.mapTo(result) {
            MethodResolutionResult.ConcreteMethod(it)
        }

        return result
    }

    private fun JIRMethod.isValidConcreteMethod(): Boolean =
        !isAbstract && unitResolver.resolve(this) != UnknownUnit

    private fun methodMayBeLambda(method: JIRMethod): Boolean {
        if (!method.isAbstract) return false
        if (!method.enclosingClass.isInterface) return false
        // class is SAM
        return method.enclosingClass.declaredMethods.count { it.isAbstract } == 1
    }

    private fun resolveValueTypeConstraints(
        value: JIRValue,
        location: JIRInst,
        context: JIRMethodAnalysisContext,
    ): Set<TypeConstraintInfo>? {
        val epContext = context.methodEntryPoint.context
        val valueCls = (value.type as? JIRRefType)?.jIRClass ?: return null
        val defaultConstraint = TypeConstraintInfo(valueCls, exactType = false)

        if (value !is JIRLocalVar) {
            val base = MethodFlowFunctionUtils.accessPathBase(value)
            val contextType = base?.let { epContext.baseTypeConstraint(it) }
            val type = contextType?.let { selectTypeConstraint(defaultConstraint, it) } ?: defaultConstraint
            return setOf(type)
        }

        val aliasInfo = context.aliasAnalysis
            ?: return setOf(defaultConstraint)

        val aliases = aliasInfo.findAlias(AccessPathBase.LocalVar(value.index), location)
            ?: return setOf(defaultConstraint)

        val resultConstraints = hashSetOf<TypeConstraintInfo>()
        val contextMethodInstList = context.methodEntryPoint.method.let { it as JIRMethod }.instList
        for (aliasInfo in aliases) {
            when (aliasInfo) {
                is AliasAllocInfo -> {
                    val allocInst = contextMethodInstList.getOrNull(aliasInfo.allocInst)
                        ?: continue

                    val allocExpr = (allocInst as? JIRAssignInst)?.rhv as? JIRNewExpr
                        ?: continue

                    val allocType = allocExpr.type as? JIRClassType ?: continue
                    resultConstraints += TypeConstraintInfo(allocType.jIRClass, exactType = true)
                }

                is AliasApInfo -> {
                    if (aliasInfo.accessors.isNotEmpty()) continue
                    if (aliasInfo.base is AccessPathBase.LocalVar) continue

                    val contextType = epContext.baseTypeConstraint(aliasInfo.base)
                    val type = contextType?.let { selectTypeConstraint(defaultConstraint, it) } ?: defaultConstraint
                    resultConstraints += type
                }
            }
        }

        if (resultConstraints.isEmpty()) {
            return setOf(defaultConstraint)
        }

        return resultConstraints
    }

    private fun MethodContext.baseTypeConstraint(base: AccessPathBase): TypeConstraintInfo? = when (this) {
        is EmptyMethodContext -> null
        is JIRInstanceTypeMethodContext -> typeConstraint.takeIf { base is AccessPathBase.This }
        is JIRArgumentTypeMethodContext -> typeConstraint.takeIf { base is AccessPathBase.Argument && base.idx == argIdx }
        is CombinedMethodContext -> first.baseTypeConstraint(base) ?: second.baseTypeConstraint(base)
        else -> error("Unexpected value for context: $this")
    }

    private fun selectTypeConstraint(left: TypeConstraintInfo, right: TypeConstraintInfo): TypeConstraintInfo {
        if (left.exactType) return left
        if (right.exactType) return right
        val cls = selectClass(left.type, right.type)
        return TypeConstraintInfo(cls, exactType = false)
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

    private sealed interface ContextTypeInfo {
        fun typeCls(method: JIRMethod): JIRClassOrInterface?

        data object Instance : ContextTypeInfo {
            override fun typeCls(method: JIRMethod): JIRClassOrInterface = method.enclosingClass
        }

        data class Param(val idx: Int, val type: TypeName) : ContextTypeInfo {
            override fun typeCls(method: JIRMethod): JIRClassOrInterface? =
                type.toJIRClassOrInterface(method.enclosingClass.classpath)
        }
    }

    private inner class MethodContextCreator(
        val context: JIRMethodAnalysisContext,
        val call: JIRCallExpr,
        val location: JIRInst,
        instanceTypeConstraints: Set<TypeConstraintInfo>?,
        private val paramTypeConstraints: MutableMap<Int, Set<TypeConstraintInfo>> = hashMapOf(),
    ) {
        fun withInstanceTypeConstraint(constraint: TypeConstraintInfo): MethodContextCreator =
            MethodContextCreator(context, call, location, setOf(constraint), paramTypeConstraints)

        private val instanceTypes by lazy {
            if (call !is JIRInstanceCallExpr) return@lazy null
            instanceTypeConstraints
                ?: resolveValueTypeConstraints(call.instance, location, context)
        }

        private fun paramTypeConstraints(paramIdx: Int): Set<TypeConstraintInfo> {
            val arg = call.args.getOrNull(paramIdx) ?: return emptySet()
            return paramTypeConstraints.getOrPut(paramIdx) {
                resolveValueTypeConstraints(arg, location, context).orEmpty()
            }
        }

        fun attachContext(method: JIRMethod): List<MethodWithContext> {
            val contextTypeInfo = mutableListOf<ContextTypeInfo>()
            if (call is JIRInstanceCallExpr && !method.isConstructor) {
                contextTypeInfo += ContextTypeInfo.Instance
            }

            method.parameters.forEach { param ->
                if (!param.type.isClass) return@forEach
                if (param.type.isObject()) return@forEach

                contextTypeInfo += ContextTypeInfo.Param(param.index, param.type)
            }

            val methodContexts = createContexts(method, contextTypeInfo)
            return methodContexts.map { MethodWithContext(method, it) }
        }

        private fun createContexts(method: JIRMethod, typeInfos: List<ContextTypeInfo>): List<MethodContext> {
            if (typeInfos.isEmpty()) return listOf(EmptyMethodContext)

            val contexts = typeInfos.map { typeInfo ->
                val typeConstraints = resolveTypes(typeInfo)

                if (typeConstraints.isNullOrEmpty()) {
                    return@map listOf(EmptyMethodContext)
                }

                val type = typeInfo.typeCls(method)
                    ?: return@map listOf(EmptyMethodContext)

                if (typeConstraints.any { type.isSubClassOf(it.type) }) {
                    return@map listOf(EmptyMethodContext)
                }

                mkMethodContext(typeInfo, typeConstraints)
            }

            val result = mutableListOf<MethodContext>()
            contexts.cartesianProductMapTo { contextArray ->
                val context = contextArray.toSet().combine()
                result += context
            }
            return result
        }

        private fun resolveTypes(typeInfo: ContextTypeInfo): Set<TypeConstraintInfo>? = when (typeInfo) {
            is ContextTypeInfo.Instance -> instanceTypes
            is ContextTypeInfo.Param -> paramTypeConstraints(typeInfo.idx)
        }

        private fun mkMethodContext(
            typeInfo: ContextTypeInfo,
            constraints: Set<TypeConstraintInfo>
        ): List<MethodContext> = when (typeInfo) {
            is ContextTypeInfo.Instance -> constraints.map { JIRInstanceTypeMethodContext(it) }
            is ContextTypeInfo.Param -> constraints.map { JIRArgumentTypeMethodContext(typeInfo.idx, it) }
        }
    }

    private fun JIRMethod.isObjectMethod(): Boolean =
        enclosingClass.name == "java.lang.Object"

    private fun TypeName.isObject(): Boolean =
        typeName == "java.lang.Object"
}
