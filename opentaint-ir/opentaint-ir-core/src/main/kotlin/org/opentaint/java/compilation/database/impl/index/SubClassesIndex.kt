package org.opentaint.java.compilation.database.impl.index


import kotlinx.collections.immutable.toImmutableList
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.opentaint.java.compilation.database.api.ByteCodeLocation

class SubClassesIndexBuilder(override val location: ByteCodeLocation) : ByteCodeLocationIndexBuilder<String> {

    // super class -> implementations
    private val parentToSubClasses = HashMap<String, HashSet<String>>()

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

    private fun addToIndex(parentInternalName: String, subClassInternalName: String) {
        val parentName = Type.getObjectType(parentInternalName).className
        val subClassName = Type.getObjectType(subClassInternalName).className
        val subClasses = parentToSubClasses.getOrPut(parentName) {
            HashSet()
        }
        subClasses.add(subClassName)
    }


    override fun build(): SubClassesIndex {
        val locationClasses = arrayListOf<String>()
        val parentClasses = arrayListOf<String>()
        val data = HashMap<Int, List<Int>>()
        parentToSubClasses.forEach { (parent, subClasses) ->
            parentClasses.add(parent)
            val subClassesIds = subClasses.map {
                locationClasses.add(it)
                locationClasses.size
            }
            data[parentClasses.size] = subClassesIds
        }
        return SubClassesIndex(
            location = location,
            locationClasses = locationClasses.toImmutableList(),
            parentClasses = parentClasses.toImmutableList(),
            parentToSubClasses = data
        )
    }

}


class SubClassesIndex(
    override val location: ByteCodeLocation,
    private val locationClasses: List<String>,
    private val parentClasses: List<String>,
    private val parentToSubClasses: Map<Int, List<Int>>
) : ByteCodeLocationIndex<String> {

    companion object {
        const val KEY = "sub-classes"
    }

    override val key = KEY

    override fun query(term: String): Sequence<String> {
        val parentClassId = parentClasses.indexOf(term)
        return parentToSubClasses[parentClassId]?.map {
            locationClasses.getOrNull(it)
        }?.asSequence()?.filterNotNull() ?: emptySequence()
    }

}


