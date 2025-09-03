package org.opentaint.dataflow.ap.ifds.sarif

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
            "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json"

        fun fromRuns(runs: List<LazyToolRunReport>): LazySarifReport =
            LazySarifReport(SARIF_SCHEMA, Version.The210, runs)
    }
}
