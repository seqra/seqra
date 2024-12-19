package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.ClassSource
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.impl.types.MethodInfo
import org.opentaint.ir.impl.vfs.ClassVfsItem

fun JIRClasspath.toJIRClass(item: ClassVfsItem?): JIRClassOrInterface? {
    item ?: return null
    return toJIRClass(item.source)
}

fun JIRClassOrInterface.toJIRMethod(methodInfo: MethodInfo, source: ClassSource): JIRMethod {
    return JIRMethodImpl(methodInfo, source, this)
}