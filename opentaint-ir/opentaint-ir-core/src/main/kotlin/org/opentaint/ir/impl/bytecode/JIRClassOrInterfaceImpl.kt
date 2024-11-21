package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.ClassSource
import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.findMethodOrNull
import org.opentaint.ir.impl.fs.ClassSourceImpl
import org.opentaint.ir.impl.fs.LazyClassSourceImpl
import org.opentaint.ir.impl.fs.fullAsmNode
import org.opentaint.ir.impl.fs.info
import org.opentaint.ir.impl.types.ClassInfo

class JIRClassOrInterfaceImpl(
    override val classpath: JIRClasspath,
    private val classSource: ClassSource
) : JIRClassOrInterface {

    private val cachedInfo: ClassInfo? = when {
        classSource is LazyClassSourceImpl -> classSource.info // that means that we are loading bytecode. It can be removed let's cache info
        classSource is ClassSourceImpl -> classSource.info // we can easily read link let's do it
        else -> null // maybe we do not need to do right now
    }

    val info by lazy { cachedInfo ?: classSource.info }

    override val declaration = JIRDeclarationImpl.of(location = classSource.location, this)

    override val name: String get() = classSource.className
    override val simpleName: String get() = classSource.className.substringAfterLast(".")

    override val signature: String?
        get() = info.signature

    override val annotations: List<JIRAnnotation>
        get() = info.annotations.map { JIRAnnotationImpl(it, classpath) }

    override val interfaces by lazy(LazyThreadSafetyMode.NONE) {
        info.interfaces.map {
            classpath.findClass(it)
        }
    }

    override val superClass by lazy(LazyThreadSafetyMode.NONE) {
        info.superClass?.let {
            classpath.findClass(it)
        }
    }

    override val outerClass by lazy(LazyThreadSafetyMode.NONE) {
        info.outerClass?.className?.let {
            classpath.findClass(it)
        }
    }

    override val innerClasses by lazy(LazyThreadSafetyMode.NONE) {
        info.innerClasses.map {
            classpath.findClass(it)
        }
    }

    override val access: Int
        get() = info.access

    override fun bytecode() = classSource.fullAsmNode
    override fun binaryBytecode(): ByteArray = classSource.byteCode

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

    override val declaredFields: List<JIRField> by lazy(LazyThreadSafetyMode.NONE) {
        info.fields.map { JIRFieldImpl(this, it) }
    }

    override val declaredMethods: List<JIRMethod> by lazy(LazyThreadSafetyMode.NONE) {
        info.methods.map { toJcMethod(it, classSource) }
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
}