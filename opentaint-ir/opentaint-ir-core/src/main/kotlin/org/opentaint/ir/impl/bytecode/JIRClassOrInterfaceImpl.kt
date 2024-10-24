package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRDeclaration
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.findMethodOrNull
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.ClassIdService
import org.opentaint.ir.impl.suspendableLazy
import org.opentaint.ir.impl.vfs.ClassVfsItem

class JIRClassOrInterfaceImpl(
    override val classpath: JIRClasspath,
    private val node: ClassVfsItem,
    private val classIdService: ClassIdService
) : JIRClassOrInterface {

    override val declaration: JIRDeclaration
        get() = JIRDeclarationImpl.of(location = node.location, this)

    override val name: String get() = node.fullName
    override val simpleName: String get() = node.name
    override val signature: String?
        get() = node.info().signature

    override val annotations: List<JIRAnnotation>
        get() = node.info().annotations.map { JIRAnnotationImpl(it, classIdService.cp) }

    private val lazyInterfaces = suspendableLazy {
        node.info().interfaces.map {
            classIdService.toClassId(it) ?: it.throwClassNotFound()
        }
    }

    private val lazySuperclass = suspendableLazy {
        val superClass = node.info().superClass
        if (superClass != null) {
            classIdService.toClassId(node.info().superClass) ?: superClass.throwClassNotFound()
        } else {
            null
        }
    }

    private val lazyOuterClass = suspendableLazy {
        val className = node.info().outerClass?.className
        if (className != null) {
            classIdService.toClassId(className) ?: className.throwClassNotFound()
        } else {
            null
        }
    }

    private val lazyInnerClasses = suspendableLazy {
        node.info().innerClasses.map {
            classIdService.toClassId(it) ?: it.throwClassNotFound()
        }
    }

    override val access: Int
        get() = node.info().access

    override suspend fun outerClass() = lazyOuterClass()

    override val isAnonymous: Boolean
        get() {
            val outerClass = node.info().outerClass
            return outerClass != null && outerClass.name == null
        }

    override suspend fun outerMethod(): JIRMethod? {
        val info = node.info()
        if (info.outerMethod != null && info.outerMethodDesc != null) {
            return outerClass()?.findMethodOrNull(info.outerMethod, info.outerMethodDesc)
        }
        return null
    }

    override val fields: List<JIRField>
        get() = node.info().fields.map { JIRFieldImpl(this, it, classIdService) }

    override val methods: List<JIRMethod>
        get() = node.info().methods.map { classIdService.toMethodId(this, it, node) }


    override suspend fun innerClasses() = lazyInnerClasses()

    override suspend fun superclass() = lazySuperclass()

    override suspend fun interfaces() = lazyInterfaces()

    suspend fun info() = node.info()

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