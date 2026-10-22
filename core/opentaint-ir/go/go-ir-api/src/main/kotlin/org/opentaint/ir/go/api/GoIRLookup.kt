package org.opentaint.ir.go.api

import org.opentaint.ir.go.type.NamedTypeRef

/** Symbol resolution in one program view. References cache only their own lookup result. */
interface GoIRLookup {
    fun function(reference: GoIrFunctionReference): GoIRFunction?
    fun namedType(reference: NamedTypeRef): GoIRNamedType?
    fun global(reference: GoIRGlobalReference): GoIRGlobal?
}

class GoIRProgramLookup(private val program: GoIRProgram) : GoIRLookup {
    override fun function(reference: GoIrFunctionReference): GoIRFunction? =
        program.findPackage(reference.pkg)?.functions?.firstOrNull {
            it.name == reference.name && it.signature == reference.signature
        }
    override fun namedType(reference: NamedTypeRef): GoIRNamedType? =
        program.findPackage(reference.refTypePkg)?.findNamedType(reference.refTypeName)
    override fun global(reference: GoIRGlobalReference): GoIRGlobal? =
        program.findPackage(reference.pkg)?.findGlobal(reference.name)
}

data class GoIRGlobalReference(val pkg: String, val name: String) {
    lateinit var program: GoIRProgram
    val global: GoIRGlobal by lazy {
        program.lookup.global(this) ?: error("Could not find global $pkg.$name")
    }
}

fun GoIRGlobal.reference(): GoIRGlobalReference = GoIRGlobalReference(pkg.importPath, name)
