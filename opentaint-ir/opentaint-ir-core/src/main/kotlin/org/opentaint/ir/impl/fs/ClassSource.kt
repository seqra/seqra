package org.opentaint.ir.impl.fs

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.types.ClassInfo

interface ClassSource : ByteCodeConverter {
    val className: String
    val info: ClassInfo
    val location: RegisteredLocation
    val byteCode: ByteArray

    val asmNode: ClassNode
    val fullAsmNode: ClassNode

    fun newClassNode(level: Int): ClassNode {
        return ClassNode(Opcodes.ASM9).also {
            ClassReader(byteCode).accept(it, level)
        }
    }

}

class ClassSourceImpl(
    override val location: RegisteredLocation,
    override val className: String,
    override val byteCode: ByteArray
) : ClassSource {

    override val info: ClassInfo by lazy(LazyThreadSafetyMode.NONE) {
        newClassNode(ClassReader.SKIP_CODE).asClassInfo(byteCode)
    }

    override val asmNode by lazy(LazyThreadSafetyMode.NONE) {
        newClassNode(ClassReader.SKIP_CODE)
    }

    override val fullAsmNode: ClassNode get() = newClassNode(ClassReader.EXPAND_FRAMES)

}