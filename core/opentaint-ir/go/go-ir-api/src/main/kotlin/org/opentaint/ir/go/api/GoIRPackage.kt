package org.opentaint.ir.go.api

/**
 * A Go package with its members.
 */
interface GoIRPackage {
    val importPath: String
    val name: String
    val functions: List<GoIRFunction>
    val namedTypes: List<GoIRNamedType>
    val globals: List<GoIRGlobal>
    val constants: List<GoIRConst>
    val imports: List<GoIRPackage>
    val initFunction: GoIRFunction?

    fun findFunction(name: String): GoIRFunction?
    fun findNamedType(name: String): GoIRNamedType?
    fun findGlobal(name: String): GoIRGlobal?
    fun findConstant(name: String): GoIRConst?
    fun allMethods(): List<GoIRFunction>
}

/**
 * Set of packages that constitute the analysis scope.
 */
interface GoIRPackageSet {
    val program: GoIRProgram
    val packages: List<GoIRPackage>

    fun findPackage(importPath: String): GoIRPackage?
    fun findNamedType(fullName: String): GoIRNamedType?
    fun findFunction(fullName: String): GoIRFunction?
}
