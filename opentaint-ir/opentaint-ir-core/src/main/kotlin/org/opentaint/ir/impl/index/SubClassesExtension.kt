package org.opentaint.ir.impl.index

import org.opentaint.ir.api.*
import org.opentaint.ir.impl.fs.relevantLocations

class SubClassesExtension(private val db: CompilationDatabase, private val cp: ClasspathSet) {

    suspend fun findSubClasses(name: String, allHierarchy: Boolean): List<ClassId> {
        val classId = cp.findClassOrNull(name) ?: return emptyList()
        return findSubClasses(classId, allHierarchy)
    }

    suspend fun findSubClasses(classId: ClassId, allHierarchy: Boolean): List<ClassId> {
        db.awaitBackgroundJobs()
        val name = classId.name
        val relevantLocations = cp.locations.relevantLocations(classId.location)

        return relevantLocations.subClasses(name, allHierarchy).map { cp.findClassOrNull(it) }.filterNotNull()
    }

    suspend fun findOverrides(methodId: MethodId): List<MethodId> {
        db.awaitBackgroundJobs()
        return emptyList()
//        val name = classId.name
//        val relevantLocations = cp.locations.relevantLocations(classId.location)
//
//        return relevantLocations.subClasses(name, allHierarchy).map { cp.findClassOrNull(it) }.filterNotNull()
    }

    private suspend fun Collection<ByteCodeLocation>.subClasses(name: String, allHierarchy: Boolean): List<String> {
        val subTypes = flatMap {
            cp.query<String>(SubClassIndex.key, it, name)
        }
        if (allHierarchy) {
            return subTypes + subTypes.flatMap { subClasses(it, true) }
        }
        return subTypes
    }

}


val ClasspathSet.subClassesExt: SubClassesExtension
    get() {
        return SubClassesExtension(db, this)
    }