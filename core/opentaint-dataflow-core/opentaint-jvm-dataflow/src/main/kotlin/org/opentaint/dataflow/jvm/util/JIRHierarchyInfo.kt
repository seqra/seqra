package org.opentaint.dataflow.jvm.util

import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongLongImmutablePair
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabasePersistence
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.findDeclaredMethodOrNull
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.InMemoryHierarchyCache
import org.opentaint.ir.impl.features.findInMemoryHierarchy
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

class JIRHierarchyInfo(val cp: JIRClasspath) {
    val persistence: JIRDatabasePersistence
    private val hierarchy: InMemoryHierarchyCache
    private val registeredLocationIds: LongOpenHashSet

    init {
        val hierarchyFeature = cp.db.findInMemoryHierarchy()
            ?: error("In memory hierarchy required")

        persistence = cp.db.persistence
        hierarchy = InMemoryHierarchyAccess.accessCacheField(hierarchyFeature)

        val registerLocIds = LongOpenHashSet()
        cp.registeredLocations.forEach { registerLocIds.add(it.id) }
        registeredLocationIds = registerLocIds
    }

    inline fun forEachSubClassName(cls: String, body: (String) -> Unit) =
        forEachSubClassName(persistence.findSymbolId(cls), body)

    inline fun forEachSubClassName(rootClsId: Long, body: (String) -> Unit) =
        forEachSubClassId(rootClsId) { clsId ->
            body(persistence.findSymbolName(clsId))
        }

    fun findOverrides(method: JIRMethod, baseClass: JIRClassOrInterface, allowedLocations: LongOpenHashSet): List<JIRMethod> {
        if (method.isFinal || method.isConstructor || method.isStatic || method.isClassInitializer) {
            return emptyList()
        }

        val desc = method.description
        val name = method.name

        val clsName = baseClass.name
        val clsId = persistence.findSymbolId(clsName)

        val result = mutableListOf<JIRMethod>()

        forEachAllowedSubClassId(clsId, allowedLocations) { subclassId ->
            val subClassName = persistence.findSymbolName(subclassId)
            val subClass = cp.findClassOrNull(subClassName)
            val methodOverride = subClass?.findDeclaredMethodOrNull(name, desc)
            if (methodOverride != null && !methodOverride.isPrivate) {
                result.add(methodOverride)
            }
        }

        return result
    }

    inline fun forEachAllowedSubClassId(rootClsId: Long, allowedLocations: LongOpenHashSet, body: (Long) -> Unit) {
        val unprocessedSubclasses = mutableListOf<LongLongImmutablePair>()
        val processedSubClasses = LongOpenHashSet()
        addSubclassesIdsWithLocations(rootClsId, unprocessedSubclasses)

        while (unprocessedSubclasses.isNotEmpty()) {
            val clsWithLocation = unprocessedSubclasses.removeLast()
            val clsId = clsWithLocation.firstLong()
            if (!processedSubClasses.add(clsId)) continue

            val locationId = clsWithLocation.secondLong()
            if (allowedLocations.contains(locationId)) {
                body(clsId)
            }

            addSubclassesIdsWithLocations(clsId, unprocessedSubclasses)
        }
    }

    inline fun forEachSubClassId(rootClsId: Long, body: (Long) -> Unit) {
        val unprocessedSubclasses = LongArrayList()
        val processedSubClasses = LongOpenHashSet()
        addSubclassesIds(rootClsId, unprocessedSubclasses)

        while (unprocessedSubclasses.isNotEmpty()) {
            val clsId = unprocessedSubclasses.removeLong(unprocessedSubclasses.lastIndex)
            if (!processedSubClasses.add(clsId)) continue

            body(clsId)

            addSubclassesIds(clsId, unprocessedSubclasses)
        }
    }

    fun addSubclassesIds(clsId: Long, result: LongArrayList) {
        val subclasses = hierarchy[clsId] ?: return
        for ((location, ids) in subclasses) {
            if (location in registeredLocationIds) {
                result.addAll(ids)
            }
        }
    }

    fun addSubclassesIdsWithLocations(clsId: Long, result: MutableList<LongLongImmutablePair>) {
        val subclasses = hierarchy[clsId] ?: return
        for ((location, ids) in subclasses) {
            if (location in registeredLocationIds) {
                ids.mapTo(result) { LongLongImmutablePair(it, location) }
            }
        }
    }

    private object InMemoryHierarchyAccess {
        @Suppress("UNCHECKED_CAST")
        fun accessCacheField(hierarchy: InMemoryHierarchy): InMemoryHierarchyCache {
            val allProperties = hierarchy::class.declaredMemberProperties
            val cacheProperty = allProperties.single { it.name == "cache" } as KProperty1<InMemoryHierarchy, InMemoryHierarchyCache>
            cacheProperty.isAccessible = true
            return cacheProperty.get(hierarchy)
        }
    }
}
