package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.jvm.*
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.api.jvm.ext.findMethodOrNull
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

    private val cachedInfo: ClassInfo? = when (classSource) {
        is LazyClassSourceImpl -> classSource.info // that means that we are loading bytecode. It can be removed let's cache info
        is ClassSourceImpl -> classSource.info // we can easily read link let's do it
        else -> null // maybe we do not need to do right now
    }

    private val interfacesArray: Array<JIRClassOrInterface> by lazy(PUBLICATION) {
        info.interfaces.map { classpath.findClass(it) }.toTypedArray()
    }

    private val innerClassesArray: Array<JIRClassOrInterface> by lazy(PUBLICATION) {
        info.innerClasses.filter { it != name }.map { classpath.findClass(it) }.toTypedArray()
    }

    override val lookup: JIRLookup<JIRField, JIRMethod> = ClassDelegatingLookup(
        this,
        featuresChain.classLookups,
        JIRClassLookupImpl(this)
    )

    private val extensionData by lazy(PUBLICATION) {
        HashMap<String, Any>().also { map ->
            featuresChain.run<JIRClassExtFeature> {
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

    override val interfaces: List<JIRClassOrInterface> get() = interfacesArray.asList()

    override val superClass: JIRClassOrInterface? by lazy(PUBLICATION) {
        info.superClass?.let { classpath.findClass(it) }
    }

    override val outerClass: JIRClassOrInterface? by lazy(PUBLICATION) {
        info.outerClass?.className?.let { classpath.findClass(it) }
    }

    override val innerClasses: List<JIRClassOrInterface> get() = innerClassesArray.asList()

    override val access: Int
        get() = info.access

    private val lazyAsmNode: ClassNode by weakLazy {
        classSource.fullAsmNode
    }

    override fun <T> withAsmNode(body: (ClassNode) -> T): T {
        val asmNode = lazyAsmNode
        return synchronized(asmNode) {
            body(asmNode)
        }
    }

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

    override val declaredFields: List<JIRField> by lazy(PUBLICATION) {
        val default = info.fields.map { JIRFieldImpl(this, it) }
        default.joinFeatureFields(this, featuresChain)
    }


    override val declaredMethods: List<JIRMethod> by lazy(PUBLICATION) {
        val default = info.methods.map { toJIRMethod(it, featuresChain) }
        default.joinFeatureMethods(this, featuresChain)
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

fun List<JIRField>.joinFeatureFields(
    jIRClassOrInterface: JIRClassOrInterface,
    featuresChain: JIRFeaturesChain
): List<JIRField> {
    val hasClassFeatures = featuresChain.features.any { it is JIRClassExtFeature }
    if (hasClassFeatures) {
        val additional = TreeSet<JIRField> { o1, o2 -> o1.name.compareTo(o2.name) }
        featuresChain.run<JIRClassExtFeature> {
            it.fieldsOf(jIRClassOrInterface, this)?.let {
                additional.addAll(it)
            }
        }
        if (additional.isNotEmpty()) {
            return appendOrOverride(additional) { it.name }
        }
    }
    return this
}

fun List<JIRMethod>.joinFeatureMethods(
    jIRClassOrInterface: JIRClassOrInterface,
    featuresChain: JIRFeaturesChain
): List<JIRMethod> {
    val hasClassFeatures = featuresChain.features.any { it is JIRClassExtFeature }
    if (hasClassFeatures) {
        val additional = TreeSet<JIRMethod> { o1, o2 ->
            o1.uniqueName.compareTo(o2.uniqueName)
        }
        featuresChain.run<JIRClassExtFeature> {
            it.methodsOf(jIRClassOrInterface, this)?.let {
                additional.addAll(it)
            }
        }
        if (additional.isNotEmpty()) {
            return appendOrOverride(additional) { it.uniqueName }
        }
    }
    return this
}

private val JIRMethod.uniqueName: String get() = name + description

private inline fun <T> List<T>.appendOrOverride(additional: Set<T>, getKey: (T) -> String): List<T> {
    if (additional.isNotEmpty()) {
        val additionalMap = additional.associateBy(getKey).toMutableMap()
        // we need to preserve order
        return map {
            val uniqueName = getKey(it)
            additionalMap[uniqueName]?.also {
                additionalMap.remove(uniqueName)
            } ?: it
        } + additionalMap.values
    }
    return this
}
