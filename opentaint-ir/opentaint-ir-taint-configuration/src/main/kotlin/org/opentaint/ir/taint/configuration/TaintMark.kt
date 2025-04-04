package org.opentaint.ir.taint.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("TaintMark")
data class TaintMark(val name: String)