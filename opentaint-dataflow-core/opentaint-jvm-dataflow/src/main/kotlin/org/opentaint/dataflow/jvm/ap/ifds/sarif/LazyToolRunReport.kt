package org.opentaint.dataflow.jvm.ap.ifds.sarif

import io.github.detekt.sarif4k.Result
import io.github.detekt.sarif4k.Tool
import kotlinx.serialization.Serializable

@Serializable
data class LazyToolRunReport(
    val tool: Tool,
    @Serializable(with = ResultSequenceSerializer::class)
    val results: Sequence<Result>
)
