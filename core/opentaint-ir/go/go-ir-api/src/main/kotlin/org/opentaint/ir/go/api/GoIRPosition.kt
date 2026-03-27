package org.opentaint.ir.go.api

import org.opentaint.ir.api.common.CommonMethodParameter

data class GoIRPosition(
    val filename: String,
    val line: Int,
    val column: Int,
) {
    override fun toString(): String = "$filename:$line:$column"
}

data class GoIRParameter(
    val name: String,
    override val type: org.opentaint.ir.go.type.GoIRType,
    val index: Int,
): CommonMethodParameter

data class GoIRFreeVar(
    val name: String,
    val type: org.opentaint.ir.go.type.GoIRType,
    val index: Int,
)

data class GoIRTypeParamDecl(
    val name: String,
    val index: Int,
    val constraint: org.opentaint.ir.go.type.GoIRType,
)
