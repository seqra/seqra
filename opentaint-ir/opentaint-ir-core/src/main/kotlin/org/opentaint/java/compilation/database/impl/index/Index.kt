package org.opentaint.java.compilation.database.impl.index

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.opentaint.java.compilation.database.api.ByteCodeLocation

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

