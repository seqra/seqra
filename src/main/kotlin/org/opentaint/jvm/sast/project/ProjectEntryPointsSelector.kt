package org.opentaint.jvm.sast.project

import mu.KLogging
import org.opentaint.ir.api.jvm.JIRMethod

private val logger = object : KLogging() {}.logger

fun ProjectAnalysisContext.selectProjectEntryPoints(): List<JIRMethod> = getEntryPoints()

private fun ProjectAnalysisContext.getEntryPoints(): List<JIRMethod> {
    logger.info { "Search entry points for project: ${project.sourceRoot}" }
    return when (projectKind) {
        ProjectKind.UNKNOWN -> allProjectEntryPoints()
        ProjectKind.SPRING_WEB -> projectClasses.springWebProjectEntryPoints(cp)
    }
}

private fun ProjectAnalysisContext.allProjectEntryPoints(): List<JIRMethod> =
    projectClasses.projectPublicClasses()
        .flatMapTo(mutableListOf()) { it.publicAndProtectedMethods() }
        .also {
            it.sortWith(compareBy<JIRMethod> { it.enclosingClass.name }.thenBy { it.name })
        }
