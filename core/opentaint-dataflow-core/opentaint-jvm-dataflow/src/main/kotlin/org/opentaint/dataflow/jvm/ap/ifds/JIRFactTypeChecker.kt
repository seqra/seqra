package org.opentaint.dataflow.jvm.ap.ifds

import it.unimi.dsi.fastutil.longs.LongLongImmutablePair
import it.unimi.dsi.fastutil.longs.LongLongPair
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.FactTypeChecker.AlwaysAcceptFilter
import org.opentaint.dataflow.ap.ifds.FactTypeChecker.CompatibilityFilterResult
import org.opentaint.dataflow.ap.ifds.FactTypeChecker.FactApFilter
import org.opentaint.dataflow.ap.ifds.FactTypeChecker.FilterResult
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.ValueAccessor
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.util.JIRHierarchyInfo
import org.opentaint.ir.api.common.CommonType
import org.opentaint.ir.api.jvm.JIRArrayType
import org.opentaint.ir.api.jvm.JIRBoundedWildcard
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRPrimitiveType
import org.opentaint.ir.api.jvm.JIRRefType
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.JIRTypeVariable
import org.opentaint.ir.api.jvm.JIRUnboundWildcard
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.ext.ifArrayGetElementType
import org.opentaint.ir.api.jvm.ext.isAssignable
import org.opentaint.ir.api.jvm.ext.isSubClassOf
import org.opentaint.ir.api.jvm.ext.objectType
import org.opentaint.ir.impl.features.classpaths.JIRUnknownType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

class JIRFactTypeChecker(private val cp: JIRClasspath) : FactTypeChecker {
    private val hierarchyInfo = JIRHierarchyInfo(cp)

    private val objectType by lazy { cp.objectType }
    private val objectClass by lazy { objectType.jIRClass }

    val localFactsTotal = LongAdder()
    val localFactsRejected = LongAdder()

    private fun Boolean.logLocalFactCheck(): Boolean = also { isCorrect ->
        localFactsTotal.increment()
        if (!isCorrect) localFactsRejected.increment()
    }

    val accessTotal = LongAdder()
    val accessRejected = LongAdder()

    private fun Boolean.logAccessCheck(): Boolean = also { isCorrect ->
        accessTotal.increment()
        if (!isCorrect) accessRejected.increment()
    }

    val compatibilityTotal = LongAdder()
    val compatibilityRejected = LongAdder()

    private fun Boolean.logCompatibilityCheck(): Boolean = also { isCorrect ->
        compatibilityTotal.increment()
        if (!isCorrect) compatibilityRejected.increment()
    }

    private inner class AccessorFilter(
        private val actualType: JIRType,
        private val isLocalCheck: Boolean
    ) : FactApFilter {
        override fun check(accessor: Accessor): FilterResult = checkAccessor(accessor).also {
            val result = it !== FilterResult.Reject
            if (isLocalCheck) result.logLocalFactCheck() else result.logAccessCheck()
        }

        private fun checkAccessor(accessor: Accessor): FilterResult {
            when (accessor) {
                is TaintMarkAccessor, FinalAccessor, AnyAccessor, is ClassStaticAccessor -> return FilterResult.Accept
                is FieldAccessor -> {
                    if (actualType !is JIRRefType) return FilterResult.Reject

                    val factType = fieldClassType(accessor) ?: return FilterResult.Accept
                    if (!typeMayHaveSubtypeOf(actualType, factType)) return FilterResult.Reject
                    return FilterResult.Accept
                }

                ElementAccessor -> {
                    if (actualType !is JIRRefType) return FilterResult.Reject
                    if (!typeMayBeArrayType(actualType)) return FilterResult.Reject

                    val actualElementType = actualType.ifArrayGetElementType ?: return FilterResult.Accept

                    return FilterResult.FilterNext(
                        AccessorFilter(actualElementType, isLocalCheck)
                    )
                }

                ValueAccessor -> {
                    return if (actualType is JIRPrimitiveType) {
                        FilterResult.Accept
                    } else {
                        FilterResult.Reject
                    }
                }
            }
        }
    }

    private inner class AccessorCompatibilityFilter(
        private val actualType: JIRType
    ) : FactTypeChecker.FactCompatibilityFilter {
        override fun check(accessor: Accessor): CompatibilityFilterResult  = checkAccessor(accessor).also {
            val result = it !== CompatibilityFilterResult.NotCompatible
            result.logCompatibilityCheck()
        }

        private fun checkAccessor(accessor: Accessor): CompatibilityFilterResult {
            if (accessor !is FieldAccessor) return CompatibilityFilterResult.Compatible

            val fieldType = fieldAccessorType(accessor)
                ?: return CompatibilityFilterResult.Compatible

            if (!typesCompatible(actualType, fieldType)) return CompatibilityFilterResult.NotCompatible

            return CompatibilityFilterResult.Compatible
        }
    }

    override fun filterFactByLocalType(actualType: CommonType?, factAp: FinalFactAp): FinalFactAp? {
        if (actualType == null) return factAp
        jIRDowncast<JIRType>(actualType)

        val filter = AccessorFilter(actualType, isLocalCheck = true)
        return factAp.filterFact(filter)
    }

    override fun accessPathFilter(accessPath: List<Accessor>): FactApFilter {
        val actualType = accessorActualType(accessPath) ?: return AlwaysAcceptFilter
        return AccessorFilter(actualType, isLocalCheck = false)
    }

    override fun accessPathCompatibilityFilter(accessPath: List<Accessor>): FactTypeChecker.FactCompatibilityFilter {
        val actualType = accessorActualType(accessPath) ?: return FactTypeChecker.AlwaysCompatibleFilter
        return AccessorCompatibilityFilter(actualType)
    }

    fun callArgumentMayBeArray(call: JIRCallExpr, arg: AccessPathBase.Argument): Boolean {
        val argument = call.args.getOrNull(arg.idx) ?: return false
        val argType = argument.type
        return argType.mayBeArray()
    }

    fun JIRType.mayBeArray(): Boolean {
        if (this !is JIRRefType) return false
        return typeMayBeArrayType(this)
    }

    private fun accessorActualType(accessPath: List<Accessor>): JIRType? {
        val accessor = accessPath.lastOrNull() ?: return null
        return when (accessor) {
            is FieldAccessor -> fieldAccessorType(accessor)
            ElementAccessor -> {
                val prevAccessors = accessPath.subList(0, accessPath.size - 1)
                accessorActualType(prevAccessors)?.ifArrayGetElementType
            }
            ValueAccessor -> {
                val prevAccessors = accessPath.subList(0, accessPath.size - 1)
                accessorActualType(prevAccessors)
            }

            is TaintMarkAccessor, FinalAccessor, AnyAccessor, is ClassStaticAccessor -> null
        }
    }

    private fun fieldAccessorType(accessor: FieldAccessor): JIRType? {
        return cp.findTypeOrNull(accessor.fieldType)
    }

    private fun fieldClassType(accessor: FieldAccessor): JIRClassType? {
        return cp.findTypeOrNull(accessor.className) as? JIRClassType
    }

    private fun typeMayBeArrayType(type: JIRRefType): Boolean = when (type) {
        is JIRArrayType -> true
        is JIRClassType -> type == objectType
        is JIRTypeVariable -> type.bounds.all { bound -> typeMayBeArrayType(bound) }

        // todo: check wildcards
        is JIRUnboundWildcard, is JIRBoundedWildcard -> true

        else -> error("Unexpected type: $type")
    }

    private fun typesCompatible(t1: JIRType, t2: JIRType): Boolean {
        if (t1 == t2) return true
        if (t1 is JIRUnknownType || t2 is JIRUnknownType) return true
        if (t1.isAssignable(t2) || t2.isAssignable(t1)) return true
        if (t1 !is JIRRefType || t2 !is JIRRefType) return false
        if (t1 !is JIRClassType && t2 !is JIRClassType) return true
        if (t1 is JIRClassType && typeMayHaveSubtypeOf(t2, t1)) return true
        if (t2 is JIRClassType && typeMayHaveSubtypeOf(t1, t2)) return true
        return false
    }

    private fun typeMayHaveSubtypeOf(type: JIRRefType, requiredType: JIRClassType): Boolean = when (type) {
        is JIRClassType -> if (type.jIRClass.isInterface) {
            interfaceMayHaveSubtypeOf(type.jIRClass, requiredType.jIRClass)
        } else {
            requiredType.isAssignable(type) || type.isAssignable(requiredType)
        }

        is JIRArrayType -> requiredType.isAssignable(type)
        is JIRTypeVariable -> type.bounds.all { bound -> typeMayHaveSubtypeOf(bound, requiredType) }

        // todo: check wildcards
        is JIRUnboundWildcard, is JIRBoundedWildcard -> true

        else -> error("Unexpected type: $type")
    }

    // todo: cache limit?
    private val typeMayHaveSubtypeOfCache = ConcurrentHashMap<LongLongPair, Boolean>()

    fun typeMayHaveSubtypeOf(typeName: String, requiredTypeName: String): Boolean {
        if (requiredTypeName == "java.lang.Object") return true
        if (typeName == "java.lang.Object") return true

        if (typeName.endsWith("[]")) {
            return requiredTypeName.endsWith("[]")
        }

        if (requiredTypeName.endsWith("[]")) {
            return false
        }

        val typeNameId = hierarchyInfo.persistence.findSymbolId(typeName)
        val requiredTypeNameId = hierarchyInfo.persistence.findSymbolId(requiredTypeName)

        val cacheKey = LongLongImmutablePair(typeNameId, requiredTypeNameId)
        return typeMayHaveSubtypeOfCache.computeIfAbsent(cacheKey) {
            computeTypeMayHaveSubtypeOf(typeName, requiredTypeName)
        }
    }

    private fun computeTypeMayHaveSubtypeOf(
        typeName: String, requiredTypeName: String
    ): Boolean {
        val typeCls = cp.findClassOrNull(typeName) ?: return true
        val requiredTypeCls = cp.findClassOrNull(requiredTypeName) ?: return true

        return if (typeCls.isInterface) {
            interfaceMayHaveSubtypeOf(typeCls, requiredTypeCls)
        } else {
            typeCls.isSubClassOf(requiredTypeCls) || requiredTypeCls.isSubClassOf(typeCls)
        }
    }

    // todo: cache limit?
    private val interfaceMayHaveSubtypeOfCache = ConcurrentHashMap<LongLongPair, Boolean>()

    private fun interfaceMayHaveSubtypeOf(
        interfaceType: JIRClassOrInterface,
        requiredType: JIRClassOrInterface
    ): Boolean {
        if (requiredType == objectClass) return true

        val requiredTypeId = hierarchyInfo.persistence.findSymbolId(requiredType.name)
        val interfaceTypeId = hierarchyInfo.persistence.findSymbolId(interfaceType.name)

        val cacheKey = LongLongImmutablePair(requiredTypeId, interfaceTypeId)
        return interfaceMayHaveSubtypeOfCache.computeIfAbsent(cacheKey) {
            computeInterfaceMayHaveSubtypeOf(requiredType, interfaceType, requiredTypeId)
        }
    }

    private fun computeInterfaceMayHaveSubtypeOf(
        requiredType: JIRClassOrInterface,
        interfaceType: JIRClassOrInterface,
        requiredTypeId: Long
    ): Boolean {
        val subClassCheckCache = hashSetOf<JIRClassOrInterface>()
        if (isSubClassOfInterface(requiredType, interfaceType, subClassCheckCache)) return true

        if (requiredType.isFinal) return false

        hierarchyInfo.forEachSubClassName(requiredTypeId) { className ->
            val cls = cp.findClassOrNull(className) ?: return true
            if (isSubClassOfInterface(cls, interfaceType, subClassCheckCache)) return true
        }

        return false
    }

    private fun isSubClassOfInterface(
        currentCls: JIRClassOrInterface,
        interfaceType: JIRClassOrInterface,
        checkedTypes: MutableSet<JIRClassOrInterface>
    ): Boolean {
        val uncheckedClasses = mutableListOf(currentCls)
        while (uncheckedClasses.isNotEmpty()) {
            val cls = uncheckedClasses.removeLast()
            if (cls == interfaceType) return true

            if (!checkedTypes.add(cls)) continue

            cls.superClass?.let { uncheckedClasses.add(it) }

            uncheckedClasses.addAll(cls.interfaces)
        }
        return false
    }
}
