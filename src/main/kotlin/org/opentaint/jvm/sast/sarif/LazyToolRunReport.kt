package org.opentaint.jvm.sast.sarif

import io.github.detekt.sarif4k.Result
import io.github.detekt.sarif4k.Tool
import kotlinx.serialization.Serializable

@Serializable
data class LazyToolRunReport(
    val tool: Tool,
    @Serializable(with = ResultSequenceSerializer::class)
    val results: Sequence<Result>
)
