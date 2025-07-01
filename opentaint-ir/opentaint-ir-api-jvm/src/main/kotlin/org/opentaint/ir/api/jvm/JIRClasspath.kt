package org.opentaint.ir.api.jvm

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.opentaint.ir.api.common.CommonProject
import org.opentaint.ir.api.jvm.cfg.JIRGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRRawInst
import java.io.Closeable
import java.util.concurrent.Future

/**
 * Represents classpath, i.e. number of locations of byte code.
 *
 * Classpath **must be** closed when it's not needed anymore.
 * This will release references from database to possibly outdated libraries
 */
interface JIRClasspath : Closeable, CommonProject {

    /** locations of this classpath */
    val db: JIRDatabase

    /** locations of this classpath */
    val locations: List<JIRByteCodeLocation>
    val registeredLocations: List<RegisteredLocation>
    val features: List<JIRClasspathFeature>?

    /**
     *  @param name full name of the type
     *
     * @return class or interface or null if there is no such class found in locations
     */
    fun findClassOrNull(name: String): JIRClassOrInterface?

    /**
     * in case of jar-hell there could be few classes with same name inside classpath
     * @param name - class name
     *
     * @return list of classes with that name
     */
    fun findClasses(name: String): Set<JIRClassOrInterface>

    /**
     *  @param name full name of the type
     *
     * @return class or interface or null if there is no such class found in locations
     */
    fun findTypeOrNull(name: String): JIRType?

    fun typeOf(
        jIRClass: JIRClassOrInterface,
        nullability: Boolean? = null,
        annotations: List<JIRAnnotation> = listOf()
    ): JIRRefType

    fun arrayTypeOf(
        elementType: JIRType,
        nullability: Boolean? = null,
        annotations: List<JIRAnnotation> = listOf()
    ): JIRArrayType

    fun toJIRClass(source: ClassSource): JIRClassOrInterface

    suspend fun refreshed(closeOld: Boolean): JIRClasspath
    fun asyncRefreshed(closeOld: Boolean) = GlobalScope.future { refreshed(closeOld) }

    suspend fun <T : JIRClasspathTask> execute(task: T): T

    fun <T : JIRClasspathTask> executeAsync(task: T): Future<T> = GlobalScope.future { execute(task) }

    fun isInstalled(feature: JIRClasspathFeature): Boolean
}

interface JIRClasspathTask {

    fun before(classpath: JIRClasspath) {
    }

    fun after(classpath: JIRClasspath) {

    }

    fun shouldProcess(registeredLocation: RegisteredLocation): Boolean = true
    fun shouldProcess(classSource: ClassSource): Boolean = true

    fun process(source: ClassSource, classpath: JIRClasspath)

}

interface JIRClassProcessingTask : JIRClasspathTask {

    override fun process(source: ClassSource, classpath: JIRClasspath) {
        process(classpath.toJIRClass(source))
    }

    fun process(clazz: JIRClassOrInterface)
}

/**
 * Implementation should be idempotent that means that results should not be changed during time
 * Result of this
 */
@JvmDefaultWithoutCompatibility
interface JIRClasspathFeature {

    fun on(event: JIRFeatureEvent) {
    }

    fun event(result: Any): JIRFeatureEvent? = null

}

interface JIRFeatureEvent {
    val feature: JIRClasspathFeature
    val result: Any
}

@JvmDefaultWithoutCompatibility
interface JIRClasspathExtFeature : JIRClasspathFeature {

    interface JIRResolvedClassResult {
        val name: String
        val clazz: JIRClassOrInterface?
    }

    interface JIRResolvedClassesResult {
        val name: String
        val clazz: List<JIRClassOrInterface>
    }

    interface JIRResolvedTypeResult {
        val name: String
        val type: JIRType?
    }

    /**
     * semantic of method is like this:
     * - not empty optional for found class
     * - empty optional for class that we know is not exist in classpath
     * - null for case when we do not know
     */
    fun tryFindClass(classpath: JIRClasspath, name: String): JIRResolvedClassResult? = null

    /**
     * semantic is the same as for `tryFindClass` method
     */
    fun tryFindType(classpath: JIRClasspath, name: String, nullable: Boolean? = null): JIRResolvedTypeResult? = null

    fun findClasses(classpath: JIRClasspath, name: String): List<JIRClassOrInterface>? = null

}

@JvmDefaultWithoutCompatibility
interface JIRClassExtFeature : JIRClasspathFeature {

    fun fieldsOf(clazz: JIRClassOrInterface): List<JIRField>? = null
    fun fieldsOf(clazz: JIRClassOrInterface, originalFields: List<JIRField>): List<JIRField>? = fieldsOf(clazz)
    fun methodsOf(clazz: JIRClassOrInterface): List<JIRMethod>? = null
    fun methodsOf(clazz: JIRClassOrInterface, originalMethods: List<JIRMethod>): List<JIRMethod>? = methodsOf(clazz)

    fun extensionValuesOf(clazz: JIRClassOrInterface): Map<String, Any>? = null

}

interface JIRLookupExtFeature : JIRClasspathFeature {
    fun lookup(clazz: JIRClassOrInterface): JIRLookup<JIRField, JIRMethod>
    fun lookup(type: JIRClassType): JIRLookup<JIRTypedField, JIRTypedMethod>
}

interface JIRGenericsSubstitutionFeature : JIRClasspathFeature {

    fun substitute(clazz: JIRClassOrInterface, parameters: List<JvmType>, outer: JIRSubstitutor?): JIRSubstitutor
}

@JvmDefaultWithoutCompatibility
interface JIRInstExtFeature : JIRClasspathFeature {

    fun transformRawInstList(method: JIRMethod, list: JIRInstList<JIRRawInst>): JIRInstList<JIRRawInst> = list
    fun transformInstList(method: JIRMethod, list: JIRInstList<JIRInst>): JIRInstList<JIRInst> = list
}

@JvmDefaultWithoutCompatibility
interface JIRMethodExtFeature : JIRClasspathFeature {

    interface JIRFlowGraphResult {
        val method: JIRMethod
        val flowGraph: JIRGraph
    }

    interface JIRInstListResult {
        val method: JIRMethod
        val instList: JIRInstList<JIRInst>
    }

    interface JIRRawInstListResult {
        val method: JIRMethod
        val rawInstList: JIRInstList<JIRRawInst>
    }

    fun flowGraph(method: JIRMethod): JIRFlowGraphResult? = null
    fun instList(method: JIRMethod): JIRInstListResult? = null
    fun rawInstList(method: JIRMethod): JIRRawInstListResult? = null

}
