package org.opentaint.dataflow.python.graph

import org.opentaint.ir.api.python.PIRAnyType
import org.opentaint.ir.api.python.PIRCFG
import org.opentaint.ir.api.python.PIRClass
import org.opentaint.ir.api.python.PIRDecorator
import org.opentaint.ir.api.python.PIRDiagnostic
import org.opentaint.ir.api.python.PIRField
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRInstruction
import org.opentaint.ir.api.python.PIRModule
import org.opentaint.ir.api.python.PIRParameter
import org.opentaint.ir.api.python.PIRType
import org.opentaint.ir.impl.python.PIRCFGImpl

sealed interface PIRUnknownFunction : PIRFunction {
    override val parameters: List<PIRParameter> get() = emptyList()
    override val returnType: PIRType get() = PIRAnyType
    override val cfg: PIRCFG get() = PIRCFGImpl.EMPTY_CFG
    override val instList: List<PIRInstruction> get() = emptyList()
    override val decorators: List<PIRDecorator> get() = emptyList()
    override val isAsync: Boolean get() = false
    override val isGenerator: Boolean get() = false
    override val isStaticMethod: Boolean get() = false
    override val isClassMethod: Boolean get() = false
    override val isProperty: Boolean get() = false
    override val closureVars: List<String> get() = emptyList()
    override val enclosingClass: PIRClass? get() = null
}

data class PIRQualifiedUnknownFunction(
    override val qualifiedName: String,
) : PIRUnknownFunction {
    override val name: String = qualifiedName.substringAfterLast('.')
    override val module: PIRModule = SimpleNameSyntheticModule(qualifiedName.substringBeforeLast('.'))
}

data class PIRSimpleNameUnknownFunction(
    override val name: String,
) : PIRUnknownFunction {
    override val qualifiedName: String get() = name
    override val module: PIRModule = SimpleNameSyntheticModule("")
}

private class SimpleNameSyntheticModule(override val name: String) : PIRModule {
    override val path: String = ""
    override val classes: List<PIRClass> = emptyList()
    override val functions: List<PIRFunction> = emptyList()
    override val fields: List<PIRField> = emptyList()
    override val diagnostics: List<PIRDiagnostic> = emptyList()
    override val moduleInit: PIRFunction
        get() = error("Synthetic simple-name module has no init")
}
