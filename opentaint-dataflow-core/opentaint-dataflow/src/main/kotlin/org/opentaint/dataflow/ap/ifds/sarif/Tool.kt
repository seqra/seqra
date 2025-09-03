package org.opentaint.dataflow.ap.ifds.sarif

import io.github.detekt.sarif4k.Tool
import io.github.detekt.sarif4k.ToolComponent

fun generateSarifAnalyzerToolDescription(): Tool {
    val toolOrganization = System.getenv("SARIF_Opentaint_ORGANIZATION") ?: "Opentaint"
    val toolVersion = System.getenv("SARIF_Opentaint_VERSION") ?: "0.0.1"

    return Tool(
        driver = ToolComponent(name = "SAST", organization = toolOrganization, version = toolVersion)
    )
}
