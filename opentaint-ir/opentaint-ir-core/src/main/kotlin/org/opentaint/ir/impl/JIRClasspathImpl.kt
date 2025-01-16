package org.opentaint.ir.impl

import kotlinx.coroutines.*
import org.opentaint.ir.api.*
import org.opentaint.ir.api.ext.toType
import org.opentaint.ir.impl.bytecode.JIRClassOrInterfaceImpl
import org.opentaint.ir.impl.fs.ClassSourceImpl
import org.opentaint.ir.impl.types.JIRArrayTypeImpl
import org.opentaint.ir.impl.types.JIRClassTypeImpl
import org.opentaint.ir.impl.types.substition.JIRSubstitutor
import org.opentaint.ir.impl.vfs.ClasspathVfs
import org.opentaint.ir.impl.vfs.GlobalClassesVfs

class JIRClasspathImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    override val db: JIRDatabaseImpl,
    override val features: List<JIRClasspathFeature>?,
    globalClassVFS: GlobalClassesVfs
) : JIRClasspath {

    override val locations: List<JIRByteCodeLocation> = locationsRegistrySnapshot.locations.mapNotNull { it.jIRLocation }
    override val registeredLocations: List<RegisteredLocation> = locationsRegistrySnapshot.locations

    private val classpathVfs = ClasspathVfs(globalClassVFS, locationsRegistrySnapshot)

    private val classpathExtFeature = features?.filterIsInstance<JIRClasspathExtFeature>()

    override suspend fun refreshed(closeOld: Boolean): JIRClasspath {
        return db.new(this).also {
            if (closeOld) {
                close()
            }
        }
    }

    override fun findClassOrNull(name: String): JIRClassOrInterface? {
        val result = classpathExtFeature?.firstNotNullOfOrNull { it.tryFindClass(this, name) }
        if (result != null) {
            return result.orElse(null)
        }
        val source = classpathVfs.firstClassOrNull(name)
        val jIRClass = source?.let { toJIRClass(it.source) }
            ?: db.persistence.findClassSourceByName(this, locationsRegistrySnapshot.locations, name)?.let {
                toJIRClass(it)
            }
        if (jIRClass == null) {
            broadcast(JIRClassNotFound(name))
        }
        return jIRClass
    }

    override fun typeOf(jIRClass: JIRClassOrInterface): JIRRefType {
        return JIRClassTypeImpl(
            jIRClass,
            jIRClass.outerClass?.toType() as? JIRClassTypeImpl,
            JIRSubstitutor.empty,
            nullable = null
        ).also {
            broadcast(JIRTypeFoundEvent(it))
        }
    }

    override fun arrayTypeOf(elementType: JIRType): JIRArrayType {
        return JIRArrayTypeImpl(elementType, null)
    }

    override fun toJIRClass(source: ClassSource): JIRClassOrInterface {
        return JIRClassOrInterfaceImpl(this, source, features).also {
            broadcast(JIRClassFoundEvent(it))
        }
    }

    override fun findTypeOrNull(name: String): JIRType? {
        val result = classpathExtFeature?.firstNotNullOfOrNull { it.tryFindType(this, name) }
        if (result != null) {
            return result.orElse(null)
        }
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
                        .takeIf { it.isNotEmpty() } ?: it.jIRLocation?.classes?.map { entry ->
                        ClassSourceImpl(location = it, className = entry.key, byteCode = entry.value)
                    } ?: emptyList()

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