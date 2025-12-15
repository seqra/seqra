package org.opentaint.jvm.sast.sarif

import io.github.detekt.sarif4k.Version
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LazySarifReport(
    @SerialName("\$schema")
    val schema: String,
    val version: Version,
    val runs: List<LazyToolRunReport>
) {
    companion object {
        private const val SARIF_SCHEMA =
            "https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json"

        fun fromRuns(runs: List<LazyToolRunReport>): LazySarifReport =
            LazySarifReport(SARIF_SCHEMA, Version.The210, runs)
    }
}
