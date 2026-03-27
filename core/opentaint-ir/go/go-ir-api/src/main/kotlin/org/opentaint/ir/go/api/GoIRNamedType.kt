package org.opentaint.ir.go.api

import org.opentaint.ir.go.type.GoIRNamedTypeKind
import org.opentaint.ir.go.type.GoIRType

/**
 * A named type declaration in a package (struct, interface, alias, or other).
 */
interface GoIRNamedType {
    val name: String
    val fullName: String
    val pkg: GoIRPackage
    val underlying: GoIRType
    val kind: GoIRNamedTypeKind
    val position: GoIRPosition?

    // Struct fields (empty for non-struct)
    val fields: List<GoIRField>

    // Method sets
    val methods: List<GoIRFunction>         // value receiver T
    val pointerMethods: List<GoIRFunction>  // pointer receiver *T
    fun allMethods(): List<GoIRFunction> = methods + pointerMethods

    // Interface members (empty for non-interface)
    val interfaceMethods: List<GoIRInterfaceMethod>
    val embeddedInterfaces: List<GoIRNamedType>

    // Generics
    val typeParams: List<GoIRTypeParamDecl>

    // Queries
    fun methodByName(name: String): GoIRFunction? =
        allMethods().find { it.name == name }
}

data class GoIRField(
    val name: String,
    val type: GoIRType,
    val index: Int,
    val isEmbedded: Boolean,
    val isExported: Boolean,
    val tag: String,
    val enclosingType: GoIRNamedType,
)

data class GoIRInterfaceMethod(
    val name: String,
    val signature: org.opentaint.ir.go.type.GoIRFuncType,
    val enclosingInterface: GoIRNamedType,
)
