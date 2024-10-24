package org.opentaint.ir.impl.fs

import org.jetbrains.exposed.sql.Database
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.types.ClassInfo

interface ClassByteCodeSource : ByteCodeConverter {
    val className: String
    val info: ClassInfo
    val location: RegisteredLocation
    val binaryByteCode: ByteArray

    val asmNode: ClassNode
    val fullAsmNode: ClassNode

    fun newClassNode(level: Int): ClassNode {
        return ClassNode(Opcodes.ASM9).also {
            ClassReader(binaryByteCode).accept(it, level)
        }
    }

}

class ClassByteCodeSourceImpl(
    override val location: RegisteredLocation,
    override val className: String,
    override val binaryByteCode: ByteArray
) : ClassByteCodeSource {

    override val info: ClassInfo by lazy(LazyThreadSafetyMode.NONE) {
        newClassNode(ClassReader.SKIP_CODE).asClassInfo(binaryByteCode)
    }

    override val asmNode by lazy(LazyThreadSafetyMode.NONE) {
        newClassNode(ClassReader.SKIP_CODE)
    }

    override val fullAsmNode: ClassNode get() = newClassNode(ClassReader.EXPAND_FRAMES)

}

class PersistentByteCodeSource(val classId: Long, private val db: Database) {

}