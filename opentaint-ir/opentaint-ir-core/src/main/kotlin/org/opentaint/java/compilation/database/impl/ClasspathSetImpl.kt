package org.opentaint.java.compilation.database.impl

import org.opentaint.java.compilation.database.api.*
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
        db.awaitBackgroundJobs()
        val subTypes = locations.flatMap { indexesRegistry.subClassesIndex(it)?.query(name).orEmpty() }
        return subTypes.mapNotNull { findClassOrNull(it) }
    }

    override suspend fun findSubTypesOf(classId: ClassId): List<ClassId> {
        return findSubTypesOf(classId.name)
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