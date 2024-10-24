package org.opentaint.ir.impl

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.impl.bytecode.JIRClassOrInterfaceImpl
import org.opentaint.ir.impl.bytecode.JIRMethodImpl
import org.opentaint.ir.impl.types.MethodInfo
import org.opentaint.ir.impl.vfs.ClassVfsItem

class ClassIdService(internal val cp: JIRClasspath) {

    fun toClassId(node: ClassVfsItem?): JIRClassOrInterface? {
        node ?: return null
        return node.asClassId()
    }

    private fun ClassVfsItem.asClassId() = JIRClassOrInterfaceImpl(cp, this, this@ClassIdService)

    suspend fun toClassId(fullName: String?): JIRClassOrInterface? {
        fullName ?: return null
        return cp.findClassOrNull(fullName)
    }

    fun toMethodId(classId: JIRClassOrInterface, methodInfo: MethodInfo, node: ClassVfsItem): JIRMethod {
        return JIRMethodImpl(methodInfo, node, classId, this)
    }


}