package org.opentaint.ir.impl.index

import kotlinx.collections.immutable.toPersistentList
import org.opentaint.ir.api.*
import org.opentaint.ir.impl.fs.relevantLocations

class HierarchyExtension(private val db: CompilationDatabase, private val cp: ClasspathSet) {

    suspend fun findSubClasses(name: String, allHierarchy: Boolean): List<ClassId> {
        val classId = cp.findClassOrNull(name) ?: return emptyList()
        return findSubClasses(classId, allHierarchy)
    }

    suspend fun findSubClasses(classId: ClassId, allHierarchy: Boolean): List<ClassId> {
        if (classId is ArrayClassId) {
            return emptyList()
        }
        db.awaitBackgroundJobs()
        val name = classId.name
        val relevantLocations = cp.locations.relevantLocations(classId.location)

        return relevantLocations.subClasses(name, allHierarchy).map { cp.findClassOrNull(it) }.filterNotNull()
    }

    suspend fun findOverrides(methodId: MethodId): List<MethodId> {
        val desc = methodId.description()
        val name = methodId.name
        val subClasses = findSubClasses(methodId.classId, allHierarchy = true)
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


val ClasspathSet.hierarchyExt: HierarchyExtension
    get() {
        return HierarchyExtension(db, this)
    }