package org.opentaint.java.compilation.database.api

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.io.InputStream
import java.io.OutputStream

/**
 * index builder
 */
interface ByteCodeLocationIndexBuilder<T> {

    val location: ByteCodeLocation

    fun index(classNode: ClassNode)

    fun index(classNode: ClassNode, methodNode: MethodNode)

    fun build(): ByteCodeLocationIndex<T>

}

interface ByteCodeLocationIndex<T> {

    val key: String
    val location: ByteCodeLocation

    fun query(term: String): Sequence<T>
}

interface IndexInstaller<T, INDEX: ByteCodeLocationIndex<T>> {

    fun indexBuilderOf(location: ByteCodeLocation) : ByteCodeLocationIndexBuilder<T>

    fun serialize(index: INDEX, out: OutputStream)

    fun deserialize(stream: InputStream): INDEX?

}
