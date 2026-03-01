package org.opentaint.jvm.sast.util

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.jvm.sast.dataflow.ClassLocationChecker
import org.opentaint.jvm.sast.project.ProjectClasses

class ProjectLocationChecker(val classes: ProjectClasses) : ClassLocationChecker {
    override fun isProjectClass(cls: JIRClassOrInterface): Boolean = classes.isProjectClass(cls)
    override fun isProjectLocation(loc: RegisteredLocation): Boolean = classes.isProjectLocation(loc)
}

fun ProjectClasses.locationChecker(): ClassLocationChecker = ProjectLocationChecker(this)
