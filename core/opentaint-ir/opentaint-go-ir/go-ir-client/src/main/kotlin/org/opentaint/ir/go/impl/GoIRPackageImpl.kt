package org.opentaint.ir.go.impl

import org.opentaint.ir.go.api.*

class GoIRPackageImpl(
    override val importPath: String,
    override val name: String,
) : GoIRPackage {
    private val _functions = mutableListOf<GoIRFunction>()
    private val _namedTypes = mutableListOf<GoIRNamedType>()
    private val _globals = mutableListOf<GoIRGlobal>()
    private val _constants = mutableListOf<GoIRConst>()
    private val _imports = mutableListOf<GoIRPackage>()

    override val functions: List<GoIRFunction> get() = _functions
    override val namedTypes: List<GoIRNamedType> get() = _namedTypes
    override val globals: List<GoIRGlobal> get() = _globals
    override val constants: List<GoIRConst> get() = _constants
    override val imports: List<GoIRPackage> get() = _imports
    override var initFunction: GoIRFunction? = null

    // Deferred resolution data
    internal var importIds: List<Int> = emptyList()
    internal var initFunctionId: Int = 0

    fun addFunction(fn: GoIRFunction) { _functions.add(fn) }
    fun addNamedType(nt: GoIRNamedType) { _namedTypes.add(nt) }
    fun addGlobal(g: GoIRGlobal) { _globals.add(g) }
    fun addConst(c: GoIRConst) { _constants.add(c) }

    fun resolveImports(packagesById: Map<Int, GoIRPackageImpl>) {
        for (id in importIds) {
            packagesById[id]?.let { _imports.add(it) }
        }
    }

    fun resolveInitFunction(functionsById: Map<Int, GoIRFunctionImpl>) {
        if (initFunctionId != 0) {
            initFunction = functionsById[initFunctionId]
        }
    }

    override fun findFunction(name: String) = functions.find { it.name == name }
    override fun findNamedType(name: String) = namedTypes.find { it.name == name }
    override fun findGlobal(name: String) = globals.find { it.name == name }
    override fun findConstant(name: String) = constants.find { it.name == name }

    override fun allMethods(): List<GoIRFunction> =
        namedTypes.flatMap { it.allMethods() }

    override fun toString(): String = "GoIRPackage($importPath)"
}
