package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRDeclaration
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.findMethodOrNull
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.findAndWrap
import org.opentaint.ir.impl.fs.ClassSource
import org.opentaint.ir.impl.suspendableLazy
import org.opentaint.ir.impl.toJcMethod

class JIRClassOrInterfaceImpl(
    override val classpath: JIRClasspath,
    private val classSource: ClassSource
) : JIRClassOrInterface {

    override val declaration: JIRDeclaration
        get() = JIRDeclarationImpl.of(location = classSource.location.jirLocation, this)

    override val name: String get() = classSource.className
    override val simpleName: String get() = classSource.className.substringAfterLast(".")

    override val signature: String?
        get() = classSource.info.signature

    override val annotations: List<JIRAnnotation>
        get() = classSource.info.annotations.map { JIRAnnotationImpl(it, classpath) }

    private val lazyInterfaces = suspendableLazy {
        classSource.info.interfaces.map {
            classpath.findAndWrap(it) ?: it.throwClassNotFound()
        }
    }

    private val lazySuperclass = suspendableLazy {
        val superClass = classSource.info.superClass
        if (superClass != null) {
            classpath.findAndWrap(classSource.info.superClass) ?: superClass.throwClassNotFound()
        } else {
            null
        }
    }

    private val lazyOuterClass = suspendableLazy {
        val className = classSource.info.outerClass?.className
        if (className != null) {
            classpath.findAndWrap(className) ?: className.throwClassNotFound()
        } else {
            null
        }
    }

    private val lazyInnerClasses = suspendableLazy {
        classSource.info.innerClasses.map {
            classpath.findAndWrap(it) ?: it.throwClassNotFound()
        }
    }

    override val access: Int
        get() = classSource.info.access

    override suspend fun bytecode() = classSource.fullAsmNode

    override suspend fun outerClass() = lazyOuterClass()

    override val isAnonymous: Boolean
        get() {
            val outerClass = classSource.info.outerClass
            return outerClass != null && outerClass.name == null
        }

    override suspend fun outerMethod(): JIRMethod? {
        val info = classSource.info
        if (info.outerMethod != null && info.outerMethodDesc != null) {
            return outerClass()?.findMethodOrNull(info.outerMethod, info.outerMethodDesc)
        }
        return null
    }

    override val fields: List<JIRField>
        get() = classSource.info.fields.map { JIRFieldImpl(this, it) }

    override val methods: List<JIRMethod>
        get() = classSource.info.methods.map { toJcMethod(it, classSource) }


    override suspend fun innerClasses() = lazyInnerClasses()

    override suspend fun superclass() = lazySuperclass()

    override suspend fun interfaces() = lazyInterfaces()

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