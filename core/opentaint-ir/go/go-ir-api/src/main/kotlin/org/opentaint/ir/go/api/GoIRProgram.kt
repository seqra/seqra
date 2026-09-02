package org.opentaint.ir.go.api

import org.opentaint.ir.go.type.GoIRAnonymousInterfaceType

/**
 * Top-level entity: a loaded Go program with all its packages.
 */
interface GoIRProgram {
    /** The program before model lookup overlays. This program is [this] when no models are active. */
    val originalProgram: GoIRProgram get() = this

    val packages: Map<String, GoIRPackage>  // keyed by import path
    val anonymousInterfaces: Map<Int, GoIRAnonymousInterfaceType>

    fun findPackage(importPath: String): GoIRPackage?
    /** Returns the effective function for a reference from [originalProgram]. */
    fun effectiveFunction(function: GoIRFunction): GoIRFunction = function
    fun allFunctions(): List<GoIRFunction>
    fun allNamedTypes(): List<GoIRNamedType>
    fun mainPackage(): GoIRPackage?
}
