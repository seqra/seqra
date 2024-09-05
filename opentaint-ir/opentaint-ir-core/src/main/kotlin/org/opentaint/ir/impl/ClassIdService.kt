package org.opentaint.ir.impl

import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.MethodId
import org.opentaint.ir.impl.meta.ClassIdImpl
import org.opentaint.ir.impl.meta.MethodIdImpl
import org.opentaint.ir.impl.reader.MethodMetaInfo
import org.opentaint.ir.impl.tree.ClassNode
import org.opentaint.ir.impl.tree.ClasspathClassTree

class ClassIdService(internal val classpathClassTree: ClasspathClassTree) {

    fun toClassId(node: ClassNode?): ClassId? {
        node ?: return null
        return ClassIdImpl(node, this)
    }

    fun toClassId(fullName: String?): ClassId? {
        fullName ?: return null
        return toClassId(classpathClassTree.findClassOrNull(fullName))
    }

    fun toMethodId(classId: ClassId, methodInfo: MethodMetaInfo): MethodId {
        return MethodIdImpl(methodInfo, classId, this)
    }

}