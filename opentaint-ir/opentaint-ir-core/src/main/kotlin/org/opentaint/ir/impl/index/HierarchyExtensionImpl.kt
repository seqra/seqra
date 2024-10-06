package org.opentaint.ir.impl.index

import kotlinx.collections.immutable.toPersistentList
import org.opentaint.ir.api.*
import org.opentaint.ir.api.ext.HierarchyExtension
import org.opentaint.ir.impl.fs.relevantLocations

class HierarchyExtensionImpl(private val db: JIRDB, private val cp: ClasspathSet) : HierarchyExtension {

    override suspend fun findSubClasses(name: String, allHierarchy: Boolean): List<ClassId> {
        val classId = cp.findClassOrNull(name) ?: return emptyList()
        return findSubClasses(classId, allHierarchy)
    }

    override suspend fun findSubClasses(classId: ClassId, allHierarchy: Boolean): List<ClassId> {
        if (classId is ArrayClassId) {
            return emptyList()
        }
        db.awaitBackgroundJobs()
        val name = classId.name
        val relevantLocations = cp.locations.relevantLocations(classId.location)

        return relevantLocations.subClasses(name, allHierarchy).mapNotNull { cp.findClassOrNull(it) }
    }

    override suspend fun findOverrides(methodId: MethodId): List<MethodId> {
        val desc = methodId.description()
        val name = methodId.name
        val subClasses = cp.findSubClasses(methodId.classId, allHierarchy = true)
        return subClasses.mapNotNull {
            it.findMethodOrNull(name, desc)
        }
    }

    private suspend fun Collection<ByteCodeLocation>.subClasses(name: String, allHierarchy: Boolean): List<String> {
        val subTypes = flatMap {
            cp.query<String>(Hierarchy.key, it, name)
        }.toHashSet()
        if (allHierarchy) {
            return (subTypes + subTypes.flatMap { subClasses(it, true) }).toPersistentList()
        }
        return subTypes.toPersistentList()
    }

}


val ClasspathSet.hierarchyExt: HierarchyExtensionImpl
    get() {
        return HierarchyExtensionImpl(db, this)
    }