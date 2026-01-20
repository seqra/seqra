package org.opentaint.jvm.sast.project

import mu.KLogging
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.jvm.sast.project.spring.springWebProjectEntryPoints

private val logger = object : KLogging() {}.logger

fun ProjectAnalysisContext.selectProjectEntryPoints(): List<JIRMethod> = getEntryPoints()

private fun ProjectAnalysisContext.getEntryPoints(): List<JIRMethod> {
    logger.info { "Search entry points for project: ${project.sourceRoot}" }
    val springEp = springWebProjectContext?.springWebProjectEntryPoints().orEmpty()
    return when (projectKind) {
        ProjectKind.UNKNOWN -> allProjectEntryPoints() + springEp
        ProjectKind.SPRING_WEB -> springEp
    }
}

private fun ProjectAnalysisContext.allProjectEntryPoints(): List<JIRMethod> =
    projectClasses.projectPublicClasses()
        .flatMapTo(mutableListOf()) { it.publicAndProtectedMethods() }
        .also {
            it.sortWith(compareBy<JIRMethod> { it.enclosingClass.name }.thenBy { it.name })
        }
