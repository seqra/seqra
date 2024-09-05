package org.opentaint.ir.impl

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.impl.tree.ClassTree
import org.opentaint.ir.impl.tree.LimitedClassTree
import kotlinx.collections.immutable.PersistentList

class ClasspathSetImpl(
    override val locations: PersistentList<ByteCodeLocation>,
    classTree: ClassTree
) : ClasspathSet {

    private val limitedClassTree = LimitedClassTree(classTree, locations)

    override suspend fun findClassOrNull(name: String): ClassId? {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}