package org.opentaint.ir.api.common

import org.opentaint.ir.api.common.cfg.CommonInst

interface CommonTypedMethod<Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    val method: Method
    val returnType: CommonType
    val parameters: List<CommonTypedMethodParameter>
}

interface CommonTypedMethodParameter {
    val name: String?
    val type: CommonType
    val enclosingMethod: CommonTypedMethod<*, *>
}
