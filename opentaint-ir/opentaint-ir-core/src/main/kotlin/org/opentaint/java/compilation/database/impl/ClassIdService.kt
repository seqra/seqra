package org.opentaint.java.compilation.database.impl

import kotlinx.collections.immutable.toImmutableMap
import org.opentaint.java.compilation.database.api.ClassId
import org.opentaint.java.compilation.database.api.MethodId
import org.opentaint.java.compilation.database.impl.tree.ClassNode
import org.opentaint.java.compilation.database.impl.tree.ClasspathClassTree
import org.opentaint.java.compilation.database.impl.types.ClassIdImpl
import org.opentaint.java.compilation.database.impl.types.MethodIdImpl
import org.opentaint.java.compilation.database.impl.types.MethodMetaInfo
import org.opentaint.java.compilation.database.impl.types.PredefinedPrimitive

class ClassIdService(internal val classpathClassTree: ClasspathClassTree) {

    companion object {
        private val predefinedClasses = PredefinedPrimitive.values.associateBy { it.simpleName }.toImmutableMap()
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
        return toClassId(classpathClassTree.firstClassOrNull(fullName))
    }

    fun toMethodId(classId: ClassId, methodInfo: MethodMetaInfo, node: ClassNode): MethodId {
        return MethodIdImpl(methodInfo, node, classId, this)
    }

}