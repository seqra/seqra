package org.opentaint.ir.impl.index

import kotlinx.collections.immutable.toImmutableMap
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.opentaint.ir.api.*
import java.io.InputStream
import java.io.OutputStream

class HierarchyIndexBuilder(private val globalIdsStore: GlobalIdsStore) :
    ByteCodeLocationIndexBuilder<String, HierarchyIndex> {

    // super class -> implementations
    private val parentToSubClasses = hashMapOf<Int, HashSet<Int>>()

    override suspend fun index(classNode: ClassNode) {
        val superClass = classNode.superName?.takeIf {
            it != "java/lang/Object"
        }
        if (superClass != null) {
            addToIndex(superClass, classNode.name)
        }
        classNode.interfaces.forEach {
            addToIndex(it, classNode.name)
        }
    }

    override suspend fun index(classNode: ClassNode, methodNode: MethodNode) {
        // nothing to do here
    }

    private suspend fun addToIndex(parentInternalName: String, subClassInternalName: String) {
        val parentName = parentInternalName.intId()
        val subClassName = subClassInternalName.intId()
        val subClasses = parentToSubClasses.getOrPut(parentName) {
            HashSet()
        }
        subClasses.add(subClassName)
    }

    private suspend fun String.intId(): Int {
        val pureName = Type.getObjectType(this).className
        return globalIdsStore.getId(pureName)
    }

    override fun build(location: ByteCodeLocation): HierarchyIndex {
        return HierarchyIndex(
            globalIdsStore = globalIdsStore,
            location = location,
            parentToSubClasses = parentToSubClasses.toImmutableMap()
        )
    }

}


class HierarchyIndex(
    private val globalIdsStore: GlobalIdsStore,
    override val location: ByteCodeLocation,
    internal val parentToSubClasses: Map<Int, Set<Int>>
) : ByteCodeLocationIndex<String> {

    override suspend fun query(term: String): Sequence<String> {
        val parentClassId = globalIdsStore.getId(term)
        return parentToSubClasses[parentClassId]?.map {
            globalIdsStore.getName(it)
        }?.asSequence()?.filterNotNull() ?: emptySequence()
    }

}

object Hierarchy : Feature<String, HierarchyIndex> {

    override val key = "hierarchy"

    override fun newBuilder(globalIdsStore: GlobalIdsStore) = HierarchyIndexBuilder(globalIdsStore)

    override fun deserialize(
        globalIdsStore: GlobalIdsStore,
        location: ByteCodeLocation,
        stream: InputStream
    ): HierarchyIndex {
        val reader = stream.reader()
        val result = hashMapOf<Int, Set<Int>>()
        reader.use {
            reader.forEachLine {
                val (key, value) = it.asEntry()
                result[key] = value
            }
        }
        return HierarchyIndex(globalIdsStore, location, result)
    }

    override fun serialize(index: HierarchyIndex, out: OutputStream) {
        out.writer().use { writer ->
            index.parentToSubClasses.forEach {
                writer.write(it.asString())
            }
        }
    }

}