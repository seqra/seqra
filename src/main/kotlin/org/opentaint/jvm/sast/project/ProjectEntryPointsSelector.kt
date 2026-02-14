package org.opentaint.jvm.sast.project

import mu.KLogging
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.jvm.sast.project.spring.springWebProjectEntryPoints

private val logger = object : KLogging() {}.logger

fun ProjectAnalysisContext.selectProjectEntryPoints(options: ProjectAnalysisOptions): List<JIRMethod> =
    getEntryPoints(options)

private fun ProjectAnalysisContext.getEntryPoints(options: ProjectAnalysisOptions): List<JIRMethod> {
    logger.info { "Search entry points for project: ${project.sourceRoot}" }
    val springEp = springWebProjectContext?.springWebProjectEntryPoints().orEmpty()
    return when (projectKind) {
        ProjectKind.UNKNOWN -> allProjectEntryPoints(options) + springEp
        ProjectKind.SPRING_WEB -> springEp
    }
}

private fun ProjectAnalysisContext.allProjectEntryPoints(options: ProjectAnalysisOptions): List<JIRMethod> {
    val debugEp = options.debugOptions?.debugRunAnalysisOnSelectedEntryPoints
    if (debugEp == null) {
        return projectClasses.projectPublicClasses()
            .flatMapTo(mutableListOf()) { it.publicAndProtectedMethods() }
            .ordered()
    }

    val allMethods = projectClasses.projectAllAnalyzableClasses()
        .flatMapTo(mutableListOf()) { it.allAnalyzableMethods() }
        .ordered()

    if (debugEp == "*") return allMethods

    val (clsName, methodName) = debugEp.split('#')
    return allMethods.filter { it.name == methodName && it.enclosingClass.name == clsName }
}

private fun MutableList<JIRMethod>.ordered(): List<JIRMethod> {
    sortWith(compareBy<JIRMethod> { it.enclosingClass.name }.thenBy { it.name })
    return this
}
