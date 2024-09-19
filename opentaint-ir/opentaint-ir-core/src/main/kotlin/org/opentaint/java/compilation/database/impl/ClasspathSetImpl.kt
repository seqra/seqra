package org.opentaint.java.compilation.database.impl

import org.opentaint.java.compilation.database.api.*
import org.opentaint.java.compilation.database.impl.fs.relevantLocations
import org.opentaint.java.compilation.database.impl.tree.ClassTree
import org.opentaint.java.compilation.database.impl.tree.ClasspathClassTree

class ClasspathSetImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    private val indexesRegistry: IndexesRegistry,
    private val db: CompilationDatabaseImpl,
    classTree: ClassTree
) : ClasspathSet {

    override val locations: List<ByteCodeLocation> = locationsRegistrySnapshot.locations

    private val classpathClassTree = ClasspathClassTree(classTree, locationsRegistrySnapshot)
    private val classIdService = ClassIdService(classpathClassTree)

    override suspend fun findClassOrNull(name: String): ClassId? {
        return classIdService.toClassId(classpathClassTree.firstClassOrNull(name))
    }

    override suspend fun findSubTypesOf(name: String): List<ClassId> {
        val classId = findClassOrNull(name) ?: return emptyList()
        return findSubTypesOf(classId)
    }

    override suspend fun findSubTypesOf(classId: ClassId): List<ClassId> {
        db.awaitBackgroundJobs()
        val name = classId.name
        val subTypes = locations.relevantLocations(classId.location).flatMap {
            indexesRegistry.subClassesIndex(it)?.query(name).orEmpty()
        }
        return subTypes.mapNotNull { findClassOrNull(it) }
    }

    override suspend fun findUsages(fieldId: FieldId, mode: FindUsageMode): List<MethodId> {
        return emptyList()
    }

    override suspend fun <T> query(key: String, term: String): List<T> {
        db.awaitBackgroundJobs()
        return locations.flatMap { indexesRegistry.findIndex<T>(key, it)?.query(term).orEmpty() }
    }

    override fun close() {
        locationsRegistrySnapshot.close()
    }

}