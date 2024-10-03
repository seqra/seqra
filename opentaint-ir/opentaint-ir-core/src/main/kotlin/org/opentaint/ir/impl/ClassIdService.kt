package org.opentaint.ir.impl

import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.api.MethodId
import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.impl.tree.ClassNode
import org.opentaint.ir.impl.tree.ClasspathClassTree
import org.opentaint.ir.impl.types.ArrayClassIdImpl
import org.opentaint.ir.impl.types.ClassIdImpl
import org.opentaint.ir.impl.types.MethodIdImpl
import org.opentaint.ir.impl.types.MethodInfo

class ClassIdService(private val cp: ClasspathSet, private val classpathClassTree: ClasspathClassTree) {

    fun toClassId(node: ClassNode?): ClassId? {
        node ?: return null
        return node.asClassId()
    }

    private fun ClassNode.asClassId() = ClassIdImpl(cp, this, this@ClassIdService)

    fun toClassId(fullName: String?): ClassId? {
        fullName ?: return null
        val predefinedClass = PredefinedPrimitives.of(fullName, cp)
        if (predefinedClass != null) {
            return predefinedClass
        }
        if (fullName.endsWith("[]")) {
            val targetName = fullName.removeSuffix("[]")
            return toClassId(targetName)?.let {
                ArrayClassIdImpl(it)
            }
        }

        return toClassId(classpathClassTree.firstClassOrNull(fullName))
    }

    fun toMethodId(classId: ClassId, methodInfo: MethodInfo, node: ClassNode): MethodId {
        return MethodIdImpl(methodInfo, node, classId, this)
    }

    fun toMethodId(node: ClassNode, methodInfo: MethodInfo): MethodId {
        return MethodIdImpl(methodInfo, node, node.asClassId(), this)
    }

}