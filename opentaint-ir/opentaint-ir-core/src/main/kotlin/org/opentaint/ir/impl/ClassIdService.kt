package org.opentaint.ir.impl

import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.MethodId
import org.opentaint.ir.impl.fs.MethodMetaInfo
import org.opentaint.ir.impl.meta.ClassIdImpl
import org.opentaint.ir.impl.meta.MethodIdImpl
import org.opentaint.ir.impl.meta.PredefinedPrimitive
import org.opentaint.ir.impl.tree.ClassNode
import org.opentaint.ir.impl.tree.ClasspathClassTree
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentHashMap

class ClassIdService(internal val classpathClassTree: ClasspathClassTree) {

    companion object {
        private val predefinedClasses: PersistentMap<String, ClassId> = PredefinedPrimitive.values.map { it.simpleName to it }.toMap().toPersistentHashMap()
    }

    fun toClassId(node: ClassNode?): ClassId? {
        node ?: return null
        return ClassIdImpl(node, this)
    }

    fun toClassId(fullName: String?): ClassId? {
        fullName ?: return null
        val predefinedClass = predefinedClasses[fullName]
        if (predefinedClass != null) {
            return predefinedClass
        }
        return toClassId(classpathClassTree.findClassOrNull(fullName))
    }

    fun toMethodId(classId: ClassId, methodInfo: MethodMetaInfo, node: ClassNode): MethodId {
        return MethodIdImpl(methodInfo, node, classId, this)
    }

}