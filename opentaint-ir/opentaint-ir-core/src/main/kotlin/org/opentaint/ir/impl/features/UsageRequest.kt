package org.opentaint.ir.impl.features

import kotlinx.serialization.Serializable

@Serializable
data class UsageFeatureRequest(
    val method: String?,
    val field: String?,
    val className: String
) : java.io.Serializable