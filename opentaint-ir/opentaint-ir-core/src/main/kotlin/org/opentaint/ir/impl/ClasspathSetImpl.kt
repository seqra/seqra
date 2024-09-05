package org.opentaint.ir.impl

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.impl.tree.ClassTree
import org.opentaint.ir.impl.tree.ClasspathClassTree
import kotlinx.collections.immutable.PersistentList

class ClasspathSetImpl(
    override val locations: PersistentList<ByteCodeLocation>,
    classTree: ClassTree
) : ClasspathSet {

    private val classpathClassTree = ClasspathClassTree(classTree, locations)
    private val classIdService = ClassIdService(classpathClassTree)

    override suspend fun findClassOrNull(name: String): ClassId? {
        return classIdService.toClassId(classpathClassTree.findClassOrNull(name))
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}