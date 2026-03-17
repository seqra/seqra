package org.opentaint.jvm.sast.sarif

import io.github.detekt.sarif4k.ArtifactLocation
import io.github.detekt.sarif4k.Result
import io.github.detekt.sarif4k.Tool
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LazyToolRunReport(
    @SerialName("originalUriBaseIds")
    val originalURIBaseIDS: Map<String, ArtifactLocation>? = null,

    val tool: Tool,

    @Serializable(with = ResultSequenceSerializer::class)
    val results: Sequence<Result>
)
