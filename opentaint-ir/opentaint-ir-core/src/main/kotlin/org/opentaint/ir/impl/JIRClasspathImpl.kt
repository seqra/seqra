
package org.opentaint.ir.impl

import com.google.common.cache.CacheBuilder
import org.opentaint.ir.api.ClassSource
import org.opentaint.ir.api.JIRArrayType
import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.api.toType
import org.opentaint.ir.impl.bytecode.JIRClassOrInterfaceImpl
import org.opentaint.ir.impl.types.JIRArrayTypeImpl
import org.opentaint.ir.impl.types.JIRClassTypeImpl
import org.opentaint.ir.impl.types.substition.JIRSubstitutor
import org.opentaint.ir.impl.vfs.ClasspathVfs
import org.opentaint.ir.impl.vfs.GlobalClassesVfs
import java.time.Duration

class JIRClasspathImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    override val db: JIRDBImpl,
    globalClassVFS: GlobalClassesVfs
) : JIRClasspath {

    private class ClassHolder(val jirClass: JIRClassOrInterface?)
    private class TypeHolder(val type: JIRType?)

    private val classCache = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofSeconds(10))
        .maximumSize(1_000)
        .build<String, ClassHolder>()

    private val typeCache = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofSeconds(10))
        .maximumSize(1_000)
        .build<String, TypeHolder>()

    override val locations: List<JIRByteCodeLocation> = locationsRegistrySnapshot.locations.mapNotNull { it.jirLocation }
    override val registeredLocations: List<RegisteredLocation> = locationsRegistrySnapshot.locations

    private val classpathVfs = ClasspathVfs(globalClassVFS, locationsRegistrySnapshot)

    override suspend fun refreshed(closeOld: Boolean): JIRClasspath {
        return db.new(this).also {
            if (closeOld) {
                close()
            }
        }
    }

    override fun findClassOrNull(name: String): JIRClassOrInterface? {
        return classCache.get(name) {
            val source = classpathVfs.firstClassOrNull(name)
            val jirClass = source?.let { toJcClass(it.source, false) }
                ?: db.persistence.findClassSourceByName(this, locationsRegistrySnapshot.locations, name)?.let {
                    toJcClass(it, false)
                }
            ClassHolder(jirClass)
        }.jirClass
    }

    override fun typeOf(jirClass: JIRClassOrInterface): JIRRefType {
        return JIRClassTypeImpl(
            jirClass,
            jirClass.outerClass?.toType() as? JIRClassTypeImpl,
            JIRSubstitutor.empty,
            nullable = true
        )
    }

    override fun arrayTypeOf(elementType: JIRType): JIRArrayType {
        return JIRArrayTypeImpl(elementType, true)
    }

    override fun toJcClass(source: ClassSource, withCaching: Boolean): JIRClassOrInterface {
        if (withCaching) {
            return classCache.get(source.className) {
                ClassHolder(JIRClassOrInterfaceImpl(this, source))
            }.jirClass!!
        }
        return JIRClassOrInterfaceImpl(this, source)
    }

    override fun findTypeOrNull(name: String): JIRType? {
        return typeCache.get(name) {
            TypeHolder(doFindTypeOrNull(name))
        }.type
    }

    private fun doFindTypeOrNull(name: String): JIRType? {
        if (name.endsWith("[]")) {
            val targetName = name.removeSuffix("[]")
            return findTypeOrNull(targetName)?.let {
                JIRArrayTypeImpl(it, true)
            } ?: targetName.throwClassNotFound()
        }
        val predefined = PredefinedPrimitives.of(name, this)
        if (predefined != null) {
            return predefined
        }
        return typeOf(findClassOrNull(name) ?: return null)
    }

    override fun close() {
        locationsRegistrySnapshot.close()
    }

}