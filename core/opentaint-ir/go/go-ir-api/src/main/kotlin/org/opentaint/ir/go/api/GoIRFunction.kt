package org.opentaint.ir.go.api

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.CommonMethodParameter
import org.opentaint.ir.api.common.CommonTypeName
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.ControlFlowGraph
import org.opentaint.ir.go.type.GoIRFuncType

/**
 * A Go function (top-level, method, anonymous, or synthetic).
 */
interface GoIRFunction: CommonMethod {
    override val name: String

    val fullName: String
    val pkg: GoIRPackage?
    val signature: GoIRFuncType
    val params: List<GoIRParameter>
    val freeVars: List<GoIRFreeVar>
    val position: GoIRPosition?

    // Method info
    val isMethod: Boolean
    val receiverType: GoIRNamedType?
    val isPointerReceiver: Boolean

    // Flags
    val isExported: Boolean
    val isSynthetic: Boolean
    val syntheticKind: String?

    // Body (null for external/unbuilt functions)
    val body: GoIRBody?
    val hasBody: Boolean get() = body != null

    // Closure
    val parent: GoIRFunction?
    val anonymousFunctions: List<GoIRFunction>

    // Generics
    val typeParams: List<GoIRTypeParamDecl>

    override val parameters: List<CommonMethodParameter> get() = params
    override val returnType: CommonTypeName get() = signature.results.first()
    override fun flowGraph(): ControlFlowGraph<CommonInst> =
        body?.instGraph ?: error("Function $name has no body")
}
