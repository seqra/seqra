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
    private val nestedShadowCounters = mutableMapOf<Pair<String, String>, Int>()
    private val _registeredFunctions = mutableListOf<FlatFunctionIR>()
    private val _diagnostics = mutableListOf<PIRDiagnostic>()

    /** Append a synthetic function (lambda or nested def) to the module's function list. */
    fun register(function: FlatFunctionIR) {
        _registeredFunctions.add(function)
    }

    /** Allocate a unique synthetic-lambda name within this module, e.g. `<lambda>$3`. */
    fun freshLambdaName(): String = "<lambda>\$${lambdaCounter++}"

    /**
     * Build the module-flat short name for a nested `def` named [shortName]
     * lexically inside [parentName] (which is itself a module-flat short
     * name — so for a doubly-nested def the parent's name already contains
     * `$`). The first nested def in a given parent gets `parent$short`;
     * shadowing siblings get `parent$short$2`, `parent$short$3`, etc., so
     * `module.functions` stays unique.
     *
     * Maintains the suffix invariant: the resulting name is the suffix of
     * `"$moduleName.$result"` that will be stored as `qualifiedName`.
     */
    fun freshNestedName(parentName: String, shortName: String): String {
        val key = parentName to shortName
        val count = nestedShadowCounters.getOrDefault(key, 0)
        nestedShadowCounters[key] = count + 1
        val base = "$parentName$$shortName"
        return if (count == 0) base else "$base$${count + 1}"
    }

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
