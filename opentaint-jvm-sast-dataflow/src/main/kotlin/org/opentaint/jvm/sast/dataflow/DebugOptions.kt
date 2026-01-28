package org.opentaint.jvm.sast.dataflow

data class DebugOptions(
    val taintRulesStatsSamplingPeriod: Int?,
    val enableIfdsCoverage: Boolean,
    val factReachabilitySarif: Boolean,
    val enableVulnSummary: Boolean,
    val runRuleTests: Boolean,
)