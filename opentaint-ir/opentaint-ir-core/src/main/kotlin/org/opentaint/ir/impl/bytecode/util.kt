package org.opentaint.opentaint-ir.impl.bytecode

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.JSRInlinerAdapter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.MethodNode
import org.opentaint.opentaint-ir.api.JIRClasspath

val ClassNode.hasFrameInfo: Boolean
    get() {
        return methods.any { mn -> mn.instructions.any { it is FrameNode } }
    }

internal val MethodNode.jsrInlined: MethodNode
    get() {
        val temp = JSRInlinerAdapter(null, access, name, desc, signature, exceptions?.toTypedArray())
        this.accept(temp)
        return temp
    }

internal fun ClassNode.computeFrames(classpath: JIRClasspath): ClassNode {
    val ba = this.toByteArray(classpath)
    return ba.toClassNode()
}

private fun ByteArray.toClassNode(): ClassNode {
    val classReader = ClassReader(this.inputStream())
    val classNode = ClassNode()
    classReader.accept(classNode, 0)
    return classNode
}

internal fun ClassNode.inlineJsrs() {
    this.methods = methods.map { it.jsrInlined }
}

private fun ClassNode.toByteArray(classpath: JIRClasspath): ByteArray {
    this.inlineJsrs()
    val cw = JIRDatabaseClassWriter(classpath, ClassWriter.COMPUTE_FRAMES)
    this.accept(cw)
    return cw.toByteArray()
}
