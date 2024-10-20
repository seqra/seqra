package org.opentaint.ir.impl

import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.Classpath
import org.opentaint.ir.api.MethodId
import org.opentaint.ir.impl.tree.ClassNode
import org.opentaint.ir.impl.tree.ClasspathClassTree
import org.opentaint.ir.impl.types.ClassIdImpl
import org.opentaint.ir.impl.types.MethodIdImpl
import org.opentaint.ir.impl.types.MethodInfo

class ClassIdService(internal val cp: Classpath, private val classpathClassTree: ClasspathClassTree) {

    fun toClassId(node: ClassNode?): ClassId? {
        node ?: return null
        return node.asClassId()
    }

    private fun ClassNode.asClassId() = ClassIdImpl(cp, this, this@ClassIdService)

    suspend fun toClassId(fullName: String?): ClassId? {
        fullName ?: return null
        return cp.findClassOrNull(fullName)
    }

    fun toMethodId(classId: ClassId, methodInfo: MethodInfo, node: ClassNode): MethodId {
        return MethodIdImpl(methodInfo, node, classId, this)
    }


}