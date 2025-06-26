package org.opentaint.ir.api.common

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.ControlFlowGraph

interface CommonMethod<out Method, out Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    val enclosingClass: CommonClass // TODO: remove
    val name: String
    val parameters: List<CommonMethodParameter>
    val returnType: CommonTypeName

    fun flowGraph(): ControlFlowGraph<Statement>
}

interface CommonMethodParameter {
    val type: CommonTypeName
}
