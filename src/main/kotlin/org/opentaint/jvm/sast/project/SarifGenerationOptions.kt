package org.opentaint.jvm.sast.project

data class SarifGenerationOptions(
    val sarifFileName: String = DEFAULT_FILE_NAME,
    val sarifThreadFlowLimit: Int? = null,
    val useSemgrepStyleId: Boolean = false,
    val toolVersion: String = DEFAULT_VERSION,
    val toolSemanticVersion: String = DEFAULT_SEMANTIC_VERSION,
    val uriBase: String? = null,
) {
    companion object {
        const val DEFAULT_FILE_NAME = "report-ifds.sarif"
        const val LOCATION_URI = "%SRCROOT%"
        const val DEFAULT_VERSION = "latest"
        const val DEFAULT_SEMANTIC_VERSION = "latest"
    }
}
