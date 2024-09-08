package org.opentaint.java.compilation.database.impl

import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.api.ClassId
import org.opentaint.java.compilation.database.api.ClasspathSet
import org.opentaint.java.compilation.database.impl.tree.ClassTree
import org.opentaint.java.compilation.database.impl.tree.ClasspathClassTree

class ClasspathSetImpl(
    override val locations: List<ByteCodeLocation>,
    private val db: CompilationDatabaseImpl,
    classTree: ClassTree
) : ClasspathSet {

    private val classpathClassTree = ClasspathClassTree(classTree, locations)
    private val classIdService = ClassIdService(classpathClassTree)

    override suspend fun findClassOrNull(name: String): ClassId? {
        return classIdService.toClassId(classpathClassTree.firstClassOrNull(name))
    }

    override suspend fun findSubTypesOf(name: String): List<ClassId> {
        db.awaitBackgroundJobs()
        return classpathClassTree.findSubTypesOf(name)
            .map { classIdService.toClassId(it) }
            .filterNotNull()
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}