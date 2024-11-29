package org.opentaint.opentaint-ir.impl.bytecode

import org.opentaint.opentaint-ir.api.ClassSource
import org.opentaint.opentaint-ir.api.JIRClassOrInterface
import org.opentaint.opentaint-ir.api.JIRClasspath
import org.opentaint.opentaint-ir.api.JIRMethod
import org.opentaint.opentaint-ir.impl.types.MethodInfo
import org.opentaint.opentaint-ir.impl.vfs.ClassVfsItem

fun JIRClasspath.toJIRClass(item: ClassVfsItem?): JIRClassOrInterface? {
    item ?: return null
    return toJIRClass(item.source)
}

fun JIRClassOrInterface.toJIRMethod(methodInfo: MethodInfo, source: ClassSource): JIRMethod {
    return JIRMethodImpl(methodInfo, source, this)
}