package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.ClassSource
import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRClassExtFeature
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRClasspathFeature
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRMethodExtFeature
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.ext.findMethodOrNull
import org.opentaint.ir.impl.fs.ClassSourceImpl
import org.opentaint.ir.impl.fs.LazyClassSourceImpl
import org.opentaint.ir.impl.fs.fullAsmNodeWithFrames
import org.opentaint.ir.impl.fs.info
import org.opentaint.ir.impl.types.ClassInfo
import kotlin.LazyThreadSafetyMode.PUBLICATION

class JIRClassOrInterfaceImpl(
    override val classpath: JIRClasspath,
    private val classSource: ClassSource,
    features: List<JIRClasspathFeature>,
) : JIRClassOrInterface {

    private val cache = features.filterIsInstance<JIRMethodExtFeature>().first()

    private val cachedInfo: ClassInfo? = when (classSource) {
        is LazyClassSourceImpl -> classSource.info // that means that we are loading bytecode. It can be removed let's cache info
        is ClassSourceImpl -> classSource.info // we can easily read link let's do it
        else -> null // maybe we do not need to do right now
    }

    private val classFeatures = features.filterIsInstance<JIRClassExtFeature>()

    private val extensionData by lazy(PUBLICATION) {
        HashMap<String, Any>().also { map ->
            classFeatures.forEach {
                map.putAll(it.extensionValuesOf(this).orEmpty())
            }
        }
    }

    val info by lazy { cachedInfo ?: classSource.info }

    override val declaration = JIRDeclarationImpl.of(location = classSource.location, this)

    override val name: String get() = classSource.className
    override val simpleName: String get() = classSource.className.substringAfterLast(".")

    override val signature: String?
        get() = info.signature

    override val annotations: List<JIRAnnotation>
        get() = info.annotations.map { JIRAnnotationImpl(it, classpath) }

    override val interfaces: List<JIRClassOrInterface>
        get() {
            return info.interfaces.map {
                classpath.findClass(it)
            }
        }

    override val superClass: JIRClassOrInterface?
        get() {
            return info.superClass?.let {
                classpath.findClass(it)
            }
        }

    override val outerClass: JIRClassOrInterface?
        get() {
            return info.outerClass?.className?.let {
                classpath.findClass(it)
            }
        }

    override val innerClasses: List<JIRClassOrInterface>
        get() {
            return info.innerClasses.map {
                classpath.findClass(it)
            }
        }

    override val access: Int
        get() = info.access

    override fun asmNode() = classSource.fullAsmNodeWithFrames(classpath)
    override fun bytecode(): ByteArray = classSource.byteCode

    override fun <T> extensionValue(key: String): T? {
        return extensionData[key] as? T
    }

    override val isAnonymous: Boolean
        get() {
            val outerClass = info.outerClass
            return outerClass != null && outerClass.name == null
        }

    override val outerMethod: JIRMethod?
        get() {
            val info = info
            if (info.outerMethod != null && info.outerMethodDesc != null) {
                return outerClass?.findMethodOrNull(info.outerMethod, info.outerMethodDesc)
            }
            return null
        }

    override val declaredFields: List<JIRField>
        get() {
            val result: List<JIRField> = info.fields.map { JIRFieldImpl(this, it) }
            return when {
                classFeatures.isNotEmpty() -> {
                    val modifiedFields = result.toMutableList()
                    classFeatures.forEach {
                        it.fieldsOf(this)?.let {
                            modifiedFields.addAll(it)
                        }
                    }
                    modifiedFields
                }

                else -> result
            }
        }

    override val declaredMethods: List<JIRMethod> by lazy(PUBLICATION) {
        val result: List<JIRMethod> = info.methods.map { toJIRMethod(it, classSource, cache) }
        when {
            classFeatures.isNotEmpty() -> {
                val modifiedMethods = result.toMutableList()
                classFeatures.forEach {
                    it.methodsOf(this)?.let {
                        modifiedMethods.addAll(it)
                    }
                }
                modifiedMethods
            }

            else -> result
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JIRClassOrInterfaceImpl) {
            return false
        }
        return other.name == name && other.declaration == declaration
    }

    override fun hashCode(): Int {
        return 31 * declaration.hashCode() + name.hashCode()
    }

    override fun toString(): String {
        return "(id:${declaration.location.id})$name"
    }
}