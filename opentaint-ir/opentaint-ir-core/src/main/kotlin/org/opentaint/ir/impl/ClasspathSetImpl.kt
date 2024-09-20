package org.opentaint.ir.impl

import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldInsnNode
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.api.MethodId
import org.opentaint.ir.impl.fs.relevantLocations
import org.opentaint.ir.impl.index.ReversedUsagesIndex
import org.opentaint.ir.impl.index.subClassesExt
import org.opentaint.ir.impl.tree.ClassTree
import org.opentaint.ir.impl.tree.ClasspathClassTree

class ClasspathSetImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    private val indexesRegistry: IndexesRegistry,
    override val db: CompilationDatabaseImpl,
    classTree: ClassTree
) : ClasspathSet {

    override val locations: List<ByteCodeLocation> = locationsRegistrySnapshot.locations

    private val classpathClassTree = ClasspathClassTree(classTree, locationsRegistrySnapshot)
    private val classIdService = ClassIdService(classpathClassTree)

    override suspend fun refreshed(closeOld: Boolean): ClasspathSet {
        return db.classpathSet(locationsRegistrySnapshot.locations).also {
            if (closeOld) {
                close()
            }
        }
    }

    override suspend fun findClassOrNull(name: String): ClassId? {
        return classIdService.toClassId(classpathClassTree.firstClassOrNull(name))
    }

    override suspend fun findSubClasses(name: String, allHierarchy: Boolean): List<ClassId> {
        return subClassesExt.findSubClasses(name, allHierarchy)
    }

    override suspend fun findSubClasses(classId: ClassId, allHierarchy: Boolean): List<ClassId> {
        return subClassesExt.findSubClasses(classId, allHierarchy)
    }

    override suspend fun <T> query(key: String, term: String): List<T> {
        db.awaitBackgroundJobs()
        return locations.flatMap { indexesRegistry.findIndex<T>(key, it)?.query(term).orEmpty() }
    }

    override suspend fun <T> query(key: String, location: ByteCodeLocation, term: String): List<T> {
        db.awaitBackgroundJobs()
        return indexesRegistry.findIndex<T>(key, location)?.query(term).orEmpty().toList()
    }



    override fun close() {
        locationsRegistrySnapshot.close()
    }

    private suspend fun List<String>.findUsages(
        name: String,
        ownerName: String,
        opcode: Int,
    ): List<MethodId> {
        val result = hashSetOf<MethodId>()
        forEach {
            val classNode = classpathClassTree.firstClassOrNull(it)
            classNode?.asmNode()?.methods?.forEach { method ->
                for (inst in method.instructions) {
                    if (inst is FieldInsnNode) {
                        val matches = inst.name == name
                                && Type.getObjectType(inst.owner).className == ownerName
                                && inst.opcode == opcode
                        if (matches) {
                            val m = classNode.info().methods.firstOrNull {
                                it.name == method.name && it.desc == method.desc
                            }
                            if (m != null) {
                                result.add(classIdService.toMethodId(classNode, m))
                            } else {
                                println("Ouch")
                            }
                            break
                        }
                    }
                }
            }
        }
        return result.toList()
    }

    private suspend fun findPotentialCandidates(methodOrField: String, classId: ClassId): Pair<String, List<String>> {
        db.awaitBackgroundJobs()
        val ownerName = classId.name
        val location = classId.location
        val potentialCandidates = locations.relevantLocations(location).flatMap {
            indexesRegistry.findIndex<String>(ReversedUsagesIndex.key, it)?.query(methodOrField).orEmpty()
        }
        return Pair(ownerName, potentialCandidates)
    }

}