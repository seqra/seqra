package org.opentaint.jvm.sast.dataflow

import org.opentaint.ir.api.jvm.JIRClassOrInterface

fun interface ClassLocationChecker {
    fun isProjectClass(cls: JIRClassOrInterface): Boolean
}
