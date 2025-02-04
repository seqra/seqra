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
import org.opentaint.ir.api.JIRClasspathExtFeature.JIRResolvedClassResult
import org.opentaint.ir.api.JIRClasspathExtFeature.JIRResolvedTypeResult
import org.opentaint.ir.api.JIRClasspathFeature
import org.opentaint.ir.api.JIRClasspathTask
import org.opentaint.ir.api.JIRFeatureEvent
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.api.ext.toType
import org.opentaint.ir.impl.bytecode.JIRClassOrInterfaceImpl
import org.opentaint.ir.impl.features.JIRFeatureEventImpl
import org.opentaint.ir.impl.features.JIRFeaturesChain
import org.opentaint.ir.impl.features.classpaths.AbstractJIRResolvedResult.JIRResolvedClassResultImpl
import org.opentaint.ir.impl.features.classpaths.AbstractJIRResolvedResult.JIRResolvedTypeResultImpl
import org.opentaint.ir.impl.fs.ClassSourceImpl
import org.opentaint.ir.impl.types.JIRArrayTypeImpl
import org.opentaint.ir.impl.types.JIRClassTypeImpl
import org.opentaint.ir.impl.types.substition.JIRSubstitutor
import org.opentaint.ir.impl.vfs.ClasspathVfs
import org.opentaint.ir.impl.vfs.GlobalClassesVfs

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
        return featuresChain.call<JIRClasspathExtFeature, JIRResolvedClassResult> {
            it.tryFindClass(this, name)
        }?.clazz
    }

    override fun typeOf(
        jIRClass: JIRClassOrInterface,
        nullability: Boolean?,
        annotations: List<JIRAnnotation>
    ): JIRRefType {
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
        return featuresChain.call<JIRClasspathExtFeature, JIRResolvedTypeResult> {
            it.tryFindType(this, name)
        }?.type
    }

    override suspend fun <T : JIRClasspathTask> execute(task: T): T {
        val locations = registeredLocations.filter { task.shouldProcess(it) }
        task.before(this)
        withContext(Dispatchers.IO) {
            val parentScope = this
            locations.map {
                async {
                    val sources = db.persistence.findClassSources(db, it)
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

    override fun findClasses(name: String): Set<JIRClassOrInterface> {
        return featuresChain.features.filterIsInstance<JIRClasspathExtFeature>().flatMap { feature ->
            feature.findClasses(this, name).orEmpty()
        }.toSet()
    }

    override fun close() {
        locationsRegistrySnapshot.close()
    }

    private inner class JIRClasspathFeatureImpl : JIRClasspathExtFeature {

        override fun tryFindClass(classpath: JIRClasspath, name: String): JIRResolvedClassResult {
            val source = classpathVfs.firstClassOrNull(name)
            val jIRClass = source?.let { toJIRClass(it.source) }
                ?: db.persistence.findClassSourceByName(classpath, name)?.let {
                    toJIRClass(it)
                }
            return JIRResolvedClassResultImpl(name, jIRClass)
        }

        override fun tryFindType(classpath: JIRClasspath, name: String): JIRResolvedTypeResult {
            if (name.endsWith("[]")) {
                val targetName = name.removeSuffix("[]")
                return JIRResolvedTypeResultImpl(name,
                    findTypeOrNull(targetName)?.let { JIRArrayTypeImpl(it, true) }
                )
            }
            val predefined = PredefinedPrimitives.of(name, classpath)
            if (predefined != null) {
                return JIRResolvedTypeResultImpl(name, predefined)
            }
            val clazz = findClassOrNull(name) ?: return JIRResolvedTypeResultImpl(name, null)
            return JIRResolvedTypeResultImpl(name, typeOf(clazz))
        }

        override fun findClasses(classpath: JIRClasspath, name: String): List<JIRClassOrInterface> {
            val vfsClasses = classpathVfs.findClassNodes(name).map { toJIRClass(it.source) }
            val persistedClasses = db.persistence.findClassSources(classpath, name).map { toJIRClass(it) }
            return buildSet {
                addAll(vfsClasses)
                addAll(persistedClasses)
            }.toList()
        }

        override fun event(result: Any): JIRFeatureEvent {
            return JIRFeatureEventImpl(this, result)
        }

    }

}

