package org.opentaint.dataflow.jvm.util

import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabasePersistence
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.InMemoryHierarchyCache
import org.opentaint.ir.impl.features.findInMemoryHierarchy
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

class JIRHierarchyInfo(val cp: JIRClasspath) {
    val persistence: JIRDatabasePersistence
    private val hierarchy: InMemoryHierarchyCache
    private val registeredLocationIds: Set<Long>

    init {
        val hierarchyFeature = cp.db.findInMemoryHierarchy()
            ?: error("In memory hierarchy required")

        persistence = cp.db.persistence
        hierarchy = InMemoryHierarchyAccess.accessCacheField(hierarchyFeature)
        registeredLocationIds = cp.registeredLocations.mapTo(hashSetOf()) { it.id }
    }

    inline fun forEachSubClassName(cls: String, body: (String) -> Unit) =
        forEachSubClassName(persistence.findSymbolId(cls), body)

    inline fun forEachSubClassName(rootClsId: Long, body: (String) -> Unit) =
        forEachSubClassId(rootClsId) { clsId ->
            body(persistence.findSymbolName(clsId))
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
