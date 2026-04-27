package org.opentaint.ir.impl.python.protoToFlat

import org.opentaint.ir.api.python.PIRDiagnostic
import org.opentaint.ir.api.python.PIRDiagnosticSeverity
import org.opentaint.ir.impl.python.flat.FlatFunctionIR

/**
 * One-way sink shared by every per-function lowering inside a single module:
 *
 *   - Allocates fresh names for synthetic functions (lambdas, nested defs).
 *   - Collects [FlatFunctionIR]s emitted by inner scopes so the top-level
 *     [ModuleLowering] can append them to the module's `functions` list.
 *   - Collects diagnostics produced anywhere in the pipeline.
 *
 * No back-references to the lowering classes — components are *given* a
 * [ModuleContext] which they only write to. [ModuleContext] is single-shot:
 * one instance per module lowering; [registeredFunctions] / [diagnostics] are
 * read once at the end of [ModuleLowering.lower].
 */
internal class ModuleContext(val moduleName: String) {

    private var lambdaCounter = 0
    private var nestedCounter = 0
    private val _registeredFunctions = mutableListOf<FlatFunctionIR>()
    private val _diagnostics = mutableListOf<PIRDiagnostic>()

    /** Append a synthetic function (lambda or nested def) to the module's function list. */
    fun register(function: FlatFunctionIR) {
        _registeredFunctions.add(function)
    }

    /** Allocate a unique synthetic-lambda name within this module, e.g. `<lambda>$3`. */
    fun freshLambdaName(): String = "<lambda>\$${lambdaCounter++}"

    /** Allocate a unique synthetic-nested-function name within this module, e.g. `helper$local2`. */
    fun freshNestedName(originalName: String): String = "${originalName}\$local${nestedCounter++}"

    fun reportError(message: String, source: String, code: String) {
        _diagnostics.add(PIRDiagnostic(PIRDiagnosticSeverity.ERROR, message, source, code))
    }

    fun reportException(prefix: String, source: String, e: Throwable) {
        reportError("$prefix: ${e.javaClass.simpleName}: ${e.message}", source, e.javaClass.simpleName)
    }

    /** Functions registered so far, in registration order. */
    val registeredFunctions: List<FlatFunctionIR> get() = _registeredFunctions.toList()

    /** Diagnostics reported so far, in registration order. */
    val diagnostics: List<PIRDiagnostic> get() = _diagnostics.toList()
}
