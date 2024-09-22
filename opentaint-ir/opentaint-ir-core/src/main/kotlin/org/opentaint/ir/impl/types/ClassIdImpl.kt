package org.opentaint.ir.impl.types

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.MethodId
import org.opentaint.ir.api.findMethodOrNull
import org.opentaint.ir.impl.ClassIdService
import org.opentaint.ir.impl.suspendableLazy
import org.opentaint.ir.impl.tree.ClassNode

class ClassIdImpl(private val node: ClassNode, private val classIdService: ClassIdService) : ClassId {

    override val location: ByteCodeLocation get() = node.location
    override val name: String get() = node.fullName
    override val simpleName: String get() = node.name

    private val lazyInterfaces = suspendableLazy {
        node.info().interfaces.mapNotNull {
            classIdService.toClassId(it)
        }
    }

    private val lazySuperclass = suspendableLazy {
        classIdService.toClassId(node.info().superClass)
    }

    private val lazyOuterClass = suspendableLazy {
        classIdService.toClassId(node.info().outerClass)
    }

    private val lazyMethods = suspendableLazy {
        node.info().methods.map {
            classIdService.toMethodId(this, it, node)
        }
    }

    private val lazyAnnotations = suspendableLazy {
        node.info().annotations.mapNotNull {
            classIdService.toClassId(it.className)
        }
    }

    private val lazyFields = suspendableLazy {
        node.info().fields.map { FieldIdImpl(this, it, classIdService) }
    }

    override suspend fun byteCode(): org.objectweb.asm.tree.ClassNode {
        return node.fullByteCode()
    }

    override suspend fun access() = node.info().access

    override suspend fun outerClass() = lazyOuterClass()

    override suspend fun outerMethod(): MethodId? {
        val info = node.info()
        if (info.outerMethod != null && info.outerMethodDesc != null) {
            return outerClass()?.findMethodOrNull(info.outerMethod, info.outerMethodDesc)
        }
        return null
    }

    override suspend fun methods() = lazyMethods()

    override suspend fun superclass() = lazySuperclass()

    override suspend fun interfaces() = lazyInterfaces()

    override suspend fun annotations() = lazyAnnotations()

    override suspend fun fields() = lazyFields()

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is ClassIdImpl) {
            return false
        }
        return other.name == name && other.location == location
    }

    override fun hashCode(): Int {
        return 31 * location.hashCode() + name.hashCode()
    }
}