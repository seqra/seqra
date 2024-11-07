package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.ClassSource
import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.findMethodOrNull
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.fs.fullAsmNode
import org.opentaint.ir.impl.fs.info

class JIRClassOrInterfaceImpl(
    override val classpath: JIRClasspath,
    private val classSource: ClassSource
) : JIRClassOrInterface {

    private val info = classSource.info

    override val declaration = JIRDeclarationImpl.of(location = classSource.location.jirLocation, this)

    override val name: String get() = classSource.className
    override val simpleName: String get() = classSource.className.substringAfterLast(".")

    override val signature: String?
        get() = info.signature

    override val annotations: List<JIRAnnotation>
        get() = info.annotations.map { JIRAnnotationImpl(it, classpath) }

    override val interfaces by lazy(LazyThreadSafetyMode.NONE) {
        info.interfaces.map {
            classpath.findAndWrap(it) ?: it.throwClassNotFound()
        }
    }

    override val superClass by lazy(LazyThreadSafetyMode.NONE) {
        val superClass = info.superClass
        if (superClass != null) {
            classpath.findAndWrap(info.superClass) ?: superClass.throwClassNotFound()
        } else {
            null
        }
    }

    override val outerClass by lazy(LazyThreadSafetyMode.NONE) {
        val className = info.outerClass?.className
        if (className != null) {
            classpath.findAndWrap(className) ?: className.throwClassNotFound()
        } else {
            null
        }
    }

    override val innerClasses by lazy(LazyThreadSafetyMode.NONE) {
        info.innerClasses.map {
            classpath.findAndWrap(it) ?: it.throwClassNotFound()
        }
    }

    override val access: Int
        get() = info.access

    override fun bytecode() = classSource.fullAsmNode

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

    override val fields: List<JIRField> by lazy(LazyThreadSafetyMode.NONE) {
        info.fields.map { JIRFieldImpl(this, it) }
    }

    override val methods: List<JIRMethod> by lazy(LazyThreadSafetyMode.NONE) {
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