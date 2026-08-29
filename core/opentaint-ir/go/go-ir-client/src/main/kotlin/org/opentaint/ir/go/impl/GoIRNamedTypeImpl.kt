package org.opentaint.ir.go.impl

import org.opentaint.ir.go.api.GoIRField
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRInterfaceMethod
import org.opentaint.ir.go.api.GoIRNamedType
import org.opentaint.ir.go.api.GoIRPackage
import org.opentaint.ir.go.api.GoIRPosition
import org.opentaint.ir.go.api.GoIRTypeParamDecl
import org.opentaint.ir.go.type.GoIRNamedTypeKind
import org.opentaint.ir.go.type.GoIRType

class GoIRNamedTypeImpl(
    override val name: String,
    override val fullName: String,
    override val pkg: GoIRPackage,
    override val underlying: GoIRType,
    override val kind: GoIRNamedTypeKind,
    override val position: GoIRPosition?,
    override val typeParams: List<GoIRTypeParamDecl> = emptyList(),
) : GoIRNamedType {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GoIRNamedType) return false
        return fullName == other.fullName
    }

    override fun hashCode(): Int = fullName.hashCode()

    val _fields = mutableListOf<GoIRField>()
    val _methods = mutableListOf<GoIRFunction>()
    val _pointerMethods = mutableListOf<GoIRFunction>()
    val _interfaceMethods = mutableListOf<GoIRInterfaceMethod>()
    val _embeddedInterfaces = mutableListOf<GoIRNamedType>()

    override val fields: List<GoIRField> get() = _fields
    override val methods: List<GoIRFunction> get() = _methods
    override val pointerMethods: List<GoIRFunction> get() = _pointerMethods
    override val interfaceMethods: List<GoIRInterfaceMethod> get() = _interfaceMethods
    override val embeddedInterfaces: List<GoIRNamedType> get() = _embeddedInterfaces
    override fun toString(): String = "GoIRNamedType($fullName)"
}
