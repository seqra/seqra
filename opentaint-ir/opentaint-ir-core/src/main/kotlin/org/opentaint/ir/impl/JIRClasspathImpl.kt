package org.opentaint.ir.impl

import org.opentaint.ir.api.JIRArrayType
import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.api.anyType
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.api.toType
import org.opentaint.ir.impl.bytecode.JIRClassOrInterfaceImpl
import org.opentaint.ir.impl.index.hierarchyExt
import org.opentaint.ir.impl.types.JIRArrayClassTypesImpl
import org.opentaint.ir.impl.types.JIRClassTypeImpl
import org.opentaint.ir.impl.types.JIRTypeBindings
import org.opentaint.ir.impl.vfs.ClasspathClassTree
import org.opentaint.ir.impl.vfs.GlobalClassesVfs

class JIRClasspathImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    override val db: JIRDBImpl,
    globalClassVFS: GlobalClassesVfs
) : JIRClasspath {

    override val locations: List<JIRByteCodeLocation> = locationsRegistrySnapshot.locations.map { it.jirLocation }
    override val registeredLocations: List<RegisteredLocation> = locationsRegistrySnapshot.locations

    private val classpathClassTree = ClasspathClassTree(globalClassVFS, locationsRegistrySnapshot)

    override suspend fun refreshed(closeOld: Boolean): JIRClasspath {
        return db.new(this).also {
            if (closeOld) {
                close()
            }
        }
    }

    override suspend fun findClassOrNull(name: String): JIRClassOrInterface? {
        val inMemoryClass = toJcClass(classpathClassTree.firstClassOrNull(name))
        if (inMemoryClass != null) {
            return inMemoryClass
        }
        return db.persistence.findClassByName(this, locationsRegistrySnapshot.locations, name)?.let {
            JIRClassOrInterfaceImpl(this, it)
        }
    }

    override suspend fun typeOf(jirClass: JIRClassOrInterface): JIRRefType {
        return JIRClassTypeImpl(
            jirClass,
            jirClass.outerClass()?.toType(),
            JIRTypeBindings.ofClass(jirClass, null),
            nullable = true
        )
    }

    override suspend fun arrayTypeOf(elementType: JIRType): JIRArrayType {
        return JIRArrayClassTypesImpl(elementType, true, anyType())
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
        return typeOf(findClassOrNull(name) ?: return null)
    }

    override suspend fun findSubClasses(name: String, allHierarchy: Boolean): List<JIRClassOrInterface> {
        return hierarchyExt.findSubClasses(name, allHierarchy)
    }

    override suspend fun findSubClasses(jirClass: JIRClassOrInterface, allHierarchy: Boolean): List<JIRClassOrInterface> {
        return hierarchyExt.findSubClasses(jirClass, allHierarchy)
    }

    override fun close() {
        locationsRegistrySnapshot.close()
    }

}