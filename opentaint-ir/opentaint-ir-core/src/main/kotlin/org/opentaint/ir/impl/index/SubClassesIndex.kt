package org.opentaint.ir.impl.index

import kotlinx.collections.immutable.toImmutableMap
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ByteCodeLocationIndex
import org.opentaint.ir.api.ByteCodeLocationIndexBuilder
import org.opentaint.ir.api.IndexInstaller
import java.io.InputStream
import java.io.OutputStream

class SubClassesIndexBuilder : ByteCodeLocationIndexBuilder<String> {

    // super class -> implementations
    private val parentToSubClasses = hashMapOf<Int, HashSet<Int>>()

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
        // nothing to do here
    }

    private fun addToIndex(parentInternalName: String, subClassInternalName: String) {
        val parentName = parentInternalName.intId
        val subClassName = subClassInternalName.intId
        val subClasses = parentToSubClasses.getOrPut(parentName) {
            HashSet()
        }
        subClasses.add(subClassName)
    }

    private val String.intId: Int
        get() {
            val pureName = Type.getObjectType(this).className
            return GlobalIds.getId(pureName)
        }

    override fun build(location: ByteCodeLocation): SubClassesIndex {
        return SubClassesIndex(
            location = location,
            parentToSubClasses = parentToSubClasses.toImmutableMap()
        )
    }

}


class SubClassesIndex(
    override val location: ByteCodeLocation,
    private val parentToSubClasses: Map<Int, Set<Int>>
) : ByteCodeLocationIndex<String> {

    override fun query(term: String): Sequence<String> {
        val parentClassId = GlobalIds.getId(term)
        return parentToSubClasses[parentClassId]?.map {
            GlobalIds.getName(it)
        }?.asSequence()?.filterNotNull() ?: emptySequence()
    }

}


object SubClassIndex : IndexInstaller<String, SubClassesIndex> {

    override val key = "sub-classes"

    override val isIndexMethodBody = false

    override fun newBuilder() = SubClassesIndexBuilder()

    override fun deserialize(stream: InputStream) = null

    override fun serialize(index: SubClassesIndex, out: OutputStream) {
        TODO()
    }

}