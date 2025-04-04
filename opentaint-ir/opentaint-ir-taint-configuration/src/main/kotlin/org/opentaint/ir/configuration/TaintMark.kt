package org.opentaint.ir.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("TaintMark")
data class TaintMark(val name: String)