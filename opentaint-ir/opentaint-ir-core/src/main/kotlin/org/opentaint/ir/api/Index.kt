package org.opentaint.ir.api

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.io.InputStream
import java.io.OutputStream

/**
 * index builder
 */
interface ByteCodeLocationIndexBuilder<T, INDEX: ByteCodeLocationIndex<T>> {

    fun index(classNode: ClassNode)

    fun index(classNode: ClassNode, methodNode: MethodNode)

    fun build(location: ByteCodeLocation): INDEX

}

interface ByteCodeLocationIndex<T> {

    val location: ByteCodeLocation

    fun query(term: String): Sequence<T>
}

interface Feature<T, INDEX: ByteCodeLocationIndex<T>> {

    val key: String

    fun newBuilder() : ByteCodeLocationIndexBuilder<T, INDEX>

    fun serialize(index: INDEX, out: OutputStream)

    fun deserialize(stream: InputStream): INDEX?

}
