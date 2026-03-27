package org.opentaint.ir.go.api

import org.opentaint.ir.go.type.GoIRType
import org.opentaint.ir.go.value.GoIRConstantValue

/**
 * A package-level global variable.
 */
interface GoIRGlobal {
    val name: String
    val fullName: String
    val type: GoIRType
    val pkg: GoIRPackage
    val isExported: Boolean
    val position: GoIRPosition?
}

/**
 * A package-level named constant.
 */
interface GoIRConst {
    val name: String
    val fullName: String
    val type: GoIRType
    val value: GoIRConstantValue
    val pkg: GoIRPackage
    val isExported: Boolean
    val position: GoIRPosition?
}
