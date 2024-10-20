package org.opentaint.ir.impl.fs

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.impl.types.ClassInfo

class ClassByteCodeSource(val location: ByteCodeLocation, val className: String, val bytecode: ByteArray) :
    ByteCodeConverter {

    val info: ClassInfo by lazy(LazyThreadSafetyMode.NONE) {
        newClassNode(ClassReader.SKIP_CODE).asClassInfo(bytecode)
    }
    val fullByteCode: ClassNode get() = newClassNode(ClassReader.EXPAND_FRAMES)

    private fun newClassNode(level: Int): ClassNode {
        return ClassNode(Opcodes.ASM9).also {
            ClassReader(bytecode).accept(it, level)
        }
    }
}