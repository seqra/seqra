package org.opentaint.opentaint-ir.impl

import com.google.common.cache.CacheBuilder
import kotlinx.coroutines.*
import org.opentaint.opentaint-ir.api.*
import org.opentaint.opentaint-ir.api.ext.toType
import org.opentaint.opentaint-ir.impl.bytecode.JIRClassOrInterfaceImpl
import org.opentaint.opentaint-ir.impl.types.JIRArrayTypeImpl
import org.opentaint.opentaint-ir.impl.types.JIRClassTypeImpl
import org.opentaint.opentaint-ir.impl.types.substition.JIRSubstitutor
import org.opentaint.opentaint-ir.impl.vfs.ClasspathVfs
import org.opentaint.opentaint-ir.impl.vfs.GlobalClassesVfs
import java.time.Duration

class JIRClasspathImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    override val db: JIRDatabaseImpl,
    globalClassVFS: GlobalClassesVfs
) : JIRClasspath {

    private class ClassHolder(val jIRClass: JIRClassOrInterface?)
    private class TypeHolder(val type: JIRType?)

    private val classCache = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofSeconds(10))
        .maximumSize(1_000)
        .build<String, ClassHolder>()

    private val typeCache = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofSeconds(10))
        .maximumSize(1_000)
        .build<String, TypeHolder>()

    override val locations: List<JIRByteCodeLocation> = locationsRegistrySnapshot.locations.mapNotNull { it.jIRLocation }
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
            val jIRClass = source?.let { toJIRClass(it.source, false) }
                ?: db.persistence.findClassSourceByName(this, locationsRegistrySnapshot.locations, name)?.let {
                    toJIRClass(it, false)
                }
            ClassHolder(jIRClass)
        }.jIRClass
    }

    override fun typeOf(jIRClass: JIRClassOrInterface): JIRRefType {
        return JIRClassTypeImpl(
            jIRClass,
            jIRClass.outerClass?.toType() as? JIRClassTypeImpl,
            JIRSubstitutor.empty,
            nullable = null
        )
    }

    override fun arrayTypeOf(elementType: JIRType): JIRArrayType {
        return JIRArrayTypeImpl(elementType, null)
    }

    override fun toJIRClass(source: ClassSource, withCaching: Boolean): JIRClassOrInterface {
        if (withCaching) {
            return classCache.get(source.className) {
                ClassHolder(JIRClassOrInterfaceImpl(this, source))
            }.jIRClass!!
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

    override suspend fun <T : JIRClasspathTask> execute(task: T): T {
        val locations = registeredLocations.filter { task.shouldProcess(it) }
        task.before(this)
        withContext(Dispatchers.IO) {
            val parentScope = this
            locations.map {
                async {
                    val sources = db.persistence.findClassSources(it)
                    sources.forEach {
                        if (parentScope.isActive && task.shouldProcess(it)) {
                            task.process(it, this@JIRClasspathImpl)
                        }
                    }
                }
            }.joinAll()
        }
        task.after(this)
        return task
    }

    override fun close() {
        locationsRegistrySnapshot.close()
    }

}