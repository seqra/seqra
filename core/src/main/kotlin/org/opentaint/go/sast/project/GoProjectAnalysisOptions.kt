package org.opentaint.go.sast.project

import org.opentaint.common.sast.CommonAnalysisOptions
import java.nio.file.Path

data class GoProjectAnalysisOptions(
    val common: CommonAnalysisOptions = CommonAnalysisOptions(),
    val modelPaths: List<Path> = emptyList(),
    val useDefaultModels: Boolean = true,
)
