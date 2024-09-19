package org.opentaint.java.compilation.database.impl.fs

import kotlinx.collections.immutable.toImmutableList
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.impl.suspendableLazy
import org.opentaint.java.compilation.database.impl.types.AnnotationInfo
import org.opentaint.java.compilation.database.impl.types.ClassInfo
import org.opentaint.java.compilation.database.impl.types.FieldInfo
import org.opentaint.java.compilation.database.impl.types.MethodInfo
import java.io.InputStream

// todo inner/outer classes?
class ClassByteCodeSource(
    val location: ByteCodeLocation,
    val className: String
) {

//    // this is a soft reference to fully loaded ASM class node
//    private var fullNodeRef: SoftReference<ClassNode>? = null

    private var cachedClassNode: ClassNode? = null

    private val lazyClassInfo = suspendableLazy {
        lazyClassNode().asClassInfo()
    }

    private val lazyClassNode = suspendableLazy {
        cachedClassNode ?: getOrLoadFullClassNode()
    }

    suspend fun info(): ClassInfo = lazyClassInfo()
    suspend fun asmNode() = lazyClassNode()

    private suspend fun getOrLoadFullClassNode(): ClassNode {
        val cached = cachedClassNode
        if (cached == null) {
            val bytes = classInputStream()?.use { it.readBytes() }
            bytes ?: throw IllegalStateException("can't find bytecode for class $className in $location")
            val classNode = ClassNode(Opcodes.ASM9).also {
                ClassReader(bytes).accept(it, ClassReader.EXPAND_FRAMES)
            }
            cachedClassNode = classNode
            return classNode
        }
        return cached
    }

    private suspend fun classInputStream(): InputStream? {
        return location.resolve(className)
    }

    fun load(initialInput: InputStream) {
        val bytes = initialInput.use { it.readBytes() }
        val classNode = ClassNode(Opcodes.ASM9)
        ClassReader(bytes).accept(classNode, ClassReader.EXPAND_FRAMES)
        cachedClassNode = classNode
    }

    private fun ClassNode.asClassInfo() = ClassInfo(
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

    private fun AnnotationNode.asAnnotationInfo() = AnnotationInfo(
        className = Type.getType(desc).className
    )

    private fun MethodNode.asMethodInfo() = MethodInfo(
        name = name,
        desc = desc,
        access = access,
        returnType = Type.getReturnType(desc).className,
        parameters = Type.getArgumentTypes(desc).map { it.className }.toImmutableList(),
        annotations = visibleAnnotations.orEmpty().map { it.asAnnotationInfo() }.toImmutableList()
    )

    private fun FieldNode.asFieldInfo() = FieldInfo(
        name = name,
        access = access,
        type = Type.getType(desc).className,
        annotations = visibleAnnotations.orEmpty().map { it.asAnnotationInfo() }.toImmutableList()
    )

}

