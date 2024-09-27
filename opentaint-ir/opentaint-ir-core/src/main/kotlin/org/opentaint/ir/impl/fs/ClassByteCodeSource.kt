package org.opentaint.ir.impl.fs

import kotlinx.collections.immutable.toImmutableList
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.impl.suspendableLazy
import org.opentaint.ir.impl.types.*
import java.io.InputStream

abstract class ClassByteCodeSource(val location: ByteCodeLocation, val className: String) {

    abstract suspend fun info(): ClassInfo
    abstract suspend fun fullByteCode(): ClassNode

    abstract fun onAfterIndexing()

    abstract fun load(input: InputStream)

    protected suspend fun classInputStream(): InputStream? {
        return location.resolve(className)
    }

    protected fun ClassNode.asClassInfo() = ClassInfo(
        name = Type.getObjectType(name).className,
        signature = signature,
        access = access,

        outerClass = outerClassName(),
        innerClasses = innerClasses.map {
            Type.getObjectType(it.name).className
        },
        outerMethod = outerMethod,
        outerMethodDesc = outerMethodDesc,
        superClass = superName?.let { Type.getObjectType(it).className },
        interfaces = interfaces.map { Type.getObjectType(it).className }.toImmutableList(),
        methods = methods.map { it.asMethodInfo() }.toImmutableList(),
        fields = fields.map { it.asFieldInfo() }.toImmutableList(),
        annotations = visibleAnnotations.orEmpty().map { it.asAnnotationInfo() }.toImmutableList()
    )

    private fun ClassNode.outerClassName(): OuterClassRef? {
        val innerRef = innerClasses.firstOrNull { it.name == name }

        val direct = outerClass?.let { Type.getObjectType(it).className }
        if (direct == null && innerRef != null) {
            return OuterClassRef(Type.getObjectType(innerRef.outerName).className, innerRef.innerName)
        }
        return direct?.let {
            OuterClassRef(it, innerRef?.innerName)
        }
    }

    private fun AnnotationNode.asAnnotationInfo() = AnnotationInfo(
        className = Type.getType(desc).className
    )

    private fun MethodNode.asMethodInfo() = MethodInfo(
        name = name,
        signature = signature,
        desc = desc,
        access = access,
        returnType = Type.getReturnType(desc).className,
        parameters = Type.getArgumentTypes(desc).map { it.className }.toImmutableList(),
        annotations = visibleAnnotations.orEmpty().map { it.asAnnotationInfo() }.toImmutableList()
    )

    private fun FieldNode.asFieldInfo() = FieldInfo(
        name = name,
        signature = signature,
        access = access,
        type = Type.getType(desc).className,
        annotations = visibleAnnotations.orEmpty().map { it.asAnnotationInfo() }.toImmutableList()
    )

    suspend fun loadMethod(name: String, desc: String): MethodNode? {
        return fullByteCode().methods.first { it.name == name && it.desc == desc }
    }

}


class LazyByteCodeSource(location: ByteCodeLocation, className: String) :
    ClassByteCodeSource(location, className) {

    constructor(location: ByteCodeLocation, classInfo: ClassInfo) : this(location, classInfo.name) {
        this.classInfo = classInfo
    }

    private lateinit var classInfo: ClassInfo

    @Volatile
    private var classNode: ClassNode? = null

    override fun load(input: InputStream) {
        val bytes = input.use { it.readBytes() }
        val classNode = ClassNode(Opcodes.ASM9)
        ClassReader(bytes).accept(classNode, ClassReader.EXPAND_FRAMES)

        this.classNode = classNode
        this.classInfo = classNode.asClassInfo()
    }

    override suspend fun info(): ClassInfo {
        if (this::classInfo.isInitialized) {
            return classInfo
        }
        return (this.classNode ?: fullByteCode()).asClassInfo().also {
            classInfo = it
        }
    }

    override fun onAfterIndexing() {
        classNode = null
    }

    override suspend fun fullByteCode(): ClassNode {
        val node = classNode
        if (node != null) {
            return node
        }
        val bytes = classInputStream()?.use { it.readBytes() }
        bytes ?: throw IllegalStateException("can't find bytecode for class $className in $location")
        return ClassNode(Opcodes.ASM9).also {
            ClassReader(bytes).accept(it, ClassReader.EXPAND_FRAMES)
        }
    }
}

class ExpandedByteCodeSource(location: ByteCodeLocation, className: String) :
    ClassByteCodeSource(location, className) {

    @Volatile
    private var cachedByteCode: ClassNode? = null

    private val lazyClassInfo = suspendableLazy {
        fullByteCode().asClassInfo()
    }

    override fun load(input: InputStream) {
        val bytes = input.use { it.readBytes() }
        val classNode = ClassNode(Opcodes.ASM9)
        ClassReader(bytes).accept(classNode, ClassReader.EXPAND_FRAMES)
        cachedByteCode = classNode
    }

    override suspend fun info() = lazyClassInfo()
    override suspend fun fullByteCode(): ClassNode {
        val cached = cachedByteCode
        if (cached == null) {
            val bytes = classInputStream()?.use { it.readBytes() }
            bytes ?: throw IllegalStateException("can't find bytecode for class $className in $location")
            val classNode = ClassNode(Opcodes.ASM9).also {
                ClassReader(bytes).accept(it, ClassReader.EXPAND_FRAMES)
            }
            cachedByteCode = classNode
            return classNode
        }
        return cached
    }

    override fun onAfterIndexing() {
        // do nothing. this is expected to be hot code
    }

}