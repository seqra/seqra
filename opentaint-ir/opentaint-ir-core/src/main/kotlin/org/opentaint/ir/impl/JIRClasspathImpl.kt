package org.opentaint.ir.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import org.opentaint.ir.api.ClassSource
import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRArrayType
import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRClasspathExtFeature
import org.opentaint.ir.api.JIRClasspathFeature
import org.opentaint.ir.api.JIRClasspathTask
import org.opentaint.ir.api.JIRFeatureEvent
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.api.ext.toType
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.bytecode.JIRClassOrInterfaceImpl
import org.opentaint.ir.impl.features.JIRFeatureEventImpl
import org.opentaint.ir.impl.features.JIRFeaturesChain
import org.opentaint.ir.impl.fs.ClassSourceImpl
import org.opentaint.ir.impl.types.JIRArrayTypeImpl
import org.opentaint.ir.impl.types.JIRClassTypeImpl
import org.opentaint.ir.impl.types.substition.JIRSubstitutor
import org.opentaint.ir.impl.vfs.ClasspathVfs
import org.opentaint.ir.impl.vfs.GlobalClassesVfs
import java.util.*

class JIRClasspathImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    override val db: JIRDatabaseImpl,
    override val features: List<JIRClasspathFeature>,
    globalClassVFS: GlobalClassesVfs
) : JIRClasspath {

    override val locations: List<JIRByteCodeLocation> = locationsRegistrySnapshot.locations.mapNotNull { it.jIRLocation }
    override val registeredLocations: List<RegisteredLocation> = locationsRegistrySnapshot.locations

    private val classpathVfs = ClasspathVfs(globalClassVFS, locationsRegistrySnapshot)
    private val featuresChain = JIRFeaturesChain(features + JIRClasspathFeatureImpl())

    override suspend fun refreshed(closeOld: Boolean): JIRClasspath {
        return db.new(this).also {
            if (closeOld) {
                close()
            }
        }
    }

    override fun findClassOrNull(name: String): JIRClassOrInterface? {
        return featuresChain.newRequest(name).call<JIRClasspathExtFeature, Optional<JIRClassOrInterface>> {
            it.tryFindClass(this, name)
        }?.orElse(null)
    }

    override fun typeOf(jIRClass: JIRClassOrInterface, nullability: Boolean?, annotations: List<JIRAnnotation>): JIRRefType {
        return JIRClassTypeImpl(
            this,
            jIRClass.name,
            jIRClass.outerClass?.toType() as? JIRClassTypeImpl,
            JIRSubstitutor.empty,
            nullability,
            annotations
        )
    }

    override fun arrayTypeOf(elementType: JIRType, nullability: Boolean?, annotations: List<JIRAnnotation>): JIRArrayType {
        return JIRArrayTypeImpl(elementType, nullability, annotations)
    }

    override fun toJIRClass(source: ClassSource): JIRClassOrInterface {
        return JIRClassOrInterfaceImpl(this, source, featuresChain)
    }

    override fun findTypeOrNull(name: String): JIRType? {
        return featuresChain.newRequest(name).call<JIRClasspathExtFeature, Optional<JIRType>> {
            it.tryFindType(this, name)
        }?.orElse(null)
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

    private inner class JIRClasspathFeatureImpl: JIRClasspathExtFeature{

        override fun tryFindClass(classpath: JIRClasspath, name: String): Optional<JIRClassOrInterface> {
            val source = classpathVfs.firstClassOrNull(name)
            val jIRClass = source?.let { toJIRClass(it.source) }
                ?: db.persistence.findClassSourceByName(this@JIRClasspathImpl, locationsRegistrySnapshot.locations, name)?.let {
                    toJIRClass(it)
                }
            return Optional.ofNullable(jIRClass)
        }

        override fun tryFindType(classpath: JIRClasspath, name: String): Optional<JIRType>? {
            if (name.endsWith("[]")) {
                val targetName = name.removeSuffix("[]")
                return findTypeOrNull(targetName)?.let {
                    Optional.of(JIRArrayTypeImpl(it, true))
                }
            }
            val predefined = PredefinedPrimitives.of(name, this@JIRClasspathImpl)
            if (predefined != null) {
                return Optional.of(predefined)
            }
            val clazz = findClassOrNull(name) ?: return Optional.empty()
            return Optional.of(typeOf(clazz))
        }

        override fun event(result: Any, input: Array<Any>): JIRFeatureEvent {
            return JIRFeatureEventImpl(this, result, input)
        }

    }

}

