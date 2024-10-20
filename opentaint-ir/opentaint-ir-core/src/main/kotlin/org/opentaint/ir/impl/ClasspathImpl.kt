package org.opentaint.ir.impl

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.Classpath
import org.opentaint.ir.api.IndexRequest
import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.impl.index.hierarchyExt
import org.opentaint.ir.impl.tree.ClassTree
import org.opentaint.ir.impl.tree.ClasspathClassTree
import org.opentaint.ir.impl.types.ArrayClassIdImpl
import java.io.Serializable

class ClasspathImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    private val featuresRegistry: FeaturesRegistry,
    override val db: JIRDBImpl,
    classTree: ClassTree
) : Classpath {

    override val locations: List<ByteCodeLocation> = locationsRegistrySnapshot.locations

    private val classpathClassTree = ClasspathClassTree(classTree, locationsRegistrySnapshot)
    private val classIdService = ClassIdService(this, classpathClassTree)

    override suspend fun refreshed(closeOld: Boolean): Classpath {
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

    override suspend fun <T: Serializable, REQ: IndexRequest> query(key: String, term: REQ): List<T> {
        db.awaitBackgroundJobs()
        return featuresRegistry.findIndex<T, REQ>(key)?.query(term).orEmpty().toList()
    }

    override suspend fun <T: Serializable, REQ: IndexRequest> query(key: String, location: ByteCodeLocation, term: REQ): List<T> {
        db.awaitBackgroundJobs()
        return featuresRegistry.findIndex<T, REQ>(key)?.query(term).orEmpty().toList()
    }

    override fun close() {
        locationsRegistrySnapshot.close()
    }

}