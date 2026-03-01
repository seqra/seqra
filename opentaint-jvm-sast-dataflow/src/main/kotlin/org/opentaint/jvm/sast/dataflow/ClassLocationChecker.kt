package org.opentaint.jvm.sast.dataflow

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.RegisteredLocation

interface ClassLocationChecker {
    fun isProjectLocation(loc: RegisteredLocation): Boolean
    fun isProjectClass(cls: JIRClassOrInterface): Boolean
}
