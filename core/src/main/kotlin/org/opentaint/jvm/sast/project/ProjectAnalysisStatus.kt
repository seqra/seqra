package org.opentaint.jvm.sast.project

import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzer

enum class ProjectAnalysisStatus: Comparable<ProjectAnalysisStatus> {
    OK, OOM, TIMEOUT, EXCEPTION
}

fun JIRTaintAnalyzer.Status.toProjectStatus(): ProjectAnalysisStatus =
    maxOf(analysisStatus.toProjectStatus(), traceResolutionStatus.toProjectStatus())

private fun TaintAnalysisUnitRunnerManager.Status.toProjectStatus(): ProjectAnalysisStatus = when (this) {
    TaintAnalysisUnitRunnerManager.Status.OK -> ProjectAnalysisStatus.OK
    TaintAnalysisUnitRunnerManager.Status.EXCEPTION -> ProjectAnalysisStatus.EXCEPTION
    TaintAnalysisUnitRunnerManager.Status.TIMEOUT -> ProjectAnalysisStatus.TIMEOUT
    TaintAnalysisUnitRunnerManager.Status.OOM -> ProjectAnalysisStatus.OOM
}
