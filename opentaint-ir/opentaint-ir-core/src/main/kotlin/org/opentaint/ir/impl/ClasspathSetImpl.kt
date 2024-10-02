package org.opentaint.ir.impl

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.impl.index.hierarchyExt
import org.opentaint.ir.impl.tree.ClassTree
import org.opentaint.ir.impl.tree.ClasspathClassTree
import org.opentaint.ir.impl.types.ArrayClassIdImpl
import org.opentaint.ir.impl.types.PredefinedPrimitives
import java.io.Serializable

class ClasspathSetImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    private val featuresRegistry: FeaturesRegistry,
    override val db: JIRDBImpl,
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
        if (name.endsWith("[]")) {
            val targetName = name.removeSuffix("[]")
            return findClassOrNull(targetName)?.let {
                ArrayClassIdImpl(it)
            }
        }
        val predefined = PredefinedPrimitives.of(name, this)
        if (predefined != null) {
            return predefined
        }
        return classIdService.toClassId(classpathClassTree.firstClassOrNull(name))
    }

    override suspend fun findSubClasses(name: String, allHierarchy: Boolean): List<ClassId> {
        return hierarchyExt.findSubClasses(name, allHierarchy)
    }

    override suspend fun findSubClasses(classId: ClassId, allHierarchy: Boolean): List<ClassId> {
        return hierarchyExt.findSubClasses(classId, allHierarchy)
    }

    override suspend fun <T: Serializable> query(key: String, term: String): List<T> {
        db.awaitBackgroundJobs()
        return locations.flatMap { featuresRegistry.findIndex<T>(key, it)?.query(term).orEmpty() }
    }

    override suspend fun <T: Serializable> query(key: String, location: ByteCodeLocation, term: String): List<T> {
        db.awaitBackgroundJobs()
        return featuresRegistry.findIndex<T>(key, location)?.query(term).orEmpty().toList()
    }

    override fun close() {
        locationsRegistrySnapshot.close()
    }

}