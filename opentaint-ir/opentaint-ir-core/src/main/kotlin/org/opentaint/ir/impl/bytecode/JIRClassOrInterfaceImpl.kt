package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.ClassSource
import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRClassExtFeature
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.ext.findMethodOrNull
import org.opentaint.ir.impl.features.JIRFeaturesChain
import org.opentaint.ir.impl.fs.ClassSourceImpl
import org.opentaint.ir.impl.fs.LazyClassSourceImpl
import org.opentaint.ir.impl.fs.fullAsmNode
import org.opentaint.ir.impl.fs.info
import org.opentaint.ir.impl.types.ClassInfo
import org.opentaint.ir.impl.weakLazy
import org.objectweb.asm.tree.ClassNode
import java.util.*
import kotlin.LazyThreadSafetyMode.PUBLICATION

class JIRClassOrInterfaceImpl(
    override val classpath: JIRClasspath,
    private val classSource: ClassSource,
    private val featuresChain: JIRFeaturesChain,
) : JIRClassOrInterface {

    private val hasClassFeatures = featuresChain.features.any { it is JIRClassExtFeature }

    private val cachedInfo: ClassInfo? = when (classSource) {
        is LazyClassSourceImpl -> classSource.info // that means that we are loading bytecode. It can be removed let's cache info
        is ClassSourceImpl -> classSource.info // we can easily read link let's do it
        else -> null // maybe we do not need to do right now
    }

    private val extensionData by lazy(PUBLICATION) {
        HashMap<String, Any>().also { map ->
            featuresChain.newRequest().run<JIRClassExtFeature> {
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

    private val lazyAsmNode: ClassNode by weakLazy {
        classSource.fullAsmNode
    }

    override fun asmNode() = lazyAsmNode
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
            val default = info.fields.map { JIRFieldImpl(this, it) }
            if (hasClassFeatures) {
                val additional = TreeSet<JIRField> { o1, o2 -> o1.name.compareTo(o2.name) }
                featuresChain.newRequest().run<JIRClassExtFeature> {
                    it.fieldsOf(this, default)?.let {
                        additional.addAll(it)
                    }
                }
                if (additional.isNotEmpty()) {
                    val additionalMap = additional.associateBy { it.name }.toMutableMap()
                    // we need to preserve order of methods
                    return default.map {
                        val uniqueName = it.name
                        additionalMap[uniqueName]?.also {
                            additionalMap.remove(uniqueName)
                        } ?: it
                    } + additionalMap.values
                }
            }
            return default
        }

    private val JIRMethod.uniqueName: String get() = name + description

    override val declaredMethods: List<JIRMethod> by lazy(PUBLICATION) {
        val default = info.methods.map { toJIRMethod(it, featuresChain) }
        if (hasClassFeatures) {
            val additional = TreeSet<JIRMethod> { o1, o2 ->
                o1.uniqueName.compareTo(o2.uniqueName)
            }
            featuresChain.newRequest().run<JIRClassExtFeature> {
                it.methodsOf(this, default)?.let {
                    additional.addAll(it)
                }
            }
            if (additional.isNotEmpty()) {
                val additionalMap = additional.associateBy { it.uniqueName }.toMutableMap()
                // we need to preserve order of methods
                default.map {
                    val uniqueName = it.uniqueName
                    additionalMap[uniqueName]?.also {
                        additionalMap.remove(uniqueName)
                    } ?: it
                } + additionalMap.values
            } else {
                default
            }
        } else {
            default
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