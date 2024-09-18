package org.opentaint.java.compilation.database.impl.types

import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.api.ClassId
import org.opentaint.java.compilation.database.impl.ClassIdService
import org.opentaint.java.compilation.database.impl.suspendableLazy
import org.opentaint.java.compilation.database.impl.tree.ClassNode

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

    override suspend fun access() = node.info().access

    override suspend fun methods() = lazyMethods()

    override suspend fun superclass() = lazySuperclass()

    override suspend fun interfaces() = lazyInterfaces()

    override suspend fun annotations() = lazyAnnotations()

    override suspend fun fields() = lazyFields()
}