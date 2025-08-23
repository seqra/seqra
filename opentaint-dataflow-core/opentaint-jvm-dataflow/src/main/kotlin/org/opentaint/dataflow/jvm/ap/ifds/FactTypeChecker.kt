package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRArrayType
import org.opentaint.ir.api.jvm.JIRBoundedWildcard
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRDatabasePersistence
import org.opentaint.ir.api.jvm.JIRRefType
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.JIRTypeVariable
import org.opentaint.ir.api.jvm.JIRUnboundWildcard
import org.opentaint.ir.api.jvm.ext.ifArrayGetElementType
import org.opentaint.ir.api.jvm.ext.isAssignable
import org.opentaint.ir.api.jvm.ext.objectType
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.InMemoryHierarchyCache
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import java.util.concurrent.atomic.LongAdder
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

class FactTypeChecker(private val cp: JIRClasspath) {
    private val persistence: JIRDatabasePersistence
    private val hierarchy: InMemoryHierarchyCache
    private val registeredLocationIds: Set<Long>

    init {
        check(cp.db.isInstalled(InMemoryHierarchy)) { "In memory hierarchy required" }
        persistence = cp.db.persistence
        val allHierarchies = InMemoryHierarchyAccess.accessHierarchiesField(InMemoryHierarchy)
        hierarchy = checkNotNull(allHierarchies[cp.db])
        registeredLocationIds = cp.registeredLocations.mapTo(hashSetOf()) { it.id }
    }

    private val objectType by lazy { cp.objectType }
    private val objectClass by lazy { objectType.jirClass }

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

    sealed interface FilterResult {
        data object Accept : FilterResult
        data object Reject : FilterResult
        data class FilterNext(val filter: FactApFilter) : FilterResult
    }

    interface FactApFilter {
        fun check(accessor: Accessor): FilterResult
    }

    object AlwaysAcceptFilter : FactApFilter {
        override fun check(accessor: Accessor): FilterResult = FilterResult.Accept
    }

    object AlwaysRejectFilter : FactApFilter {
        override fun check(accessor: Accessor): FilterResult = FilterResult.Reject
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
                is TaintMarkAccessor, FinalAccessor -> return FilterResult.Accept
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
            }
        }
    }

    fun filterFactByLocalType(actualType: JIRType?, factAp: FinalFactAp): FinalFactAp? {
        if (actualType == null) return factAp
        val filter = AccessorFilter(actualType, isLocalCheck = true)
        return factAp.filterFact(filter)
    }

    fun accessPathFilter(accessPath: List<Accessor>): FactApFilter {
        val actualType = accessorActualType(accessPath) ?: return AlwaysAcceptFilter
        return AccessorFilter(actualType, isLocalCheck = false)
    }

    private fun accessorActualType(accessPath: List<Accessor>): JIRType? {
        val accessor = accessPath.lastOrNull() ?: return null
        return when (accessor) {
            is FieldAccessor -> fieldAccessorType(accessor)
            ElementAccessor -> {
                val prevAccessors = accessPath.subList(0, accessPath.size - 1)
                accessorActualType(prevAccessors)?.ifArrayGetElementType
            }

            is TaintMarkAccessor, FinalAccessor -> null
        }
    }

    fun fieldAccessorType(accessor: FieldAccessor): JIRType? {
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

    private fun typeMayHaveSubtypeOf(type: JIRRefType, requiredType: JIRClassType): Boolean = when (type) {
        is JIRClassType -> if (type.jirClass.isInterface) {
            interfaceMayHaveSubtypeOf(type.jirClass, requiredType.jirClass)
        } else {
            requiredType.isAssignable(type) || type.isAssignable(requiredType)
        }

        is JIRArrayType -> requiredType.isAssignable(type)
        is JIRTypeVariable -> type.bounds.all { bound -> typeMayHaveSubtypeOf(bound, requiredType) }

        // todo: check wildcards
        is JIRUnboundWildcard, is JIRBoundedWildcard -> true

        else -> error("Unexpected type: $type")
    }

    private fun interfaceMayHaveSubtypeOf(
        interfaceType: JIRClassOrInterface,
        requiredType: JIRClassOrInterface
    ): Boolean {
        if (requiredType == objectClass) return true

        val subClassCheckCache = hashSetOf<JIRClassOrInterface>()
        if (isSubClassOfInterface(requiredType, interfaceType, subClassCheckCache)) return true

        if (requiredType.isFinal) return false

        val requiredTypeId = persistence.findSymbolId(requiredType.name)

        val unprocessedSubclasses = mutableListOf<Long>()
        val processedSubClasses = hashSetOf<Long>()
        addSubclassesIds(requiredTypeId, unprocessedSubclasses)

        while (unprocessedSubclasses.isNotEmpty()) {
            val clsId = unprocessedSubclasses.removeLast()
            if (!processedSubClasses.add(clsId)) continue

            val className = persistence.findSymbolName(clsId)
            val cls = cp.findClassOrNull(className) ?: return true

            if (isSubClassOfInterface(cls, interfaceType, subClassCheckCache)) return true

            addSubclassesIds(clsId, unprocessedSubclasses)
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

    private fun addSubclassesIds(clsId: Long, result: MutableList<Long>) {
        val subclasses = hierarchy[clsId] ?: return
        for ((location, ids) in subclasses) {
            if (location in registeredLocationIds) {
                result.addAll(ids)
            }
        }
    }

    private object InMemoryHierarchyAccess {
        fun accessHierarchiesField(hierarchy: InMemoryHierarchy): Map<JIRDatabase, InMemoryHierarchyCache> {
            val allProperties = hierarchy::class.declaredMemberProperties
            val hierarchiesProperty = allProperties.single { it.name == "hierarchies" }
            hierarchiesProperty.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            return hierarchiesProperty.call() as Map<JIRDatabase, InMemoryHierarchyCache>
        }
    }
}
