package org.opentaint.ir.api.common

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.ControlFlowGraph

interface CommonMethod {
    val name: String
    val parameters: List<CommonMethodParameter>
    val returnType: CommonTypeName

    fun flowGraph(): ControlFlowGraph<CommonInst>
}

interface CommonMethodParameter {
    val type: CommonTypeName
}
