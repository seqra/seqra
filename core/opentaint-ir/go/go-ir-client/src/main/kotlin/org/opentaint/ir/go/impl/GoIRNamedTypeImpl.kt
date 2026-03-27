package org.opentaint.ir.go.impl

import org.opentaint.ir.go.api.*
import org.opentaint.ir.go.type.GoIRNamedTypeKind
import org.opentaint.ir.go.type.GoIRType

class GoIRNamedTypeImpl(
    override val name: String,
    override val fullName: String,
    override val pkg: GoIRPackage,
    override val underlying: GoIRType,
    override val kind: GoIRNamedTypeKind,
    override val position: GoIRPosition?,
) : GoIRNamedType {
    private val _fields = mutableListOf<GoIRField>()
    private val _methods = mutableListOf<GoIRFunction>()
    private val _pointerMethods = mutableListOf<GoIRFunction>()
    private val _interfaceMethods = mutableListOf<GoIRInterfaceMethod>()
    private val _embeddedInterfaces = mutableListOf<GoIRNamedType>()

    override val fields: List<GoIRField> get() = _fields
    override val methods: List<GoIRFunction> get() = _methods
    override val pointerMethods: List<GoIRFunction> get() = _pointerMethods
    override val interfaceMethods: List<GoIRInterfaceMethod> get() = _interfaceMethods
    override val embeddedInterfaces: List<GoIRNamedType> get() = _embeddedInterfaces
    override val typeParams: List<GoIRTypeParamDecl> = emptyList()

    // Deferred resolution data
    internal var methodIds: List<Int> = emptyList()
    internal var pointerMethodIds: List<Int> = emptyList()
    internal var embeddedInterfaceIds: List<Int> = emptyList()

    fun addField(f: GoIRField) { _fields.add(f) }
    fun addInterfaceMethod(m: GoIRInterfaceMethod) { _interfaceMethods.add(m) }

    fun resolveMethods(functionsById: Map<Int, GoIRFunctionImpl>) {
        for (id in methodIds) {
            functionsById[id]?.let {
                _methods.add(it)
                it.receiverType = this
            }
        }
        for (id in pointerMethodIds) {
            functionsById[id]?.let {
                _pointerMethods.add(it)
                it.receiverType = this
            }
        }
    }

    fun resolveEmbeddedInterfaces(namedTypesById: Map<Int, GoIRNamedTypeImpl>) {
        for (id in embeddedInterfaceIds) {
            namedTypesById[id]?.let { _embeddedInterfaces.add(it) }
        }
    }

    override fun toString(): String = "GoIRNamedType($fullName)"
}
