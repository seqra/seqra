package org.opentaint.ir.impl.fs

import org.opentaint.ir.ApiLevel
import org.opentaint.ir.api.ByteCodeLocation
import kotlinx.collections.immutable.toPersistentList
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

    private var fullNodeRef: SoftReference<ClassNode>? = null
    lateinit var meta: ClassMetaInfo

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

    fun loadLightInfo(initialInput: InputStream): ClassMetaInfo {
        val bytes = initialInput.use { it.readBytes() }
        val classNode = ClassNode(apiLevel.code)
        ClassReader(bytes).accept(classNode, ClassReader.SKIP_CODE)

        return ClassMetaInfo(
            name = Type.getObjectType(classNode.name).className,
            access = classNode.access,
            superClass = classNode.superName?.let { Type.getObjectType(it).className },
            interfaces = classNode.interfaces.map { Type.getObjectType(it).className }.toPersistentList(),
            methods = classNode.methods.map { it.asMethodInfo() }.toPersistentList(),
            fields = classNode.fields.map { it.asFieldInfo() }.toPersistentList(),
            annotations = classNode.visibleAnnotations.orEmpty().map { it.asAnnotationInfo() }.toPersistentList()
        ).also {
            meta  = it
        }
    }

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
        parameters = Type.getArgumentTypes(desc).map { it.className }.toPersistentList(),
        annotations = visibleAnnotations.orEmpty().map { it.asAnnotationInfo() }.toPersistentList()
    )

    private fun FieldNode.asFieldInfo() = FieldMetaInfo(
        name = name,
        access = access,
        type = Type.getType(desc).className
    )

}

suspend fun ByteCodeLocation.sources(): Sequence<ClassByteCodeSource> {
    return classesByteCode().map {
        ClassByteCodeSource(apiLevel, location = this, it.first).also { reader ->
            reader.loadLightInfo(it.second)
        }
    }
}