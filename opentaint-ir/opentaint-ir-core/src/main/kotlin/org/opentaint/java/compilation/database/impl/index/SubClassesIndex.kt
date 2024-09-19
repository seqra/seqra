package org.opentaint.java.compilation.database.impl.index

import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.api.ByteCodeLocationIndex
import org.opentaint.java.compilation.database.api.ByteCodeLocationIndexBuilder
import org.opentaint.java.compilation.database.api.IndexInstaller
import java.io.InputStream
import java.io.OutputStream

class SubClassesIndexBuilder(override val location: ByteCodeLocation) : ByteCodeLocationIndexBuilder<String> {

    // super class -> implementations
    private val parentToSubClasses = hashMapOf<Int, HashSet<Int>>()
    private val allClassesIds = hashMapOf<String, Int>()

    override fun index(classNode: ClassNode) {
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

    override fun index(classNode: ClassNode, methodNode: MethodNode) {
        // do nothing
    }

    private val String.identifier: Int
        get() {
            val pureName = Type.getObjectType(this).className
            return allClassesIds.getOrPut(pureName) {
                allClassesIds.size
            }
        }

    private fun addToIndex(parentInternalName: String, subClassInternalName: String) {
        val parentName = parentInternalName.identifier
        val subClassName = subClassInternalName.identifier
        val subClasses = parentToSubClasses.getOrPut(parentName) {
            HashSet()
        }
        subClasses.add(subClassName)
    }


    override fun build(): SubClassesIndex {
        val orderedClasses = allClassesIds.entries.sortedBy {
            it.value
        }.map { it.key }
        return SubClassesIndex(
            location = location,
            classes = orderedClasses.toImmutableList(),
            parentToSubClasses = parentToSubClasses.toImmutableMap()
        )
    }

}


class SubClassesIndex(
    override val location: ByteCodeLocation,
    private val classes: List<String>,
    private val parentToSubClasses: Map<Int, Set<Int>>
) : ByteCodeLocationIndex<String> {

    companion object {
        const val KEY = "sub-classes"
    }

    override val key = KEY

    override fun query(term: String): Sequence<String> {
        val parentClassId = classes.indexOf(term)
        return parentToSubClasses[parentClassId]?.map {
            classes.getOrNull(it)
        }?.asSequence()?.filterNotNull() ?: emptySequence()
    }

}


object SubClassIndex : IndexInstaller<String, SubClassesIndex> {

    override fun indexBuilderOf(location: ByteCodeLocation) = SubClassesIndexBuilder(location)

    override fun deserialize(stream: InputStream) = null

    override fun serialize(index: SubClassesIndex, out: OutputStream) {
        TODO()
    }

}