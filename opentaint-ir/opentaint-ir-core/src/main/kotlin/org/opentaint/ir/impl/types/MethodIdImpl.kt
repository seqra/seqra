package org.opentaint.ir.impl.types

import org.objectweb.asm.tree.MethodNode
import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.MethodId
import org.opentaint.ir.api.classNotFound
import org.opentaint.ir.impl.ClassIdService
import org.opentaint.ir.impl.signature.MethodResolution
import org.opentaint.ir.impl.signature.MethodSignature
import org.opentaint.ir.impl.tree.ClassNode

class MethodIdImpl(
    private val methodInfo: MethodInfo,
    private val classNode: ClassNode,
    override val classId: ClassId,
    private val classIdService: ClassIdService
) : MethodId {

    override val name: String get() = methodInfo.name
    override suspend fun access() = methodInfo.access

    private val lazyParameters by lazy(LazyThreadSafetyMode.NONE) {
        methodInfo.parameters.map {
            classIdService.toClassId(it)  ?: throw org.opentaint.ir.api.NoClassInClasspathException(it)
        }
    }
    private val lazyAnnotations by lazy(LazyThreadSafetyMode.NONE) {
        methodInfo.annotations.map {
            val className = it.className
            classIdService.toClassId(className) ?: classNotFound(className)
        }
    }

    override suspend fun signature(): MethodResolution {
        return MethodSignature.of(methodInfo.signature, classId.classpath)
    }

    override suspend fun returnType() = classIdService.toClassId(methodInfo.returnType) ?: classNotFound(methodInfo.returnType)

    override suspend fun parameters() = lazyParameters

    override suspend fun annotations() = lazyAnnotations

    override suspend fun description() = methodInfo.desc

    override suspend fun readBody(): MethodNode? {
        val location = classId.location
        if (location?.isChanged() == true) {
            return null
        }
        return classNode.source.loadMethod(name, methodInfo.desc)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is MethodIdImpl) {
            return false
        }
        return other.name == name && classId == other.classId && methodInfo.desc == other.methodInfo.desc
    }

    override fun hashCode(): Int {
        return 31 * classId.hashCode() + name.hashCode()
    }


}