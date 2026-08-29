package org.opentaint.ir.go.impl

import org.opentaint.ir.go.api.GoIRBody
import org.opentaint.ir.go.api.GoIRBodyUnavailableException
import org.opentaint.ir.go.api.GoIRFreeVar
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRPackage
import org.opentaint.ir.go.api.GoIRParameter
import org.opentaint.ir.go.api.GoIRPosition
import org.opentaint.ir.go.api.GoIRTypeParamDecl
import org.opentaint.ir.go.api.GoIrFunctionReference
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
    private val declaredHasBody: Boolean = false,
    override val parent: GoIrFunctionReference?,
    override val anonymousFunctions: List<GoIrFunctionReference>,
    override val typeParams: List<GoIRTypeParamDecl> = emptyList(),
) : GoIRFunction {
    private var _body: GoIRBody? = null

    override val body: GoIRBody?
        get() {
            val b = _body
            if (b != null) return b
            if (!declaredHasBody) return null
            throw GoIRBodyUnavailableException(fullName)
        }

    override val hasBody: Boolean get() = declaredHasBody
    override val bodyAvailable: Boolean get() = _body != null

    fun setBody(body: GoIRBody) {
        this._body = body
    }

    override fun toString(): String = "GoIRFunction($fullName)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GoIRFunction) return false
        return fullName == other.fullName
    }

    override fun hashCode(): Int = fullName.hashCode()
}
