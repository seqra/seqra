package org.opentaint.ir.go.impl

import org.opentaint.ir.go.api.*
import org.opentaint.ir.go.type.GoIRFuncType

class GoIRFunctionImpl(
    override val name: String,
    override val fullName: String,
    override val pkg: GoIRPackage?,
    override val signature: GoIRFuncType,
    override val params: List<GoIRParameter>,
    override val freeVars: List<GoIRFreeVar>,
    override val position: GoIRPosition?,
    override val isMethod: Boolean,
    override val isPointerReceiver: Boolean,
    override val isExported: Boolean,
    override val isSynthetic: Boolean,
    override val syntheticKind: String?,
    // Deferred resolution fields
    internal val receiverTypeId: Int = 0,
    internal val parentFunctionId: Int = 0,
    internal val anonFunctionIds: List<Int> = emptyList(),
) : GoIRFunction {
    override var body: GoIRBody? = null
        private set

    override var receiverType: GoIRNamedType? = null
        internal set

    override var parent: GoIRFunction? = null
        internal set

    private val _anonymousFunctions = mutableListOf<GoIRFunction>()
    override val anonymousFunctions: List<GoIRFunction> get() = _anonymousFunctions

    override val typeParams: List<GoIRTypeParamDecl> = emptyList() // TODO: implement

    fun setBody(body: GoIRBody) {
        this.body = body
    }

    fun resolveReferences(
        functionsById: Map<Int, GoIRFunctionImpl>,
        namedTypesById: Map<Int, GoIRNamedTypeImpl>,
    ) {
        if (parentFunctionId != 0) {
            parent = functionsById[parentFunctionId]
        }
        for (id in anonFunctionIds) {
            functionsById[id]?.let { _anonymousFunctions.add(it) }
        }
        // receiverType resolved via namedTypesById — but we need the type ID to be a named type ID
        // This is handled during named type method resolution
    }

    override fun toString(): String = "GoIRFunction($fullName)"
}
