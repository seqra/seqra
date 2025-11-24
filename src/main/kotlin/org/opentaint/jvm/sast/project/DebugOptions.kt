package org.opentaint.jvm.sast.project

data class DebugOptions(
    val taintRulesStatsSamplingPeriod: Int?,
    val enableIfdsCoverage: Boolean
)