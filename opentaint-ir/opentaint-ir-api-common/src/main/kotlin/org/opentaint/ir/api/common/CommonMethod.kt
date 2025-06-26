package org.opentaint.ir.api.common

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.ControlFlowGraph

interface CommonMethod<out Method, out Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    val name: String
    val enclosingClass: CommonClass
    val returnType: CommonTypeName
    val parameters: List<CommonMethodParameter>

    fun flowGraph(): ControlFlowGraph<Statement>
}

interface CommonMethodParameter {
    // val type: CommonTypeName
    // val name: String?
    // val index: Int
    // val method: CommonMethod<*, *>
}
