package org.opentaint.ir.impl.meta

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ClassId
import org.opentaint.ir.impl.ClassIdService
import org.opentaint.ir.impl.tree.ClassNode

class ClassIdImpl(private val node: ClassNode, private val classIdService: ClassIdService) : ClassId {

    override val location: ByteCodeLocation get() = node.location
    override val name: String get() = node.fullName
    override val simpleName: String get() = node.simpleName

    private val lazyInterfaces by lazy {
        node.info.interfaces.mapNotNull {
            classIdService.toClassId(it)
        }
    }

    private val lazySuperclass by lazy {
        classIdService.toClassId(node.info.superClass)
    }

    private val lazyMethods by lazy {
        node.info.methods.map {
            classIdService.toMethodId(this, it)
        }
    }

    private val lazyAnnotations by lazy {
        node.info.annotations.mapNotNull {
            val targetNode = classIdService.classpathClassTree.findClassOrNull(it.type)
            classIdService.toClassId(targetNode)
        }
    }

    override suspend fun methods() = lazyMethods

    override suspend fun superclass() = lazySuperclass

    override suspend fun interfaces() = lazyInterfaces

    override suspend fun annotations() = lazyAnnotations
}