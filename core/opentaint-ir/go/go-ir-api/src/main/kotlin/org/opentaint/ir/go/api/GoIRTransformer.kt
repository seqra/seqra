package org.opentaint.ir.go.api

/** Lookup transformations, applied in list order before the resulting IR is exposed. */
interface GoIRTransformer {
    fun transformPackage(pkg: GoIRPackage): GoIRPackage = pkg
    fun transformFunction(function: GoIRFunction): GoIRFunction = function
    val additionalPackages: List<GoIRPackage> get() = emptyList()
}
