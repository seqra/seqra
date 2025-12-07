package org.opentaint.jvm.sast.sarif

import io.github.detekt.sarif4k.Tool
import io.github.detekt.sarif4k.ToolComponent

fun generateSarifAnalyzerToolDescription(): Tool {
    val toolOrganization = System.getenv("SARIF_ORGANIZATION") ?: "Opentaint"
    val toolVersion = System.getenv("SARIF_VERSION") ?: "0.0.0"

    return Tool(
        driver = ToolComponent(name = "SAST", organization = toolOrganization, version = toolVersion)
    )
}
