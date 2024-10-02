package org.opentaint.ir.impl.fs

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.impl.suspendableLazy
import org.opentaint.ir.impl.types.ClassInfo
import java.io.InputStream

abstract class ClassByteCodeSource(val location: ByteCodeLocation, val className: String) : ByteCodeConverter {

    abstract suspend fun info(): ClassInfo
    abstract suspend fun fullByteCode(): ClassNode

    abstract fun onAfterIndexing()

    abstract fun load(input: InputStream)

    protected suspend fun classInputStream(): InputStream? {
        return location.resolve(className)
    }

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