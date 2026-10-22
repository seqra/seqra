package org.opentaint.ir.go.api

/** A lookup interceptor. Unhandled requests are passed to the next resolver. */
interface GoIRTransformer {
    fun resolvePackage(importPath: String, next: () -> GoIRPackage?): GoIRPackage? = next()
    fun resolveFunction(reference: GoIrFunctionReference, next: () -> GoIRFunction?): GoIRFunction? = next()
    fun packagePath(importPath: String): String = importPath
    fun symbolName(fullName: String): String = fullName
    val additionalPackages: List<GoIRPackage> get() = emptyList()
}
