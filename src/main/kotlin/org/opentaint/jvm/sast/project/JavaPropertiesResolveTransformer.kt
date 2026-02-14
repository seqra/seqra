package org.opentaint.jvm.sast.project

import mu.KLogging
import org.opentaint.dataflow.jvm.util.JIRInstListBuilder
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRInstExtFeature
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.PredefinedPrimitives
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRBool
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIREqExpr
import org.opentaint.ir.api.jvm.cfg.JIRGotoInst
import org.opentaint.ir.api.jvm.cfg.JIRIfInst
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRInstRef
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRStaticCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRStringConstant
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.boolean
import org.opentaint.ir.impl.cfg.JIRInstLocationImpl
import org.opentaint.ir.impl.cfg.TypedStaticMethodRefImpl
import org.opentaint.ir.impl.features.classpaths.JIRUnknownType
import org.opentaint.ir.impl.features.classpaths.VirtualLocation
import org.opentaint.ir.impl.fs.BuildFolderLocation
import org.opentaint.ir.impl.fs.JarLocation
import org.opentaint.ir.impl.types.TypeNameImpl
import org.opentaint.jvm.util.stringType
import java.util.Properties
import java.util.jar.JarFile

class JavaPropertiesResolveTransformer(
    private val projectClasses: ProjectClasses,
) : JIRInstExtFeature {
    override fun transformInstList(method: JIRMethod, list: JIRInstList<JIRInst>): JIRInstList<JIRInst> {
        val methodCls = method.enclosingClass
        if (!projectClasses.isProjectClass(methodCls)) return list

        val getPropertyCalls = list.mapNotNull { inst ->
            if (inst !is JIRAssignInst) return@mapNotNull null
            inst.findGetPropertyCall()?.let { inst to it }
        }
        if (getPropertyCalls.isEmpty()) return list

        val propertyDescriptors = getPropertyCalls.mapNotNull { (inst, call) ->
            call.extractPropertyDescriptor()?.let { inst to it }
        }

        val concreteProperties = propertyDescriptors.filter { (_, d) -> d.concretePropertyName != null }
        if (concreteProperties.isEmpty()) return list

        val builder = JIRInstListBuilder(list.toList().toMutableList())
        for ((inst, property) in concreteProperties) {
            val value = findPropertyValue(property, inst, list)
                ?.let { JIRStringConstant(it, methodCls.classpath.stringType) }

           builder.addPropertyValueBlock(inst, value, property.propertyDefaultValue)
        }

        return builder
    }

    private fun JIRInstListBuilder.addPropertyValueBlock(
        propertyAccess: JIRAssignInst,
        value: JIRImmediate?,
        default: JIRImmediate?
    ) {
        if (value == null && default == null) return

        val originalLoc = propertyAccess.location
        val method = propertyAccess.location.method
        val cp = method.enclosingClass.classpath

        val instCopyIdx: Int
        addInst { newPropertyAccessIdx ->
            instCopyIdx = newPropertyAccessIdx
            val loc = with(originalLoc) {
                JIRInstLocationImpl(method, newPropertyAccessIdx, lineNumber)
            }
            JIRAssignInst(loc, propertyAccess.lhv, propertyAccess.rhv)
        }
        with(originalLoc) {
            mutableInstructions[index] = JIRGotoInst(this, JIRInstRef(instCopyIdx))
        }

        if (value != null) {
            addNonDetAssign(cp, method, value, propertyAccess.lhv)
        }

        if (default != null) {
            addNonDetAssign(cp, method, default, propertyAccess.lhv)
        }

        addInstWithLocation(method) { loc ->
            JIRGotoInst(loc, JIRInstRef(originalLoc.index + 1))
        }
    }

    private fun JIRInstListBuilder.addNonDetAssign(cp: JIRClasspath, method: JIRMethod, value: JIRImmediate, assignTo: JIRValue) {
        val condIdx = nextLocalVarIdx()
        val condVar = JIRLocalVar(condIdx, "cond", cp.boolean)
        addInstWithLocation(method) { loc ->
            JIRAssignInst(loc, condVar, opentaintNonDet(cp))
        }
        addInstWithLocation(method) { loc ->
            JIRIfInst(
                loc,
                condition = JIREqExpr(cp.boolean, condVar, JIRBool(true, cp.boolean)),
                trueBranch = JIRInstRef(loc.index + 1),
                falseBranch = JIRInstRef(loc.index + 2)
            )
        }
        addInstWithLocation(method) { loc ->
            JIRAssignInst(loc, assignTo, value)
        }
    }

    private fun findPropertyValue(
        descriptor: PropertyDescriptor,
        propertyAccessInst: JIRInst,
        instructions: JIRInstList<JIRInst>
    ): String? {
        val initializer = findPropertyInitializerInst(descriptor.propertiesObj, propertyAccessInst, instructions)
            ?: return null

        val propertiesSource = findPropertiesSource(initializer, instructions)
            ?: return null

        return resolvePropertyValue(propertyAccessInst.location.method.enclosingClass, propertiesSource, descriptor)
    }

    private data class PropertiesFromResource(
        val inst: JIRInst,
        val path: JIRImmediate
    ) {
        val concretePath: String?
            get() = (path as? JIRStringConstant)?.value
    }

    private data class PropertyLoadInitializer(
        val inst: JIRInst,
        val loadFrom: JIRImmediate
    )

    private fun resolvePropertyValue(
        locationCls: JIRClassOrInterface,
        source: PropertiesFromResource,
        property: PropertyDescriptor
    ): String? {
        val resourcePath = source.concretePath ?: return null

        val location = locationCls.declaration.location.jIRLocation
        val propertiesFileContent = when (location) {
            is BuildFolderLocation -> location.resolvePropertiesFile(resourcePath) ?: return null
            is JarLocation -> location.resolvePropertiesFile(resourcePath) ?: return null
            else -> return null
        }

        val properties = loadProperties(propertiesFileContent) ?: return null
        val propertyName = property.concretePropertyName ?: return null
        return properties.getProperty(propertyName)
    }

    private fun loadProperties(properties: String): Properties? = runCatching {
        Properties().also { it.load(properties.byteInputStream()) }
    }.onFailure {
        logger.error("Failed to parse properties file", it)
    }.getOrNull()

    private fun BuildFolderLocation.resolvePropertiesFile(path: String): String? = runCatching {
        val file = jarOrFolder.resolve(path.removePrefix("/"))
        if (!file.exists()) return null
        return file.readText()
    }.onFailure {
        logger.error("Failed to resolve DIR properties file: $path", it)
    }.getOrNull()

    private fun JarLocation.resolvePropertiesFile(path: String): String? = runCatching {
        return JarFile(jarOrFolder).use { jarFile ->
            val entry = jarFile.getJarEntry(path) ?: return null
            jarFile.getInputStream(entry).use { content ->
                content.bufferedReader().readText()
            }
        }
    }.onFailure {
        logger.error("Failed to resolve JAR properties file: $path", it)
    }.getOrNull()

    // todo: use cfg, propagate assignments
    private fun findPropertyInitializerInst(
        propertyObj: JIRImmediate,
        propertyAccessInst: JIRInst,
        instructions: JIRInstList<JIRInst>
    ): PropertyLoadInitializer? = traverseCalls(propertyAccessInst, instructions) { inst, call ->
        if (!call.method.method.isPropertyLoad()) return@traverseCalls
        if (call !is JIRInstanceCallExpr) return@traverseCalls
        if (call.instance != propertyObj) return@traverseCalls

        val loadFrom = call.args.getOrNull(0) as? JIRImmediate ?: return@traverseCalls

        return PropertyLoadInitializer(inst, loadFrom)
    }

    private fun findPropertiesSource(
        initializer: PropertyLoadInitializer,
        instructions: JIRInstList<JIRInst>
    ): PropertiesFromResource? = traverseCalls(initializer.inst, instructions) { inst, call ->
        if (!call.method.method.isGetResource()) return@traverseCalls
        if (inst !is JIRAssignInst) return@traverseCalls
        if (inst.lhv != initializer.loadFrom) return@traverseCalls

        val resourcePath = call.args.getOrNull(0) as? JIRImmediate ?: return@traverseCalls
        return PropertiesFromResource(inst, resourcePath)
    }

    private inline fun traverseCalls(
        start: JIRInst,
        instructions: JIRInstList<JIRInst>,
        body: (JIRInst, JIRCallExpr) -> Unit
    ): Nothing? {
        var instIdx = start.location.index
        while (instIdx >= 0) {
            val inst = instructions[instIdx--]
            val call = inst.findCallExpr()
                ?: continue
            body(inst, call)
        }
        return null
    }

    private data class PropertyDescriptor(
        val propertiesObj: JIRImmediate,
        val propertyName: JIRImmediate,
        val propertyDefaultValue: JIRImmediate?
    ) {
        val concretePropertyName: String?
            get() = (propertyName as? JIRStringConstant)?.value
    }

    private fun JIRCallExpr.extractPropertyDescriptor(): PropertyDescriptor? {
        return PropertyDescriptor(
            propertiesObj = (this as? JIRInstanceCallExpr)?.instance as? JIRImmediate ?: return null,
            propertyName = args.getOrNull(0) as? JIRImmediate ?: return null,
            propertyDefaultValue = args.getOrNull(1) as? JIRImmediate
        )
    }

    private fun JIRInst.findGetPropertyCall(): JIRCallExpr? {
        val call = findCallExpr() ?: return null
        if (!call.method.method.isGetProperty()) return null
        return call
    }

    private fun JIRMethod.isGetProperty(): Boolean =
        name == GET_PROPERTY && enclosingClass.name == JAVA_PROPERTIES

    private fun JIRMethod.isPropertyLoad(): Boolean =
        name == LOAD && enclosingClass.name == JAVA_PROPERTIES

    private fun JIRMethod.isGetResource(): Boolean =
        name == GET_RESOURCE && enclosingClass.name == CLASS_LOADER

    private fun JIRInst.findCallExpr(): JIRCallExpr? = when (this) {
        is JIRAssignInst -> rhv as? JIRCallExpr
        is JIRCallInst -> callExpr
        else -> null
    }

    private fun opentaintNonDet(cp: JIRClasspath): JIRCallExpr {
        val type = TypeNameImpl.fromTypeName(PredefinedPrimitives.Boolean)
        val methodRef = TypedStaticMethodRefImpl(opentaintNonDetCls(cp), "next", argTypes = emptyList(), type)
        return JIRStaticCallExpr(methodRef, emptyList())
    }

    private fun opentaintNonDetCls(cp: JIRClasspath): JIRClassType =
        JIRUnknownType(cp, "opentaint.NonDetCls", virtualLoc, nullable = false)

    companion object {
        private val logger = object : KLogging() {}.logger
        private const val JAVA_PROPERTIES = "java.util.Properties"
        private const val GET_PROPERTY = "getProperty"
        private const val LOAD = "load"
        private const val CLASS_LOADER = "java.lang.ClassLoader"
        private const val GET_RESOURCE = "getResourceAsStream"

        private val virtualLoc = VirtualLocation()
    }
}
