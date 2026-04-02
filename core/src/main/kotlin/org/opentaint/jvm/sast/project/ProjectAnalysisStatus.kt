package org.opentaint.jvm.sast.project

import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager

enum class ProjectAnalysisStatus: Comparable<ProjectAnalysisStatus> {
    OK, OOM, TIMEOUT, EXCEPTION
}

fun TaintAnalysisUnitRunnerManager.Status.toProjectStatus(): ProjectAnalysisStatus = when (this) {
    TaintAnalysisUnitRunnerManager.Status.OK -> ProjectAnalysisStatus.OK
    TaintAnalysisUnitRunnerManager.Status.EXCEPTION -> ProjectAnalysisStatus.EXCEPTION
    TaintAnalysisUnitRunnerManager.Status.TIMEOUT -> ProjectAnalysisStatus.TIMEOUT
    TaintAnalysisUnitRunnerManager.Status.OOM -> ProjectAnalysisStatus.OOM
}
