package org.opentaint.jvm.sast.project

import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.jvm.sast.JIRSourceFileResolver
import org.opentaint.project.Project
import java.nio.file.Path

fun Project.sourceResolver(projectClasses: ProjectClasses): JIRSourceFileResolver {
    val locationSourceRoots = hashMapOf<RegisteredLocation, Path>()
    for ((loc, module) in projectClasses.locationProjectModules) {
        locationSourceRoots[loc] = module.moduleSourceRoot ?: continue
    }

    return JIRSourceFileResolver(sourceRoot, locationSourceRoots)
}
