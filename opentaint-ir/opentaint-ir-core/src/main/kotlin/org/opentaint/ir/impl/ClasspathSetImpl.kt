package org.opentaint.ir.impl

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.impl.index.subClassesExt
import org.opentaint.ir.impl.tree.ClassTree
import org.opentaint.ir.impl.tree.ClasspathClassTree

class ClasspathSetImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    private val indexesRegistry: IndexesRegistry,
    override val db: CompilationDatabaseImpl,
    classTree: ClassTree
) : ClasspathSet {

    override val locations: List<ByteCodeLocation> = locationsRegistrySnapshot.locations

    private val classpathClassTree = ClasspathClassTree(classTree, locationsRegistrySnapshot)
    private val classIdService = ClassIdService(this, classpathClassTree)

    override suspend fun refreshed(closeOld: Boolean): ClasspathSet {
        return db.classpathSet(locationsRegistrySnapshot.locations).also {
            if (closeOld) {
                close()
            }
        }
    }

    override suspend fun findClassOrNull(name: String): ClassId? {
        return classIdService.toClassId(classpathClassTree.firstClassOrNull(name))
    }

    override suspend fun findSubClasses(name: String, allHierarchy: Boolean): List<ClassId> {
        return subClassesExt.findSubClasses(name, allHierarchy)
    }

    override suspend fun findSubClasses(classId: ClassId, allHierarchy: Boolean): List<ClassId> {
        return subClassesExt.findSubClasses(classId, allHierarchy)
    }

    override suspend fun <T> query(key: String, term: String): List<T> {
        db.awaitBackgroundJobs()
        return locations.flatMap { indexesRegistry.findIndex<T>(key, it)?.query(term).orEmpty() }
    }

    override suspend fun <T> query(key: String, location: ByteCodeLocation, term: String): List<T> {
        db.awaitBackgroundJobs()
        return indexesRegistry.findIndex<T>(key, location)?.query(term).orEmpty().toList()
    }

    override fun close() {
        locationsRegistrySnapshot.close()
    }

}