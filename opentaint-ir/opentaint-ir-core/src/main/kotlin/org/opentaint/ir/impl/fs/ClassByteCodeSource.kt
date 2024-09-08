package org.opentaint.ir.impl.fs

import org.opentaint.ir.ApiLevel
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.impl.suspendableLazy
import kotlinx.collections.immutable.toImmutableList
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import java.io.InputStream
import java.lang.ref.SoftReference

// todo inner/outer classes?
class ClassByteCodeSource(
    private val apiLevel: ApiLevel,
    val location: ByteCodeLocation,
    val className: String
) {

    private var fullNodeRef: SoftReference<ClassNode>? = null // this is a soft reference to fully loaded ASM class node
    private var preloadedNode: ClassNode? = null  // this is a preloaded instance loaded only for

    private val lazyMeta = suspendableLazy {
        val node = preloadedNode ?: getOrLoadFullClassNode()
        node.asClassInfo()
    }
    suspend fun meta() = lazyMeta()

    private suspend fun getOrLoadFullClassNode(): ClassNode {
        val cached = fullNodeRef?.get()
        if (cached == null) {
            val bytes = classInputStream()?.use { it.readBytes() }
            bytes ?: throw IllegalStateException("can't find bytecode for class $className in ${location.version}")
            val classNode = ClassNode(apiLevel.code).also {
                ClassReader(bytes).accept(it, ClassReader.EXPAND_FRAMES)
            }
            fullNodeRef = SoftReference(classNode)
            return classNode
        }
        return cached
    }

    private suspend fun classInputStream(): InputStream? {
        return location.resolve(className)
    }

    fun preLoad(initialInput: InputStream) {
        val bytes = initialInput.use { it.readBytes() }
        val classNode = ClassNode(apiLevel.code)
        ClassReader(bytes).accept(classNode, ClassReader.SKIP_CODE)
        preloadedNode = classNode
    }

    private fun ClassNode.asClassInfo() = ClassMetaInfo(
        name = Type.getObjectType(name).className,
        access = access,
        superClass = superName?.let { Type.getObjectType(it).className },
        interfaces = interfaces.map { Type.getObjectType(it).className }.toImmutableList(),
        methods = methods.map { it.asMethodInfo() }.toImmutableList(),
        fields = fields.map { it.asFieldInfo() }.toImmutableList(),
        annotations = visibleAnnotations.orEmpty().map { it.asAnnotationInfo() }.toImmutableList()
    )

    suspend fun loadMethod(methodName: String, methodDesc: String): MethodNode {
        val classNode = getOrLoadFullClassNode()
        return classNode.methods.first { it.name == methodName && it.desc == methodDesc }
    }

    private fun AnnotationNode.asAnnotationInfo() = AnnotationMetaInfo(
        className = Type.getType(desc).className
    )

    private fun MethodNode.asMethodInfo() = MethodMetaInfo(
        name = name,
        desc = desc,
        access = access,
        returnType = Type.getReturnType(desc).className,
        parameters = Type.getArgumentTypes(desc).map { it.className }.toImmutableList(),
        annotations = visibleAnnotations.orEmpty().map { it.asAnnotationInfo() }.toImmutableList()
    )

    private fun FieldNode.asFieldInfo() = FieldMetaInfo(
        name = name,
        access = access,
        type = Type.getType(desc).className
    )

}

