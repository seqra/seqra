package org.opentaint.ir.impl

import kotlinx.coroutines.*
import org.opentaint.ir.api.jvm.*
import org.opentaint.ir.api.jvm.JIRClasspathExtFeature.JIRResolvedClassResult
import org.opentaint.ir.api.jvm.JIRClasspathExtFeature.JIRResolvedTypeResult
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.impl.bytecode.JIRClassOrInterfaceImpl
import org.opentaint.ir.impl.features.JIRFeatureEventImpl
import org.opentaint.ir.impl.features.JIRFeaturesChain
import org.opentaint.ir.impl.features.classpaths.AbstractJIRResolvedResult.JIRResolvedClassResultImpl
import org.opentaint.ir.impl.features.classpaths.AbstractJIRResolvedResult.JIRResolvedTypeResultImpl
import org.opentaint.ir.impl.features.classpaths.JIRUnknownClass
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.opentaint.ir.impl.features.classpaths.isResolveAllToUnknown
import org.opentaint.ir.impl.fs.ClassSourceImpl
import org.opentaint.ir.impl.types.JIRArrayTypeImpl
import org.opentaint.ir.impl.types.JIRClassTypeImpl
import org.opentaint.ir.impl.types.substition.JIRSubstitutorImpl
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
    private val featuresChain = run{
        val strictFeatures = features.filter { it !is UnknownClasses }
        val hasUnknownClasses = strictFeatures.size != features.size
        JIRFeaturesChain(strictFeatures + listOfNotNull(JIRClasspathFeatureImpl(), UnknownClasses.takeIf { hasUnknownClasses }) )
    }

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

    override fun classTypeOf(
        jIRClass: JIRClassOrInterface,
        nullability: Boolean?,
        annotations: List<JIRAnnotation>
    ): JIRClassType {
        return JIRClassTypeImpl(
            this,
            jIRClass.name,
            jIRClass.outerClass?.toType() as? JIRClassTypeImpl,
            JIRSubstitutorImpl.empty,
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

    override fun isInstalled(feature: JIRClasspathFeature): Boolean {
        return featuresChain.features.contains(feature)
    }

    override fun close() {
        locationsRegistrySnapshot.close()
    }

    private inner class JIRClasspathFeatureImpl : JIRClasspathExtFeature {

        override fun tryFindClass(classpath: JIRClasspath, name: String): JIRResolvedClassResult? {
            val source = classpathVfs.firstClassOrNull(name)
            val jIRClass = source?.let { toJIRClass(it.source) }
                ?: db.persistence.findClassSourceByName(classpath, name)?.let {
                    toJIRClass(it)
                }
            if (jIRClass == null && isResolveAllToUnknown) {
                return null
            }
            return JIRResolvedClassResultImpl(name, jIRClass)
        }

        override fun tryFindType(classpath: JIRClasspath, name: String): JIRResolvedTypeResult? {
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
            return when (val clazz = findClassOrNull(name)) {
                null -> JIRResolvedTypeResultImpl(name, null)
                is JIRUnknownClass -> null // delegating to UnknownClass feature
                else -> JIRResolvedTypeResultImpl(name, classTypeOf(clazz))
            }
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
