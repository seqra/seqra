package org.opentaint.ir.impl

import org.opentaint.ir.api.JIRArrayType
import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.api.anyType
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.index.hierarchyExt
import org.opentaint.ir.impl.types.JIRArrayClassTypesImpl
import org.opentaint.ir.impl.types.JIRClassTypeImpl
import org.opentaint.ir.impl.vfs.ClasspathClassTree
import org.opentaint.ir.impl.vfs.GlobalClassesVfs
import java.io.Serializable

class JIRClasspathImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    private val featuresRegistry: FeaturesRegistry,
    override val db: JIRDBImpl,
    globalClassVFS: GlobalClassesVfs
) : JIRClasspath {

    override val locations: List<JIRByteCodeLocation> = locationsRegistrySnapshot.locations.map { it.jirLocation }

    private val classpathClassTree = ClasspathClassTree(globalClassVFS, locationsRegistrySnapshot)
    private val classIdService = ClassIdService(this)

    override suspend fun refreshed(closeOld: Boolean): JIRClasspath {
        return db.classpath(locationsRegistrySnapshot.locations).also {
            if (closeOld) {
                close()
            }
        }
    }

    override suspend fun findClassOrNull(name: String): JIRClassOrInterface? {
        return classIdService.toClassId(classpathClassTree.firstClassOrNull(name))
    }

    override suspend fun typeOf(jirClass: JIRClassOrInterface): JIRRefType {
        TODO("Not yet implemented")
    }

    override suspend fun arrayTypeOf(elementType: JIRType): JIRArrayType {
        TODO("Not yet implemented")
    }

    override suspend fun findTypeOrNull(name: String): JIRType? {
        if (name.endsWith("[]")) {
            val targetName = name.removeSuffix("[]")
            return findTypeOrNull(targetName)?.let {
                JIRArrayClassTypesImpl(it, true, anyType())
            } ?: targetName.throwClassNotFound()
        }
        val predefined = PredefinedPrimitives.of(name, this)
        if (predefined != null) {
            return predefined
        }
        val jirClass = findClassOrNull(name) ?: return null
        return JIRClassTypeImpl(jirClass, nullable = true)
    }

    override suspend fun findSubClasses(name: String, allHierarchy: Boolean): List<JIRClassOrInterface> {
        return hierarchyExt.findSubClasses(name, allHierarchy)
    }

    override suspend fun findSubClasses(jirClass: JIRClassOrInterface, allHierarchy: Boolean): List<JIRClassOrInterface> {
        return hierarchyExt.findSubClasses(jirClass, allHierarchy)
    }

    override suspend fun <RES : Serializable, REQ : Serializable> query(key: String, req: REQ): Sequence<RES> {
        db.awaitBackgroundJobs()
        return featuresRegistry.findIndex<RES, REQ>(key)?.query(req).orEmpty()
    }

    override fun close() {
        locationsRegistrySnapshot.close()
    }

}